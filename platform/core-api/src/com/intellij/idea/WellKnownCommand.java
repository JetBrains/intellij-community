// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public final class WellKnownCommand {
  public static final @NotNull String SERVER_MODE = "serverMode";
  public static final @NotNull String SPLIT_MODE = "splitMode";

  public final boolean isHeadless;
  public final boolean isCommandLine;
  public final boolean isRemoteDevHost;

  private WellKnownCommand(boolean isHeadless, boolean isCommandLine, boolean isRemoteDevHost) {
    this.isHeadless = isHeadless;
    this.isCommandLine = isCommandLine;
    this.isRemoteDevHost = isRemoteDevHost;
  }

  public static @Nullable WellKnownCommand getCommandFor(@NotNull List<@NotNull String> args) {
    if (!args.isEmpty()) {
      String commandName = args.get(0);
      WellKnownCommand command = allCommands.get(commandName);
      if (command != null) return command;
      if (commandName.length() < 20 && commandName.endsWith("inspect")) return HEADLESS;
    }
    return null;
  }

  private static final WellKnownCommand GUI = new WellKnownCommand(/*isHeadless=*/false, /*isCommandLine=*/false, /*isRemoteDevHost=*/false);
  private static final WellKnownCommand GUI_COMMAND = new WellKnownCommand(/*isHeadless=*/false, /*isCommandLine=*/true, /*isRemoteDevHost=*/false);
  private static final WellKnownCommand HEADLESS = new WellKnownCommand(/*isHeadless=*/true, /*isCommandLine=*/true, /*isRemoteDevHost=*/false);
  // RemDev host that employs `com.intellij.platform.impl.toolkit.IdeToolkit`
  private static final WellKnownCommand REMOTE_DEV_HOST = new WellKnownCommand(/*isHeadless=*/false, /*isCommandLine=*/false, /*isRemoteDevHost=*/true);

  private static final Map<String, WellKnownCommand> allCommands = new HashMap<String, WellKnownCommand>() {{
    put("exit", HEADLESS);

    put("diff", GUI_COMMAND);
    put("merge", GUI_COMMAND);

    put("update", HEADLESS);
    put("installPlugins", HEADLESS);
    put("installFrontendPlugins", HEADLESS);
    put("listBundledPlugins", HEADLESS);
    put("invalidateCaches", HEADLESS);
    put("warmup", HEADLESS);

    put("duplocate", HEADLESS);
    put("format", HEADLESS);
    put("inspect", HEADLESS);
    put("inspections", HEADLESS);
    put("inspectopedia-generator", HEADLESS);
    put("intentions", HEADLESS);

    put("traverseUI", HEADLESS);
    put("keymap", HEADLESS);
    put("dumpActions", HEADLESS);
    put("dump-launch-parameters", HEADLESS);
    put("dump-shared-index", HEADLESS);
    put("buildEventsScheme", HEADLESS);
    put("cherryPickAnalyzer", HEADLESS);
    put("full-line", HEADLESS);

    put("ant", HEADLESS);
    put("appcodeClangModulesDiff", HEADLESS);
    put("appcodeClangModulesPrinter", HEADLESS);
    put("buildAppcodeCache", HEADLESS);
    put("dataSources", HEADLESS);
    put("ml-evaluate", HEADLESS);
    put("ml-process", HEADLESS);
    put("project-with-shared-caches", HEADLESS);
    put("qodanaExcludedPlugins", HEADLESS);
    put("matterhorn", HEADLESS);
    put("installCoursePlugins", HEADLESS);
    put("createCourse", HEADLESS);
    put("mcpServer", HEADLESS);

    put("thinClient", GUI);
    put("thinClient-headless", HEADLESS);
    put("ijLight", GUI);
    put("serverMode", REMOTE_DEV_HOST);
    put("splitMode", REMOTE_DEV_HOST);
    put("cwmHost", REMOTE_DEV_HOST);
    put("cwmHostNoLobby", REMOTE_DEV_HOST);
    put("remoteDevHost", REMOTE_DEV_HOST);
    put("rdserver-headless", HEADLESS);
    put("openUrlOnClient", HEADLESS);
    put("cwmHostStatus", HEADLESS);
    put("remoteDevShowHelp", HEADLESS);
    put("remoteDevStatus", HEADLESS);
    put("registerBackendLocationForGateway", HEADLESS);
    put("installGatewayProtocolHandler", HEADLESS);
    put("uninstallGatewayProtocolHandler", HEADLESS);
  }};
}
