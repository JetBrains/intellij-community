// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.stdioMcpRunner

import com.intellij.platform.buildData.productInfo.CustomCommandLaunchData
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.OsFamily

internal const val STDIO_MCP_RUNNER_COMMAND: String = "stdioMcpServer"

/** The jars the stdio MCP runner adds to the boot class path of the IDE. */
internal val STDIO_MCP_RUNNER_BOOT_CLASS_PATH_JAR_NAMES: List<String> = listOf(
  "../plugins/mcpserver/lib/intellij.mcpserver.stdio.jar",
  "../plugins/mcpserver/lib/intellij.libraries.kotlin.logging.jar",
  "../plugins/mcpserver/lib/intellij.libraries.ktor.server.sse.jar",
  "../plugins/mcpserver/lib/intellij.libraries.mcp.kotlin.sdk.jar",
)

internal const val STDIO_MCP_RUNNER_MAIN_CLASS: String = "com.intellij.mcpserver.stdio.McpStdioRunnerKt"

internal fun stdioMcpRunnerVmOptionsFilePath(os: OsFamily): String = "${if (os == OsFamily.MACOS) "../bin" else "bin"}/mcp-server.vmoptions"

internal fun generateStdioMcpRunnerLaunchData(ideContext: BuildContext, os: OsFamily): CustomCommandLaunchData = CustomCommandLaunchData(
  commands = listOf(STDIO_MCP_RUNNER_COMMAND),
  vmOptionsFilePath = stdioMcpRunnerVmOptionsFilePath(os),
  bootClassPathJarNames = ideContext.bootClassPathJarNames + STDIO_MCP_RUNNER_BOOT_CLASS_PATH_JAR_NAMES,
  mainClass = STDIO_MCP_RUNNER_MAIN_CLASS,
)
