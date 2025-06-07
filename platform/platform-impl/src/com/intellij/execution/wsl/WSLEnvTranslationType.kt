// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import org.jetbrains.annotations.ApiStatus

// https://learn.microsoft.com/en-us/windows/wsl/filesystems#wslenv-flags
@ApiStatus.Internal
enum class WSLEnvTranslationType(val translationFlag: String) {
  Path("/p"),
  PathToWSL("/up"),
  PathToWin("/wp"),

  PathList("/l"),
  PathListToWSL("/ul"),
  PathListToWin("/wl"),

  ToWSL("/u"),
  ToWin("/w");

  companion object {

    @JvmStatic
    fun fromFlag(flag: String): WSLEnvTranslationType = when (flag) {
      "/p", "p" -> Path
      "/up", "up" -> PathToWSL
      "/wp", "wp" -> PathToWin

      "/l", "l" -> PathList
      "/ul", "ul" -> PathListToWSL
      "/wl", "wl" -> PathListToWin

      "/u", "u" -> ToWSL
      "/w", "w" -> ToWin

      else -> ToWSL
    }
  }
}