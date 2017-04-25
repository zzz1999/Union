package com.him188.union;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;
import money.Money;

import java.util.*;

import static cn.nukkit.utils.TextFormat.*;

/**
 * 半年前写的 (现在时间 2017/04/25)
 * 面向过程的, 不规范的公会插件
 * <p>
 * 将来可能会修改为对象化 (可能, 可能, 可能...)
 *
 * @author Him188
 */
public class UnionMain extends PluginBase {
	// TODO: 2017/4/25  Objective


	private Map<String, Map<String, Object>> list = new HashMap<>();

	private List<Request> requests = new ArrayList<>();

	private Map<Integer, Integer[]> levels = new HashMap<>();

	private int max_level;

	private class Request {
		private Player player;
		private String union;

		private Request(Player player, String union) {
			this.player = player;
			this.union = union;
		}

		private Player getPlayer() {
			return player;
		}

		private String getUnion() {
			return union;
		}
	}

	@Override
	public void onLoad() {
		instance = this;
	}

	@Override
	public void onEnable() {
		if (!getDataFolder().mkdir()) {
			getLogger().warning("无法创建配置目录");
		}
		reloadConfig();

		try {
			Server.getInstance().getScheduler().getClass().getMethod("scheduleRepeatingTask", Plugin.class, Runnable.class, int.class);

			Server.getInstance().getScheduler().scheduleRepeatingTask(this, this::saveConfig, 20 * 30);
		} catch (NoSuchMethodException e) {
			//noinspection deprecation
			Server.getInstance().getScheduler().scheduleRepeatingTask(this::saveConfig, 20 * 30);
		}

	}

	@SuppressWarnings("unchecked")
	@Override
	public void reloadConfig() {
		super.reloadConfig();
		list = new HashMap<>();
		getConfig().getAll().forEach((key, value) -> list.put(key, (Map<String, Object>) value));

		levels = new HashMap<Integer, Integer[]>() {
			{
				new Config(getDataFolder() + "/levels.yml", Config.YAML).getAll().forEach((key, value) -> put(Integer.parseInt(key), ((List<Integer>) value).toArray(new Integer[2])));
			}
		};

		levels.forEach((level, info) -> {
			if (level > max_level) {
				max_level = level;
			}
		});
	}

	@Override
	public void saveConfig() {
		getConfig().setAll(new LinkedHashMap<>(list));
		super.saveConfig();
	}

	private static final String BASE_COMMAND = "/公会";
	private static final String HELP_MASTER;
	private static final String HELP_VICE_MASTER;
	private static final String HELP_PLAYER;

	static {
		StringBuilder help;

		help = new StringBuilder(WHITE + "========查看公会指令========");
		for (String s : "公会列表|请求加入 <公会名>|退出|贡献 <数量>".split("|")) {
			help.append(YELLOW).append(BASE_COMMAND).append(" ").append(s).append("\n");
		}
		HELP_PLAYER = help.toString();

		help = new StringBuilder();
		for (String s : "成员列表|同意请求 <玩家全名区分大小写>|拒绝请求 <玩家全名区分大小写>|请求列表|金库|踢出 <成员名区分大小写>".split("|")) {
			help.append(AQUA).append(BASE_COMMAND).append(" ").append(s).append("\n");
		}
		HELP_VICE_MASTER = HELP_PLAYER + help;

		help = new StringBuilder();
		for (String s : "解散|改名 <公会名>|升职 <玩家全名区分大小写>|降职 <玩家全名区分大小写>|升级|提取金库 <数量>|踢出 <副会长名区分大小写>".split("|")) {
			help.append(GREEN).append(BASE_COMMAND).append(" ").append(s).append("\n");
		}
		HELP_MASTER = HELP_VICE_MASTER + help;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		String msg = callCommand(sender, command, args);
		if (!msg.equals("")) {
			sender.sendMessage(msg);
		}

		return true;
	}

	private String getHelp(CommandSender sender) {
		if (!sender.isPlayer()) {
			return HELP_PLAYER;
		}
		if (getUnionByMaster(sender.getName()) == null) {
			if (getUnionByVice(sender.getName()) == null) {
				return HELP_PLAYER;
			}
			return HELP_VICE_MASTER;
		}
		return HELP_MASTER;
	}

	@SuppressWarnings("SameParameterValue")
	private String getLast(String[] array, int pos) {
		StringBuilder result = new StringBuilder();
		for (int i = pos; i < array.length; i++) {
			result.append(" ").append(array[i]);
		}
		return result.substring(1, result.length() - 1);
	}

