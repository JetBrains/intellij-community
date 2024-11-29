// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.path.pathOs
import com.intellij.platform.eel.path.pathSeparator

sealed interface EelPlatform {
  sealed interface Posix : EelPlatform
  sealed interface Linux : Posix
  sealed interface Darwin : Posix
  sealed interface Windows : EelPlatform

  data object Arm64Darwin : Darwin
  data object Aarch64Linux : Linux
  data object Arm64Windows : Windows

  data object X8664Darwin : Darwin
  data object X8664Linux : Linux
  data object X64Windows : Windows

  companion object {
    fun getFor(os: String, arch: String): EelPlatform? =
      when (os.lowercase()) {
        "darwin" -> when (arch.lowercase()) {
          "arm64", "aarch64" -> Arm64Darwin
          "amd64", "x86_64", "x86-64" -> X8664Darwin
          else -> null
        }
        "linux" -> when (arch.lowercase()) {
          "arm64", "aarch64" -> Aarch64Linux
          "amd64", "x86_64", "x86-64" -> X8664Linux
          else -> null
        }
        "windows" -> when (arch.lowercase()) {
          "amd64", "x86_64", "x86-64" -> X64Windows
          "arm64", "aarch64" -> Arm64Windows
          else -> null
        }
        else -> null
      }
  }
}

val EelPlatform.pathSeparator: String
  get(): String = pathOs.pathSeparator