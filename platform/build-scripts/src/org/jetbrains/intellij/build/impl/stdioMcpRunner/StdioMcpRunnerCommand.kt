// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.stdioMcpRunner

import com.intellij.platform.buildData.productInfo.CustomCommandLaunchData
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.OsFamily

internal fun generateStdioMcpRunnerLaunchData(ideContext: BuildContext, os: OsFamily): CustomCommandLaunchData = CustomCommandLaunchData(
  commands = listOf("stdioMcpServer"),
  vmOptionsFilePath = "${if (os == OsFamily.MACOS) "../bin" else "bin"}/mcp-server.vmoptions",
  bootClassPathJarNames = ideContext.bootClassPathJarNames + "../plugins/mcpserver/lib/mcpserver-frontend.jar",
  mainClass = "com.intellij.mcpserver.stdio.McpStdioRunnerKt",
)