	@SuppressWarnings("unchecked")
	private String callCommand(CommandSender sender, Command command, String[] args) {
		if (args.length == 0) {
			return getHelp(sender);
		}
		switch (command.getName()) {
			case "公会列表":
				final StringBuilder msg = new StringBuilder();
				msg.append(GREEN).append("查看公会列表: \n");
				list.forEach((key, value) -> msg.append(AQUA).append("公会:").append(key).append(BLUE).append(" 等级:").append(value.get("level")).append(GOLD).append(" 会长:").append(value.get("master").toString()).append(YELLOW).append(" 成员:").append(Integer.parseInt(value.get("members").toString())).append("\n"));
				return msg.toString();
			default:
				if (!sender.isPlayer()) {
					return RED + "请在游戏内使用";
				}
		}

		String union;
		StringBuilder result;

		switch (command.getName()) {
			case "创建":
				if (getUnionByPlayer(sender.getName()) != null) {
					return RED + "你已经加入过公会了";
				}

				if (args.length == 1) {
					return RED + "请使用 /公会 创建 <公会名>";
				}

				if (createUnion(getLast(args, 1), sender.getName())) {
					return AQUA + "创建成功";
				} else return RED + "已经存在这个名字的公会";
			case "请求加入":
				if (getUnionByPlayer(sender.getName()) != null) {
					return RED + "你已经加入过公会了";
				}

				if (args.length == 1) {
					return RED + "请使用 /公会 请求加入 <公会名>";
				}

				union = getUnionByFirstName(args[1]);
				if (union == null) {
					return RED + "该公会不存在";
				}

				for (Request request : requests) {
					if (request.player.getName().equals(sender.getName())) {
						requests.remove(request);
						break;
					}
				}

				requests.add(new Request((Player) sender, union));
				broadcastUnionViceMessage(union, AQUA + "[公会] 玩家 " + sender.getName() + " 请求加入公会.\n" + YELLOW + "批准请求: /公会 同意请求 " + sender.getName() + YELLOW + "拒绝请求: /公会 同意请求 " + sender.getName());
			case "退出":
				if (getUnionByMaster(sender.getName()) == null) {
					return RED + "你还没有加入公会";
				} else {
					if ((union = getUnionByMember(sender.getName())) != null) {
						((List<String>) list.get(union).get("members")).remove(sender.getName());
						broadcastUnionMessage(union, RED + "[公会] 公会成员" + sender.getName() + " 已离开公会");
						return "";
					} else if ((union = getUnionByVice(sender.getName())) != null) {
						((List<String>) list.get(union).get("vices")).remove(sender.getName());
						broadcastUnionMessage(union, RED + "[公会] 公会副会长" + sender.getName() + " 已离开公会");
						return "";
					}

					return RED + "你是公会会长, 解散公会请使用 /公会 解散";
				}

			case "贡献":
				if ((union = getUnionByPlayer(sender.getName())) == null) {
					return RED + "你还没有加入公会";
				}

				if (args.length == 1) {
					return RED + "请使用 /公会 贡献 <数量>";
				}

				int amount;
				try {
					amount = Integer.parseInt(args[1]);
				} catch (Exception e) {
					return RED + "请输入正确的数量";
				}

				if (Money.getInstance().getMoney(sender.getName()) < amount) {
					return RED + "你的" + Money.getInstance().getMoneyUnit1() + "不足";
				}

				list.get(union).put("money", Integer.parseInt(list.get(union).get("money").toString()) + amount);
				broadcastUnionMessage(union, AQUA + "[公会] 成员 " + sender.getName() + " 向公会贡献了 " + amount + Money.getInstance().getMoneyUnit1() + "!");
				return AQUA + "贡献成功";

			case "成员列表":
				if ((union = getUnionByVice(sender.getName())) == null) {
					return RED + "你还没有加入公会或没有权限执行这个指令";
				}

				result = new StringBuilder(GREEN + "查看公会成员列表(蓝色为副会长, 黄色为普通成员): ");
				for (String member : ((List<String>) list.get(union).get("members"))) {
					result.append(YELLOW).append(member).append("  ");
				}
				for (String vice : ((List<String>) list.get(union).get("vices"))) {
					result.append(AQUA).append(vice).append("  ");
				}
				return result.toString();

			case "同意请求":
				if ((union = getUnionByViceAndMaster(sender.getName())) == null) {
					return RED + "你还没有加入公会或没有权限执行这个指令";
				}

				if (args.length == 1) {
					return RED + "请使用 /公会 同意请求 <目标玩家>";
				}

				for (Request request : requests) {
					if (request.union.equals(union)) {
						broadcastUnionMessage(union, AQUA + "[公会] " + sender.getName() + " 已批准 " + request.player.getName() + " 的加入请求");
						((List<String>) list.get(union).get("members")).add(request.player.getName());
						request.player.sendMessage(AQUA + "[公会] " + sender.getName() + " 已批准你的加入请求. 你现在是" + GOLD + union + AQUA + "中的一员了");
						requests.remove(request);
						return AQUA + "批准成功";
					}
				}
				return RED + "没有收到该玩家的请求. 使用 /公会 请求列表 查看所有请求";
			case "拒绝请求":
				if ((union = getUnionByViceAndMaster(sender.getName())) == null) {
					return RED + "你还没有加入公会或没有权限执行这个指令";
				}

				if (args.length == 1) {
					return RED + "请使用 /公会 拒绝请求 <目标玩家>";
				}

				for (Request request : requests) {
					if (request.union.equals(union)) {
						broadcastUnionMessage(union, YELLOW + "[公会] " + sender.getName() + " 已拒绝 " + request.player.getName() + " 的加入请求");
						request.player.sendMessage(RED + "[公会] " + sender.getName() + " 已拒绝你的加入请求");
						requests.remove(request);
						return AQUA + "拒绝成功";
					}
				}
				return RED + "没有收到该玩家的请求. 使用 /公会 请求列表 查看所有请求";
			case "请求列表":
				if ((union = getUnionByViceAndMaster(sender.getName())) == null) {
					return RED + "你还没有加入公会或没有权限执行这个指令";
				}

				result = new StringBuilder(GREEN + "查看加入请求列表(当前公会): ");
				for (Request request : requests) {
					if (!request.union.equals(union)) {
						continue;
					}
					result.append(AQUA).append(request.player.getName()).append("  ");
				}
				return result.toString();
			case "金库":
				if ((union = getUnionByViceAndMaster(sender.getName())) == null) {
					return RED + "你还没有加入公会或没有权限执行这个指令";
				}

				return AQUA + "公会金库: " + list.get(union).get("money").toString();
			case "解散":
				if ((union = getUnionByMaster(sender.getName())) == null) {
					return RED + "你还没有加入公会或没有权限执行这个指令";
				}
				broadcastUnionMessage(union, RED + "[公会] 会长已解散本公会!");
				list.remove(union);
				return AQUA + "已解散公会";
			case "改名":
				if ((union = getUnionByMaster(sender.getName())) == null) {
					return RED + "你还没有加入公会或没有权限执行这个指令";
				}

				if (args.length == 1) {
					return RED + "请使用 /公会 改名 <公会名>";
				}

				result = new StringBuilder(getLast(args, 1));
				if (getUnion(result.toString()) != null) {
					return RED + "该公会名已被使用";
				}

				for (Request request : requests) {
					if (request.union.equals(union)) {
						request.union = result.toString();
					}
				}
				broadcastUnionMessage(union, AQUA + "[公会] 会长将公会名修改为: " + result);
				list.put(result.toString(), list.remove(union));
				return AQUA + "已改名";
			case "升职":
				if ((union = getUnionByMaster(sender.getName())) == null) {
					return RED + "你还没有加入公会或没有权限执行这个指令";
				}

				if (args.length == 1) {
					return RED + "请使用 /公会 升职 <玩家全名区分大小写>";
				}

				if (sender.getName().equals(args[1])) {
					return RED + "你已经是会长";
				}


				try {
					result = new StringBuilder(getUnionByVice(sender.getName()));
					if (result.toString().equals(union)) {
						return RED + "该玩家已经是副会长";
					} else {
						return RED + "该玩家没有加入本公会";
					}
				} catch (NullPointerException ignored) {

				}

				try {
					//noinspection ConstantConditions
					result = new StringBuilder(getUnionByPlayer(sender.getName()));
					if (result.toString().equals(union)) {
						((List<String>) list.get(union).get("vices")).add(args[1]);
						((List<String>) list.get(union).get("members")).remove(args[1]);
						return AQUA + "已将该玩家升职为副会长";
					} else {
						return RED + "该玩家没有加入本公会";
					}
				} catch (NullPointerException e) {
					return RED + "该玩家没有加入本公会";
				}
			case "降职":
				if ((union = getUnionByMaster(sender.getName())) == null) {
					return RED + "你还没有加入公会或没有权限执行这个指令";
				}

				if (args.length == 1) {
					return RED + "请使用 /公会 降职 <玩家全名区分大小写>";
				}

				if (sender.getName().equals(args[1])) {
					return RED + "你已经是会长";
				}

				try {
					result = new StringBuilder(getUnionByVice(sender.getName()));
					if (result.toString().equals(union)) {
						((List<String>) list.get(union).get("vices")).remove(args[1]);
						((List<String>) list.get(union).get("members")).add(args[1]);
						return AQUA + "已将该玩家降职为成员";
					} else {
						return RED + "该玩家没有加入本公会";
					}
				} catch (NullPointerException e) {
					return RED + "该玩家没有加入本公会或不是副会长";
				}
			case "升级":
				if ((union = getUnionByMaster(sender.getName())) == null) {
					return RED + "你还没有加入公会或没有权限执行这个指令";
				}

				if (Integer.parseInt(list.get(union).get("level").toString()) >= max_level) {
					return RED + "公会已经达到最大等级";
				}

				int level = Integer.parseInt(list.get(union).get("level").toString());
				Integer[] need = levels.get(level);
				int money = Integer.parseInt(list.get(union).get("money").toString());
				if (need[1] > money) {
					return RED + "公会资金过低. 需要 " + need[1];
				} else {
					money -= need[1];
					list.get(union).put("level", level + 1);
					list.get(union).put("money", money);
					return AQUA + "升级成功. 消耗了公会资金: " + need[1] + "\n" + AQUA + "公会最大人数已提升为: " + need[0] + ". 公会资金剩余 " + money;
				}

			case "提取金库":
				if ((union = getUnionByMaster(sender.getName())) == null) {
					return RED + "你还没有加入公会或没有权限执行这个指令";
				}

				if (args.length == 1) {
					return RED + "请使用 /公会 提取金库 <数量>";
				}

				int money1 = Integer.parseInt(list.get(union).get("money").toString());
				int need1;
				try {
					need1 = Integer.parseInt(args[1]);
				} catch (NumberFormatException e) {
					return RED + "请输入正确的 <数量>  (小于公会资金的自然数)";
				}

				if (need1 > money1) {
					return RED + "提取的数量不能超过公会总资金数量! 公会目前资金: " + money1;
				}

				money1 -= need1;
				list.get(union).put("money", money1);
				Money.getInstance().addMoney(sender.getName(), need1);
				return AQUA + "提取成功! 成功提取 " + need1 + ". 公会金库剩余: " + money1 + ". 你的" + Money.getInstance().getMoneyUnit1() + "剩余: " + Money.getInstance().getMoney(sender.getName());

			case "踢出":
				if ((union = getUnionByViceAndMaster(sender.getName())) == null) {
					return RED + "你还没有加入公会或没有权限执行这个指令";
				}

				if (args.length == 1) {
					return RED + "请使用 /公会 踢出 <玩家名区分大小写>";
				}

				if (((List<String>) list.get(union).get("members")).remove(args[1])) {
					broadcastUnionMessage(union, YELLOW + "[公会] " + sender.getName() + " 已将 " + args[1] + " 踢出公会");
					return AQUA + "踢出成功";
				}

				if ((union = getUnionByMaster(sender.getName())) != null && ((List<String>) list.get(union).get("vices")).remove(args[1])) {
					broadcastUnionMessage(union, YELLOW + "[公会] " + sender.getName() + " 已将 " + args[1] + " 踢出公会");
					return AQUA + "踢出成功";
				}

				return RED + "你没有权限踢出该玩家或该玩家不存在";
			default:
				return getHelp(sender);
		}
	}

