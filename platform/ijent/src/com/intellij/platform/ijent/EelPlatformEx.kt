// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.eel.EelPlatform

val EelPlatform.executableName: String
  get() = when (this) {
    EelPlatform.Arm64Darwin -> "ijent-aarch64-apple-darwin-release"
    EelPlatform.X8664Darwin -> "ijent-x86_64-apple-darwin-release"
    EelPlatform.Aarch64Linux -> "ijent-aarch64-unknown-linux-musl-release"
    EelPlatform.X8664Linux -> "ijent-x86_64-unknown-linux-musl-release"
    EelPlatform.X64Windows -> "ijent-x86_64-pc-windows-gnu-release.exe"
    EelPlatform.Arm64Windows -> "ijent-aarch64-pc-windows-gnu-release" // todo: refine later when we support windows
  }