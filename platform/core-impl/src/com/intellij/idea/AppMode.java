// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@ApiStatus.Internal
public final class AppMode {
  public static final String DISABLE_NON_BUNDLED_PLUGINS = "disableNonBundledPlugins";
  public static final String DONT_REOPEN_PROJECTS = "dontReopenProjects";

  public static final String FORCE_PLUGIN_UPDATES = "idea.force.plugin.updates";

  public static final String CWM_HOST_COMMAND = "cwmHost";
  public static final String CWM_HOST_NO_LOBBY_COMMAND = "cwmHostNoLobby";

  static final String PLATFORM_PREFIX_PROPERTY = "idea.platform.prefix";
  private static final String AWT_HEADLESS = "java.awt.headless";

  private static boolean isHeadless;
  private static boolean isCommandLine;
  private static boolean isLightEdit;

  private static boolean disableNonBundledPlugins;

  private static boolean dontReopenProjects;

  private static boolean isRemoteDevHost;

  public static boolean isDisableNonBundledPlugins() {
    return disableNonBundledPlugins;
  }

  public static boolean isDontReopenProjects() {
    return dontReopenProjects;
  }

  public static boolean isLightEdit() {
    return isLightEdit;
  }

  public static boolean isCommandLine() {
    return isCommandLine;
  }

  public static boolean isHeadless() {
    return isHeadless;
  }

  public static boolean isIsRemoteDevHost() {
    return isRemoteDevHost;
  }

  public static void setFlags(@NotNull List<String> args) {
    isHeadless = isHeadless(args);
    isCommandLine = isHeadless || (args.size() > 0 && isGuiCommand(args.get(0)));
    isLightEdit = "LightEdit".equals(System.getProperty(PLATFORM_PREFIX_PROPERTY)) || (!isCommandLine && isFileAfterOptions(args));

    if (isHeadless) {
      System.setProperty(AWT_HEADLESS, Boolean.TRUE.toString());
    }

    isRemoteDevHost = args.size() > 0 && (CWM_HOST_COMMAND.equals(args.get(0)) || CWM_HOST_NO_LOBBY_COMMAND.equals(args.get(0)));

    for (String arg : args) {
      if (DISABLE_NON_BUNDLED_PLUGINS.equalsIgnoreCase(arg)) {
        disableNonBundledPlugins = true;
      }
      else if (DONT_REOPEN_PROJECTS.equalsIgnoreCase(arg)) {
        dontReopenProjects = true;
      }
    }
  }

  private static boolean isGuiCommand(String arg) {
    return "diff".equals(arg) || "merge".equals(arg);
  }

  private static boolean isFileAfterOptions(@NotNull List<String> args) {
    for (String arg : args) {
      // If not an option
      if (!arg.startsWith("-")) {
        try {
          Path path = Paths.get(arg);
          return Files.isRegularFile(path) || !Files.exists(path);
        }
        catch (Throwable t) {
          return false;
        }
      }
      else if (arg.equals("-l") || arg.equals("--line") || arg.equals("-c") || arg.equals("--column")) { // NON-NLS
        return true;
      }
    }
    return false;
  }

  @TestOnly
  public static void setHeadlessInTestMode(boolean headless) {
    isHeadless = headless;
    isCommandLine = true;
    isLightEdit = false;
  }

  private static boolean isHeadless(List<String> args) {
    if (Boolean.getBoolean(AWT_HEADLESS)) {
      return true;
    }

    if (args.size() == 0) {
      return false;
    }

    String firstArg = args.get(0);

    @SuppressWarnings("SpellCheckingInspection")
    List<String> headlessCommands = Arrays.asList(
      "ant", "duplocate", "dataSources", "dump-launch-parameters", "dump-shared-index", "traverseUI", "buildAppcodeCache", "format", "keymap", "update", "inspections", "intentions",
      "rdserver-headless", "thinClient-headless", "installPlugins", "dumpActions", "cwmHostStatus", "invalidateCaches", "warmup", "buildEventsScheme",
      "inspectopedia-generator", "remoteDevShowHelp", "installGatewayProtocolHandler", "uninstallGatewayProtocolHandler",
      "appcodeClangModulesDiff", "appcodeClangModulesPrinter", "exit", "qodanaExcludedPlugins");
    return headlessCommands.contains(firstArg) || firstArg.length() < 20 && firstArg.endsWith("inspect"); //NON-NLS
  }

  public static boolean isDevServer() {
    return Boolean.getBoolean("idea.use.dev.build.server");
  }

  public static String getDevBuildRunDirName(@NotNull String platformPrefix) {
    String result = System.getProperty("dev.build.dir");
    if (result == null) {
      return platformPrefix.equals("Idea") ? "idea-community" : platformPrefix;
    }
    else {
      return result;
    }
  }
}
