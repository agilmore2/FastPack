package org.mcupdater.fastpack;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.mcupdater.api.Version;
import org.mcupdater.model.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class FastPack
{
	public static Map<String,String> modExceptions = new HashMap<>();

	public static void main(final String[] args) {
		OptionParser optParser = new OptionParser();
        optParser.accepts("help","Shows this help").isForHelp();
        optParser.formatHelpWith(new BuiltinHelpFormatter(200,3));
		ArgumentAcceptingOptionSpec<String> searchPathSpec = optParser.accepts("path","Path to scan for mods and configs").requiredUnless("help").withRequiredArg().ofType(String.class);
		ArgumentAcceptingOptionSpec<String> baseURLSpec = optParser.accepts("baseURL","Base URL for downloads").requiredUnless("help").withRequiredArg().ofType(String.class);
		ArgumentAcceptingOptionSpec<String> MCVersionSpec = optParser.accepts("mc","Minecraft version").requiredUnless("help").withRequiredArg().ofType(String.class);
		ArgumentAcceptingOptionSpec<String> forgeVersionSpec = optParser.accepts("forge","Forge version").requiredUnless("help").withRequiredArg().ofType(String.class);
		ArgumentAcceptingOptionSpec<String> xmlPathSpec = optParser.accepts("out","XML file to write").requiredUnless("help").withRequiredArg().ofType(String.class);
		ArgumentAcceptingOptionSpec<String> serverAddrSpec = optParser.accepts("mcserver","Server address").withRequiredArg().ofType(String.class).defaultsTo("");
		ArgumentAcceptingOptionSpec<String> serverNameSpec = optParser.accepts("name","Server name").withRequiredArg().ofType(String.class).defaultsTo("FastPack Instance");
		ArgumentAcceptingOptionSpec<String> serverIdSpec = optParser.accepts("id","Server ID").withRequiredArg().ofType(String.class).defaultsTo("fastpack");
        ArgumentAcceptingOptionSpec<String> mainClassSpec = optParser.accepts("mainClass","Main class for launching Minecraft").withRequiredArg().ofType(String.class).defaultsTo("net.minecraft.launchwrapper.Launch");
        ArgumentAcceptingOptionSpec<String> newsURLSpec = optParser.accepts("newsURL","URL to display in the News tab").withRequiredArg().ofType(String.class).defaultsTo("about:blank");
        ArgumentAcceptingOptionSpec<String> iconURLSpec = optParser.accepts("iconURL","URL of icon to display in instance list").withRequiredArg().ofType(String.class).defaultsTo("");
        ArgumentAcceptingOptionSpec<String> revisionSpec = optParser.accepts("revision","Revision string to display").withRequiredArg().ofType(String.class).defaultsTo("1");
        ArgumentAcceptingOptionSpec<Boolean> autoConnectSpec = optParser.accepts("autoConnect","Auto-connect to server on launch").withRequiredArg().ofType(Boolean.class).defaultsTo(Boolean.TRUE);
		final OptionSet options = optParser.parse(args);

        if (options.has("help")) {
            try {
                optParser.printHelpOn(System.out);
                return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

		initExceptions();

		ServerDefinition definition = new ServerDefinition();
		ServerList entry = new ServerList();
		entry.setName(serverNameSpec.value(options));
		entry.setServerId(serverIdSpec.value(options));
		entry.setAddress(serverAddrSpec.value(options));
		entry.setMainClass(mainClassSpec.value(options));
		entry.setNewsUrl(newsURLSpec.value(options));
        entry.setIconUrl(iconURLSpec.value(options));
        entry.setRevision(revisionSpec.value(options));
        entry.setAutoConnect(autoConnectSpec.value(options));
		entry.setVersion(MCVersionSpec.value(options));
		definition.setServerEntry(entry);
		definition.addImport(new Import("http://files.mcupdater.com/example/forge.php?mc=" + MCVersionSpec.value(options) + "&forge=" + forgeVersionSpec.value(options),"forge"));

		Path searchPath = new File(searchPathSpec.value(options)).toPath();
		Path xmlPath = new File(xmlPathSpec.value(options)).toPath();

		PathWalker pathWalk = new PathWalker(definition, searchPath, baseURLSpec.value(options));
		try {
			Files.walkFileTree(searchPath, pathWalk);
		} catch (IOException e) {
			e.printStackTrace();
		}
		definition.sortMods();
		definition.assignConfigs();

		try {
			BufferedWriter fileWriter = Files.newBufferedWriter(xmlPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
			fileWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			fileWriter.newLine();
			fileWriter.write("<ServerPack version=\"" + org.mcupdater.api.Version.API_VERSION + "\" xmlns=\"http://www.mcupdater.com\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.mcupdater.com http://files.mcupdater.com/ServerPackv2.xsd\">");
			fileWriter.newLine();
			fileWriter.write("\t<Server id=\"" + xmlEscape(definition.getServerEntry().getServerId()) +
					"\" name=\"" + xmlEscape(definition.getServerEntry().getName()) +
					"\" newsUrl=\"" + xmlEscape(definition.getServerEntry().getNewsUrl()) +
					"\" version=\"" + xmlEscape(definition.getServerEntry().getVersion()) +
					"\" mainClass=\"" + xmlEscape(definition.getServerEntry().getMainClass()) +
					(definition.getServerEntry().getAddress().isEmpty() ? "" : ("\" serverAddress=\"" + xmlEscape(definition.getServerEntry().getAddress()))) +
                    (definition.getServerEntry().getIconUrl().isEmpty() ? "" : ("\" iconURL=\"" + xmlEscape(definition.getServerEntry().getIconUrl()))) +
                    "\" revision=\"" + xmlEscape(definition.getServerEntry().getRevision()) +
                    "\" autoConnect=\"" + Boolean.toString(definition.getServerEntry().isAutoConnect()) +
					"\">");
			fileWriter.newLine();
			for (Import importEntry : definition.getImports()) {
				fileWriter.write("\t\t<Import" + (importEntry.getUrl().isEmpty() ? ">" : (" url=\"" + xmlEscape(importEntry.getUrl())) + "\">") + importEntry.getServerId() + "</Import>");
				fileWriter.newLine();
			}
			for (Module moduleEntry : definition.getModules()) {
				fileWriter.write("\t\t<Module name=\"" + moduleEntry.getName() + "\" id=\"" + moduleEntry.getId() + "\" depends=\"" + moduleEntry.getDepends() + "\" side=\"" + moduleEntry.getSide() + "\">");
				fileWriter.newLine();
				for (PrioritizedURL url : moduleEntry.getPrioritizedUrls()) {
					fileWriter.write("\t\t\t<URL priority=\"" + url.getPriority() + "\">" + xmlEscape(url.getUrl()) + "</URL>");
					fileWriter.newLine();
				}
				fileWriter.write("\t\t\t<Required");
				if (moduleEntry.getIsDefault()) { fileWriter.write(" isDefault=\"true\""); }
				fileWriter.write(">" + (moduleEntry.getRequired() ? "true" : "false") + "</Required>");
				fileWriter.newLine();
				fileWriter.write("\t\t\t<ModType");
				if (moduleEntry.getInRoot()) { fileWriter.write(" inRoot=\"true\""); }
				if (moduleEntry.getJarOrder() > 0) { fileWriter.write(" order=\"" + moduleEntry.getJarOrder() + "\""); }
				if (moduleEntry.getKeepMeta()) { fileWriter.write(" keepMeta=\"true\""); }
				if (!moduleEntry.getLaunchArgs().isEmpty()) { fileWriter.write(" launchArgs=\"" + xmlEscape(moduleEntry.getLaunchArgs()) + "\""); }
				if (!moduleEntry.getJreArgs().isEmpty()) { fileWriter.write(" jreArgs=\"" + xmlEscape(moduleEntry.getJreArgs())); }
				fileWriter.write(">" + moduleEntry.getModType().toString() + "</ModType>");
				fileWriter.newLine();
				fileWriter.write("\t\t\t<MD5>" + moduleEntry.getMD5() + "</MD5>");
				fileWriter.newLine();
				if (moduleEntry.getMeta().size() > 0) {
					fileWriter.write("\t\t\t<Meta>");
					fileWriter.newLine();
					for (Entry<String,String> metaEntry : moduleEntry.getMeta().entrySet()) {
						fileWriter.write("\t\t\t\t<" + xmlEscape(metaEntry.getKey()) + ">" + xmlEscape(metaEntry.getValue()) + "</" + xmlEscape(metaEntry.getKey()) + ">");
						fileWriter.newLine();
					}
					fileWriter.write("\t\t\t</Meta>");
					fileWriter.newLine();
				}
				for (GenericModule submodule : moduleEntry.getSubmodules()) {
					fileWriter.write("\t\t\t<Submodule name=\"" + submodule.getName() + "\" id=\"" + submodule.getId() + "\" depends=\"" + submodule.getDepends() + "\" side=\"" + submodule.getSide() + "\">");
					fileWriter.newLine();
					for (PrioritizedURL url : submodule.getPrioritizedUrls()) {
						fileWriter.write("\t\t\t\t<URL priority=\"" + url.getPriority() + "\">" + xmlEscape(url.getUrl()) + "</URL>");
						fileWriter.newLine();
					}
					fileWriter.write("\t\t\t\t<Required");
					if (submodule.getIsDefault()) { fileWriter.write(" isDefault=\"true\""); }
					fileWriter.write(">" + (submodule.getRequired() ? "true" : "false") + "</Required>");
					fileWriter.newLine();
					fileWriter.write("<ModType");
					if (submodule.getInRoot()) { fileWriter.write(" inRoot=\"true\""); }
					if (submodule.getJarOrder() > 0) { fileWriter.write(" order=\"" + submodule.getJarOrder() + "\""); }
					if (submodule.getKeepMeta()) { fileWriter.write(" keepMeta=\"true\""); }
					if (!submodule.getLaunchArgs().isEmpty()) { fileWriter.write(" launchArgs=\"" + xmlEscape(submodule.getLaunchArgs()) + "\""); }
					if (!submodule.getJreArgs().isEmpty()) { fileWriter.write(" jreArgs=\"" + xmlEscape(submodule.getJreArgs())); }
					fileWriter.write(">" + submodule.getModType().toString() + "</ModType>");
					fileWriter.newLine();
					fileWriter.write("\t\t\t\t<MD5>" + submodule.getMD5() + "</MD5>");
					fileWriter.newLine();
					if (submodule.getMeta().size() > 0) {
						fileWriter.write("\t\t\t\t<Meta>");
						fileWriter.newLine();
						for (Entry<String,String> metaEntry : submodule.getMeta().entrySet()) {
							fileWriter.write("\t\t\t\t\t<" + xmlEscape(metaEntry.getKey()) + ">" + xmlEscape(metaEntry.getValue()) + "</" + xmlEscape(metaEntry.getKey()) + ">");
							fileWriter.newLine();
						}
						fileWriter.write("\t\t\t\t</Meta>");
						fileWriter.newLine();
					}
					fileWriter.write("\t\t\t</Submodule>");
					fileWriter.newLine();
				}
				for (ConfigFile config : moduleEntry.getConfigs()) {
					fileWriter.write("\t\t\t<ConfigFile>");
					fileWriter.newLine();
					fileWriter.write("\t\t\t\t<URL>" + xmlEscape(config.getUrl()) + "</URL>");
					fileWriter.newLine();
					fileWriter.write("\t\t\t\t<Path>" + xmlEscape(config.getPath()) + "</Path>");
					fileWriter.newLine();
					fileWriter.write("\t\t\t\t<MD5>" + xmlEscape(config.getMD5()) + "</MD5>");
					fileWriter.newLine();
					fileWriter.write("\t\t\t\t<NoOverwrite>" + config.isNoOverwrite() + "</NoOverwrite>");
					fileWriter.newLine();
					fileWriter.write("\t\t\t</ConfigFile>");
					fileWriter.newLine();
				}
				fileWriter.write("\t\t</Module>");
				fileWriter.newLine();
			}
			fileWriter.write("\t</Server>");
			fileWriter.newLine();
			fileWriter.write("</ServerPack>");
			fileWriter.newLine();
			fileWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void initExceptions() {
		modExceptions.put("NotEnoughItems","NEI");
		modExceptions.put("AWWayofTime","BloodMagic");
		modExceptions.put("WR-CBE|Core","WirelessRedstone");
		modExceptions.put("TConstruct","TinkersWorkshop");
	}

	private static String xmlEscape(String input) {
		return input.replace("\"", "&quot;").replace("'", "&apos;").replace("<", "&lt;").replace(">","&gt;").replace("&","&amp;");
	}
}
