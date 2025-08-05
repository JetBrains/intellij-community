// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
  /** @see com.jetbrains.rdserver.unattendedHost.HostWithClientSplitModeStarter */
  public static final String SPLIT_MODE_COMMAND = "splitMode";
  public static final String CWM_HOST_NO_LOBBY_COMMAND = "cwmHostNoLobby";
  public static final String REMOTE_DEV_HOST_COMMAND = "remoteDevHost";
  public static final String REMOTE_DEV_MODE_COMMAND = "serverMode";

  public static final String HELP_OPTION = "--help";
  public static final String VERSION_OPTION = "--version";

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

  public static boolean isRemoteDevHost() {
    return isRemoteDevHost;
  }

  public static boolean isDevServer() {
    return Boolean.getBoolean("idea.use.dev.build.server");
  }

  public static void setFlags(@NotNull List<String> args) {
    isHeadless = isHeadless(args);
    isCommandLine = isHeadless || (!args.isEmpty() && isGuiCommand(args.get(0)));
    isLightEdit = Boolean.parseBoolean(System.getProperty("idea.force.light.edit.mode")) || (!isCommandLine && !isKnownNonLightEditCommand(args) && isFileAfterOptions(args));

    if (isHeadless) {
      System.setProperty(AWT_HEADLESS, Boolean.TRUE.toString());
    }

    if (args.isEmpty()) {
      return;
    }

    isRemoteDevHost = CWM_HOST_COMMAND.equals(args.get(0)) ||
                      CWM_HOST_NO_LOBBY_COMMAND.equals(args.get(0)) ||
                      REMOTE_DEV_HOST_COMMAND.equals(args.get(0)) ||
                      REMOTE_DEV_MODE_COMMAND.equals(args.get(0)) ||
                      SPLIT_MODE_COMMAND.equals(args.get(0));

    for (String arg : args) {
      if (DISABLE_NON_BUNDLED_PLUGINS.equalsIgnoreCase(arg)) {
        disableNonBundledPlugins = true;
      }
      else if (DONT_REOPEN_PROJECTS.equalsIgnoreCase(arg)) {
        dontReopenProjects = true;
      }
    }
  }

  /**
   * Checks whether a known command is present in the args which shouldn't be run in 'light edit' mode. 
   * This is a temporary workaround for IJPL-161632.
   */
  private static boolean isKnownNonLightEditCommand(@NotNull List<String> args) {
    return !args.isEmpty() &&
           Arrays.asList("cwmHost", "cwmHostNoLobby", "remoteDevHost", "serverMode", "splitMode", "thinClient").contains(args.get(0));
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
      else if (arg.equals("-l") || arg.equals("--line") || arg.equals("-c") || arg.equals("--column")) {
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

    if (args.isEmpty()) {
      return false;
    }

    String firstArg = args.get(0);

    List<String> headlessCommands = Arrays.asList(
      "ant", "duplocate", "dataSources", "dump-launch-parameters", "dump-shared-index", "traverseUI", "buildAppcodeCache", "format",
      "keymap", "update", "inspections", "intentions", "rdserver-headless", "thinClient-headless", "installFrontendPlugins", "installPlugins", "dumpActions",
      "cwmHostStatus", "remoteDevStatus", "invalidateCaches", "warmup", "buildEventsScheme", "inspectopedia-generator", "remoteDevShowHelp",
      "installGatewayProtocolHandler", "uninstallGatewayProtocolHandler", "appcodeClangModulesDiff", "appcodeClangModulesPrinter", "exit",
      "qodanaExcludedPlugins", "project-with-shared-caches", "registerBackendLocationForGateway", "cherryPickAnalyzer", "listBundledPlugins");
    return headlessCommands.contains(firstArg) || firstArg.length() < 20 && firstArg.endsWith("inspect");
  }

  public static @Nullable String getDevIdeaProjectDir() {
    return System.getProperty("idea.dev.project.root");
  }
}
