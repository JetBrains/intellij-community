// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.system.CpuArch
import com.intellij.util.system.LowLevelLocalMachineAccess

@Suppress("EnumEntryName")
@OptIn(LowLevelLocalMachineAccess::class)
enum class JvmArchitecture(
  @JvmField val archName: String,
  @JvmField val fileSuffix: String,
  @JvmField val dirName: String,
  @JvmField val marketplaceName: String,
) {
  x64(archName = "X86_64", fileSuffix = "64", dirName = "amd64", marketplaceName = "x86_64"),
  aarch64(archName = "AArch64", fileSuffix = "aarch64", dirName = "aarch64", marketplaceName = "arm64");

  companion object {
    @JvmField
    val ALL: List<JvmArchitecture> = entries.toList()

    @JvmField
    val currentJvmArch: JvmArchitecture = when {
      CpuArch.isArm64() -> aarch64
      CpuArch.isIntel64() -> x64
      else -> throw IllegalStateException("Unsupported arch: " + CpuArch.CURRENT)
    }
  }
}
