// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.stdioMcpRunner

import com.intellij.platform.buildData.productInfo.CustomCommandLaunchData
import org.jetbrains.intellij.build.BuildContext

internal fun generateStdioMcpRunnerLaunchData(ideContext: BuildContext): CustomCommandLaunchData {
  return CustomCommandLaunchData(
    commands = listOf("stdioMcpServer"),
    bootClassPathJarNames = ideContext.bootClassPathJarNames + "../plugins/mcpserver/lib/mcpserver-frontend.jar",
    mainClass = "com.intellij.mcpserver.stdio.McpStdioRunnerKt",
  )
}