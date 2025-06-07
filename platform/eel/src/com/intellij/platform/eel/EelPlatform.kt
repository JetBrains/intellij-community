// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.EelPlatform.*

val EelPlatform.isMac: Boolean get() = this is Darwin
val EelPlatform.isLinux: Boolean get() = this is Linux
val EelPlatform.isWindows: Boolean get() = this is Windows
val EelPlatform.isFreeBSD: Boolean get() = this is FreeBSD

val EelPlatform.isArm32: Boolean get() = arch is Arch.ARM_32
val EelPlatform.isArm64: Boolean get() = arch is Arch.ARM_64
val EelPlatform.isX86: Boolean get() = arch is Arch.X86
val EelPlatform.isX86_64: Boolean get() = arch is Arch.X86_64

private val UNIX_DIRECTORY_SEPARATORS = charArrayOf('/')
private val WINDOWS_DIRECTORY_SEPARATORS = charArrayOf('/', '\\')

val EelPlatform.directorySeparators: CharArray
  get() = when (this) {
    is Windows -> WINDOWS_DIRECTORY_SEPARATORS
    is Posix -> UNIX_DIRECTORY_SEPARATORS
  }

sealed interface EelPlatform {
  val arch: Arch

  sealed interface Arch {
    data object X86_64 : Arch
    data object ARM_64 : Arch
    data object ARM_32 : Arch
    data object X86 : Arch
    data object Unknown : Arch
  }

  sealed interface Posix : EelPlatform

  class Linux(override val arch: Arch) : Posix {
    override fun toString(): String = "${javaClass.simpleName} $arch"
  }

  class Darwin(override val arch: Arch) : Posix {
    override fun toString(): String = "${javaClass.simpleName} $arch"
  }

  class Windows(override val arch: Arch) : EelPlatform {
    override fun toString(): String = "${javaClass.simpleName} $arch"
  }

  class FreeBSD(override val arch: Arch) : Posix {
    override fun toString(): String = "${javaClass.simpleName} $arch"
  }

  companion object {
    fun getFor(os: String, arch: String): EelPlatform? =
      when (os.lowercase()) {
        "darwin" -> when (arch.lowercase()) {
          "arm64", "aarch64" -> Darwin(Arch.ARM_64)
          "amd64", "x86_64", "x86-64" -> Darwin(Arch.X86_64)
          else -> null
        }
        "linux" -> when (arch.lowercase()) {
          "arm64", "aarch64" -> Linux(Arch.ARM_64)
          "amd64", "x86_64", "x86-64" -> Linux(Arch.X86_64)
          else -> null
        }
        "windows" -> when (arch.lowercase()) {
          "amd64", "x86_64", "x86-64" -> Windows(Arch.X86_64)
          "arm64", "aarch64" -> Windows(Arch.ARM_64)
          else -> null
        }
        else -> null
      }
  }
}

val EelPlatform.pathSeparator: String
  get() = when (this) {
    is Windows -> ";"
    is Posix -> ":"
  }