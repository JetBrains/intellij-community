// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.dependencies.JdkDownloader

interface LibcImpl {
  companion object {

    fun current(osFamily: OsFamily): LibcImpl {
      return when (osFamily) {
        OsFamily.MACOS -> MacLibcImpl.DEFAULT
        OsFamily.WINDOWS -> WindowsLibcImpl.DEFAULT
        OsFamily.LINUX -> LinuxLibcImpl.current
      }
    }
  }
}

enum class MacLibcImpl : LibcImpl {
  DEFAULT;

  companion object {
    @JvmField
    val ALL: List<MacLibcImpl> = listOf(*MacLibcImpl.entries.toTypedArray())
  }
}

enum class WindowsLibcImpl : LibcImpl {
  DEFAULT;

  companion object {
    @JvmField
    val ALL: List<WindowsLibcImpl> = listOf(*WindowsLibcImpl.entries.toTypedArray())
  }
}

enum class LinuxLibcImpl : LibcImpl {
  GLIBC,
  MUSL;

  companion object {
    @JvmField
    val ALL: List<LinuxLibcImpl> = listOf(*LinuxLibcImpl.entries.toTypedArray())

    val current: LinuxLibcImpl = if (JdkDownloader.isLinuxMusl()) MUSL else GLIBC
  }
}