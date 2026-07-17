// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.eel.EelPlatform

val EelPlatform.executableName: String
  get() = when (this) {
    is EelPlatform.Darwin if arch == EelPlatform.Arch.ARM_64 -> "ijent-aarch64-apple-darwin-release"
    is EelPlatform.Darwin if arch == EelPlatform.Arch.X86_64 -> "ijent-x86_64-apple-darwin-release"
    is EelPlatform.Linux if arch == EelPlatform.Arch.ARM_64 -> "ijent-aarch64-unknown-linux-musl-release"
    is EelPlatform.Linux if arch == EelPlatform.Arch.X86_64 -> "ijent-x86_64-unknown-linux-musl-release"
    is EelPlatform.Linux if arch == EelPlatform.Arch.ARM_32 -> @Suppress("SpellCheckingInspection") "ijent-armv7-unknown-linux-musleabihf-release"
    is EelPlatform.Windows if arch == EelPlatform.Arch.ARM_64 -> "ijent-aarch64-pc-windows-gnullvm-release.exe"
    is EelPlatform.Windows if arch == EelPlatform.Arch.X86_64 -> "ijent-x86_64-pc-windows-gnullvm-release.exe"
    else -> throw IllegalArgumentException("Unsupported platform: $this / $arch")
  }
