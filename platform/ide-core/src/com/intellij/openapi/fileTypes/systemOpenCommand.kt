// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus

/**
 * @return the command that hands [target], a file path or a URL, to the application that the operating system registers for it:
 * `rundll32 url.dll,FileProtocolHandler` on Windows, `open` on macOS and `xdg-open` elsewhere. It is `null` when `xdg-open` is not on
 * the path.
 * No shell runs the command, so `cmd` does not expand a `%NAME%` in a URL on Windows.
 */
@ApiStatus.Internal
@OptIn(LowLevelLocalMachineAccess::class)
fun systemOpenCommand(target: String, os: OS = OS.CURRENT): List<String>? {
  val launcher = when (os) {
    OS.Windows -> listOf("rundll32.exe", "url.dll,FileProtocolHandler")
    OS.macOS -> listOf("/usr/bin/open")
    else -> if (PathEnvironmentVariableUtil.isOnPath("xdg-open")) listOf("xdg-open") else return null
  }
  return launcher + target
}
