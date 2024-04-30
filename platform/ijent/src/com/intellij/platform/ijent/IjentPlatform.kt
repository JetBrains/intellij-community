// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface IjentPlatform {
  sealed interface Posix : IjentPlatform
  sealed interface Linux : Posix
  sealed interface Darwin : Posix
  sealed interface Windows : IjentPlatform

  data object Arm64Darwin : Darwin
  data object Aarch64Linux : Linux
  data object X8664Darwin : Darwin
  data object X8664Linux : Linux
  data object X64Windows : Windows

  companion object {
    fun getFor(os: String, arch: String): IjentPlatform? =
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
          else -> null
        }
        else -> null
      }
  }
}

@get:ApiStatus.Experimental
val IjentPlatform.executableName: String
  get() = when (this) {
    IjentPlatform.Arm64Darwin -> "ijent-aarch64-apple-darwin-release"
    IjentPlatform.X8664Darwin -> "ijent-x86_64-apple-darwin-release"
    IjentPlatform.Aarch64Linux -> "ijent-aarch64-unknown-linux-musl-release"
    IjentPlatform.X8664Linux -> "ijent-x86_64-unknown-linux-musl-release"
    IjentPlatform.X64Windows -> "ijent-x86_64-pc-windows-gnu-release.exe"
  }