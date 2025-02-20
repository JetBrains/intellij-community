// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ide.progress.runWithModalProgressBlocking

internal fun runToolOnBgtWithModality(project: Project, commandLine: GeneralCommandLine, toolName: @NlsSafe String): OSProcessHandler {
  return runWithModalProgressBlocking(project, ToolsBundle.message("tools.process.start.progress.title", toolName)) {
    OSProcessHandler(commandLine)
  }
}