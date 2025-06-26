// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.EelPlatform.*
import org.jetbrains.annotations.ApiStatus

/**
 * A light version of [EelPlatform].
 *
 * It contains only the type of the operating system: Windows or Posix.
 *
 * The reason of the difference between [EelOsFamily] and [EelPlatform]
 * is that [EelPlatform] in general can be generated only after an actual connection
 * to a remote machine. In contrast, [EelOsFamily] is known before any I/O.
 * Although the remote machine can suddenly become a Windows-machine
 * after being a Posix-machine for a while,
 * too much other functionality would break down in such an occasion.
 */
@ApiStatus.Experimental
enum class EelOsFamily {
  Posix,
  Windows,
}

@get:ApiStatus.Experimental
val EelOsFamily.isPosix: Boolean get() = this == EelOsFamily.Posix

@get:ApiStatus.Experimental
val EelPlatform.isPosix: Boolean get() = osFamily.isPosix

@get:ApiStatus.Experimental
val EelPlatform.isMac: Boolean get() = this is Darwin

@get:ApiStatus.Experimental
val EelPlatform.isLinux: Boolean get() = this is Linux

@get:ApiStatus.Experimental
val EelOsFamily.isWindows: Boolean get() = this == EelOsFamily.Windows

@get:ApiStatus.Experimental
val EelPlatform.isWindows: Boolean get() = osFamily.isWindows

@get:ApiStatus.Experimental
val EelPlatform.isFreeBSD: Boolean get() = this is FreeBSD

@get:ApiStatus.Experimental
val EelPlatform.isArm32: Boolean get() = arch is Arch.ARM_32

@get:ApiStatus.Experimental
val EelPlatform.isArm64: Boolean get() = arch is Arch.ARM_64

@get:ApiStatus.Experimental
val EelPlatform.isX86: Boolean get() = arch is Arch.X86

@get:ApiStatus.Experimental
val EelPlatform.isX86_64: Boolean get() = arch is Arch.X86_64

private val UNIX_DIRECTORY_SEPARATORS = charArrayOf('/')
private val WINDOWS_DIRECTORY_SEPARATORS = charArrayOf('/', '\\')

@get:ApiStatus.Experimental
val EelOsFamily.directorySeparators: CharArray
  get() = when (this) {
    EelOsFamily.Windows -> WINDOWS_DIRECTORY_SEPARATORS
    EelOsFamily.Posix -> UNIX_DIRECTORY_SEPARATORS
  }

/**
 * A heavier version of [EelOsFamily] that contains enough information for choosing
 * right binary files, shared libraries and other machine-dependent things.
 */
@ApiStatus.Experimental
sealed interface EelPlatform {
  @get:ApiStatus.Experimental
  val arch: Arch
  val osFamily: EelOsFamily

  @ApiStatus.Experimental
  sealed interface Arch {
    data object X86_64 : Arch
    data object ARM_64 : Arch
    data object ARM_32 : Arch
    data object X86 : Arch
    data object Unknown : Arch  // TODO Why does it exist?
  }

  @ApiStatus.Experimental
  sealed class Posix : EelPlatform {
    override val osFamily: EelOsFamily = EelOsFamily.Posix
    override fun toString(): String = "${javaClass.simpleName} $arch"
  }

  @ApiStatus.Experimental
  class Linux(override val arch: Arch) : Posix()

  @ApiStatus.Experimental
  class Darwin(override val arch: Arch) : Posix()

  @ApiStatus.Experimental
  class Windows(override val arch: Arch) : EelPlatform {
    override val osFamily: EelOsFamily = EelOsFamily.Windows
  }

  @ApiStatus.Experimental
  class FreeBSD(override val arch: Arch) : Posix()

  companion object {
    @ApiStatus.Internal
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

@get:ApiStatus.Experimental
val EelOsFamily.pathSeparator: String
  get() = when (this) {
    EelOsFamily.Posix -> ":"
    EelOsFamily.Windows -> ";"
  }