	public int getMaxPlayer(String union) {
		return levels.get(Integer.parseInt(list.get(union).get("level").toString()))[0];
	}

	
	/* API */

	private static UnionMain instance;

	public static UnionMain getInstance() {
		return instance;
	}

	/**
	 * 获取公会列表
	 *
	 * @return 公会列表
	 */
	public Map<String, Map<String, Object>> getList() {
		return list;
	}

	/**
	 * 广播消息给一个公会的所有会长和副会长
	 *
	 * @param union   公会名
	 * @param message 消息内容
	 */
	@SuppressWarnings("unchecked")
	public void broadcastUnionViceMessage(String union, String message) {
		List<String> list = (List<String>) this.list.get(union).get("vices");
		list.add(this.list.get(union).get("master").toString());
		Server.getInstance().getOnlinePlayers().forEach((uuid, player) -> {
			if (list.contains(player.getName())) {
				player.sendMessage(message);
			}
		});
	}

	/**
	 * 广播消息给一个公会的所有成员
	 *
	 * @param union   公会名
	 * @param message 消息内容
	 */
	@SuppressWarnings("unchecked")
	public void broadcastUnionMessage(String union, String message) {
		List<String> list = (List<String>) this.list.get(union).get("vices");
		list.add(this.list.get(union).get("master").toString());
		list.addAll((List<String>) this.list.get(union).get("members"));
		Server.getInstance().getOnlinePlayers().forEach((uuid, player) -> {
			if (list.contains(player.getName())) {
				player.sendMessage(message);
			}
		});
	}

