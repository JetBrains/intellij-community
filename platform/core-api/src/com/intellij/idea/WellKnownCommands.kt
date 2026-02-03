// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object WellKnownCommands {
  const val SERVER_MODE: String = "serverMode"
  const val SPLIT_MODE: String = "splitMode"

  private val allCommands: Map<String, WellKnownCommand> = listOf(
    WellKnownCommand("exit", CommandType.HEADLESS),

    WellKnownCommand("diff", CommandType.GUI_COMMAND),
    WellKnownCommand("merge", CommandType.GUI_COMMAND),

    WellKnownCommand("update", CommandType.HEADLESS),
    WellKnownCommand("installPlugins", CommandType.HEADLESS),
    WellKnownCommand("installFrontendPlugins", CommandType.HEADLESS),
    WellKnownCommand("listBundledPlugins", CommandType.HEADLESS),
    WellKnownCommand("invalidateCaches", CommandType.HEADLESS),
    WellKnownCommand("warmup", CommandType.HEADLESS),

    WellKnownCommand("duplocate", CommandType.HEADLESS),
    WellKnownCommand("format", CommandType.HEADLESS),
    WellKnownCommand("inspect", CommandType.HEADLESS),
    WellKnownCommand("inspections", CommandType.HEADLESS),
    WellKnownCommand("inspectopedia-generator", CommandType.HEADLESS),
    WellKnownCommand("intentions", CommandType.HEADLESS),

    WellKnownCommand("traverseUI", CommandType.HEADLESS),
    WellKnownCommand("keymap", CommandType.HEADLESS),
    WellKnownCommand("dumpActions", CommandType.HEADLESS),
    WellKnownCommand("dump-launch-parameters", CommandType.HEADLESS),
    WellKnownCommand("dump-shared-index", CommandType.HEADLESS),
    WellKnownCommand("buildEventsScheme", CommandType.HEADLESS),
    WellKnownCommand("cherryPickAnalyzer", CommandType.HEADLESS),
    WellKnownCommand("full-line", CommandType.HEADLESS),

    WellKnownCommand("ant", CommandType.HEADLESS),
    WellKnownCommand("appcodeClangModulesDiff", CommandType.HEADLESS),
    WellKnownCommand("appcodeClangModulesPrinter", CommandType.HEADLESS),
    WellKnownCommand("buildAppcodeCache", CommandType.HEADLESS),
    WellKnownCommand("dataSources", CommandType.HEADLESS),
    WellKnownCommand("ml-evaluate", CommandType.HEADLESS),
    WellKnownCommand("ml-process", CommandType.HEADLESS),
    WellKnownCommand("project-with-shared-caches", CommandType.HEADLESS),
    WellKnownCommand("qodanaExcludedPlugins", CommandType.HEADLESS),
    WellKnownCommand("matterhorn", CommandType.HEADLESS),
    WellKnownCommand("installCoursePlugins", CommandType.HEADLESS),
    WellKnownCommand("createCourse", CommandType.HEADLESS),

    WellKnownCommand("thinClient", CommandType.GUI),
    WellKnownCommand("thinClient-headless", CommandType.HEADLESS),
    WellKnownCommand("serverMode", CommandType.REMOTE_DEV_HOST),
    WellKnownCommand("splitMode", CommandType.REMOTE_DEV_HOST),
    WellKnownCommand("cwmHost", CommandType.REMOTE_DEV_HOST),
    WellKnownCommand("cwmHostNoLobby", CommandType.REMOTE_DEV_HOST),
    WellKnownCommand("remoteDevHost", CommandType.REMOTE_DEV_HOST),
    WellKnownCommand("rdserver-headless", CommandType.HEADLESS),

    WellKnownCommand("openUrlOnClient", CommandType.HEADLESS),
    WellKnownCommand("cwmHostStatus", CommandType.HEADLESS),
    WellKnownCommand("remoteDevShowHelp", CommandType.HEADLESS),
    WellKnownCommand("remoteDevStatus", CommandType.HEADLESS),
    WellKnownCommand("registerBackendLocationForGateway", CommandType.HEADLESS),
    WellKnownCommand("installGatewayProtocolHandler", CommandType.HEADLESS),
    WellKnownCommand("uninstallGatewayProtocolHandler", CommandType.HEADLESS),
  ).associateBy { it.command }

  @JvmStatic
  fun getCommandFor(args: List<String>): WellKnownCommand? {
    val commandName = args.firstOrNull() ?: return null

    val wellKnownCommand = allCommands[commandName]
    if (wellKnownCommand != null) return wellKnownCommand

    if (commandName.length < 20 && commandName.endsWith("inspect")) {
      return WellKnownCommand(commandName, commandType = CommandType.HEADLESS)
    }

    return wellKnownCommand
  }
}

/**
 * An unknown command is treated by IDE as `WellKnownCommand(name, CommandType.GUI)`
 */
@ApiStatus.Internal
class WellKnownCommand internal constructor(
  val command: String,
  val commandType: CommandType,
) {
  /**
   * Whether a command may show Swing UI.
   *
   * [AppMode.isHeadless] and [com.intellij.openapi.application.Application.isHeadlessEnvironment] and [java.awt.GraphicsEnvironment.isHeadless]
   */
  val isHeadless: Boolean = commandType == CommandType.HEADLESS

  /**
   * [AppMode.isCommandLine] and [com.intellij.openapi.application.Application.isCommandLine]
   */
  val isCommandLine: Boolean = isHeadless || commandType == CommandType.GUI_COMMAND

  /**
   * Whether the command will start IDE in Remote Development host mode.
   *
   * [AppMode.isRemoteDevHost]
   */
  val isRemoteDevHost: Boolean = commandType == CommandType.REMOTE_DEV_HOST
}

@ApiStatus.Internal
enum class CommandType {
  GUI,
  GUI_COMMAND,
  HEADLESS,

  /**
   * RemDev host that employs [com.intellij.platform.impl.toolkit.IdeToolkit]
   */
  REMOTE_DEV_HOST
}
