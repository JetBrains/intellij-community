// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import java.io.File

/**
 * @author yole
 */
fun setupLowPriorityExecution(commandLine: GeneralCommandLine, executablePath: String) {
  if (!canRunLowPriority()) {
    commandLine.exePath = executablePath
  }
  else {
    if (SystemInfo.isWindows) {
      commandLine.exePath = "cmd"
      commandLine.parametersList.prependAll("/c", "start", "/b", "/low", "/wait", GeneralCommandLine.inescapableQuote(""), executablePath)
    }
    else {
      commandLine.exePath = nicePath
      commandLine.parametersList.prependAll("-n", "10", executablePath)
    }
  }
}

private fun canRunLowPriority(): Boolean {
  if (!Registry.`is`("ide.allow.low.priority.process")) {
    return false
  }
  if (!SystemInfo.isWindows && !niceExists) {
    return false
  }
  return true
}

private const val nicePath = "/usr/bin/nice"
private val niceExists by lazy { File(nicePath).exists() }