	/**
	 * 创建一个公会
	 *
	 * @param name   公会名
	 * @param master 会长
	 *
	 * @return 是否成功
	 */
	public boolean createUnion(String name, String master) {
		name = name.trim();
		if (getUnion(name) != null) {
			return false;
		}

		list.put(name, new HashMap<String, Object>() {
			{
				put("master", master);
				put("members", new ArrayList<>());
				put("vices", new ArrayList<>());
				put("level", 1);
				put("money", 0);
			}
		});
		return true;
	}

	/**
	 * 由有颜色的或未区分大小写的公会名获取使用 API 需要的真正公会名
	 *
	 * @param name 有颜色的或未区分大小写的公会名
	 *
	 * @return 使用 API 需要的真正公会名
	 */
	public String getUnion(final String name) {
		final String trimName = name.trim();
		final String[] found = {null};
		list.forEach((union, value) -> {
			if (found[0] != null) {
				return;
			}

			if (TextFormat.clean(union).equals(trimName)) {
				found[0] = union;
			}
		});
		return found[0];
	}

	/**
	 * 通过公会成员名字的开头获取公会名
	 *
	 * @param name 名字
	 *
	 * @return 公会名
	 */
	public String getUnionByFirstName(final String name) {
		final String trimName = name.trim();
		final String[] found = {null};
		list.forEach((union, value) -> {
			if (found[0] != null) {
				return;
			}

			if (TextFormat.clean(union).startsWith(trimName)) {
				found[0] = union;
			}
		});
		return found[0];
	}

