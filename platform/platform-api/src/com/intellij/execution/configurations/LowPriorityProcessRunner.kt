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
      // TODO
    }
    else {
      commandLine.exePath = "/usr/bin/nice"
      commandLine.addParameter("-n")
      commandLine.addParameter("10")
      commandLine.addParameter(executablePath)
    }
  }
}

private fun canRunLowPriority(): Boolean {
  if (!Registry.`is`("ide.allow.low.priority.process")) {
    return false
  }
  if (SystemInfo.isWindows) {
    return false
  }
  else {
    if (!niceExists) return false
  }
  return true
}

private val niceExists by lazy { File("/usr/bin/nice").exists() }
