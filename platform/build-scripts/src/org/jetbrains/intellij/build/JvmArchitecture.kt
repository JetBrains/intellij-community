// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.system.CpuArch

@Suppress("EnumEntryName")
enum class JvmArchitecture(@JvmField val archName: String, @JvmField val fileSuffix: String, @JvmField val dirName: String) {
  x64("X86_64", "64", "amd64"), aarch64("AArch64", "aarch64", "aarch64");

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