	/**
	 * 通过公会会长名字的开头获取公会名
	 *
	 * @param name 名字
	 *
	 * @return 公会名
	 */
	public String getUnionByMaster(final String name) {
		final String[] found = {null};
		list.forEach((union, value) -> {
			if (found[0] != null) {
				return;
			}
			if (value.getOrDefault("master", "").equals(name)) {
				found[0] = union;
			}
		});

		return found[0];
	}

	/**
	 * 通过公会成员(不包括会长和副会长)名字的开头获取公会名
	 *
	 * @param name 名字
	 *
	 * @return 公会名
	 */
	@SuppressWarnings("unchecked")
	public String getUnionByMember(final String name) {
		final String[] found = {null};
		list.forEach((union, value) -> {
			if (found[0] != null) {
				return;
			}
			if (((List<String>) value.getOrDefault("members", "")).contains(name)) {
				found[0] = union;
			}
		});

		return found[0];
	}

	/**
	 * 通过公会副会长(不包括会长)名字的开头获取公会名
	 *
	 * @param name 名字
	 *
	 * @return 公会名
	 */
	@SuppressWarnings("unchecked")
	public String getUnionByVice(final String name) {
		final String[] found = {null};
		list.forEach((union, value) -> {
			if (found[0] != null) {
				return;
			}
			if (((List<String>) value.getOrDefault("vices", "")).contains(name)) {
				found[0] = union;
			}
		});

		return found[0];
	}

	/**
	 * 通过公会会长或副会长名字的开头获取公会名
	 *
	 * @param name 名字
	 *
	 * @return 公会名
	 */
	@SuppressWarnings("unchecked")
	public String getUnionByViceAndMaster(final String name) {
		final String[] found = {null};
		list.forEach((union, value) -> {
			if (found[0] != null) {
				return;
			}
			if (value.get("master").toString().equals(name)) {
				found[0] = union;
			}
			if (((List<String>) value.getOrDefault("vices", "")).contains(name)) {
				found[0] = union;
			}
		});

		return found[0];
	}

	/**
	 * 通过公会名字获取玩家名
	 *
	 * @param name 名字
	 *
	 * @return 公会名
	 */
	//by any player
	public String getUnionByPlayer(String name) {
		String union = getUnionByMaster(name);
		if (union != null) {
			return union;
		}

		union = getUnionByVice(name);
		if (union != null) {
			return union;
		}

		union = getUnionByMaster(name);
		if (union != null) {
			return union;
		}

		return null;
	}
}