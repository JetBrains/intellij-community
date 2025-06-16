// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.eel.EelPlatform

val EelPlatform.executableName: String
  get() = when (this) {
    is EelPlatform.Darwin -> {
      when (arch) {
        EelPlatform.Arch.ARM_64 ->  "ijent-aarch64-apple-darwin-release"
        EelPlatform.Arch.X86_64 -> "ijent-x86_64-apple-darwin-release"
        else -> throw IllegalArgumentException("Unsupported darwin arch: $arch")
      }
    }
    is EelPlatform.Linux -> {
      when (arch) {
        EelPlatform.Arch.ARM_64 ->  "ijent-aarch64-unknown-linux-musl-release"
        EelPlatform.Arch.X86_64 -> "ijent-x86_64-unknown-linux-musl-release"
        else -> throw IllegalArgumentException("Unsupported linux arch: $arch")
      }
    }
    is EelPlatform.Windows -> {
      when (arch) {
        EelPlatform.Arch.ARM_64 -> "ijent-aarch64-pc-windows-gnu-release" // todo: refine later when we support windows
        EelPlatform.Arch.X86_64 -> "ijent-x86_64-pc-windows-gnu-release.exe"
        else -> throw IllegalArgumentException("Unsupported windows arch: $arch")
      }
    }
    is EelPlatform.FreeBSD -> throw IllegalStateException("FreeBSD is not supported")
  }