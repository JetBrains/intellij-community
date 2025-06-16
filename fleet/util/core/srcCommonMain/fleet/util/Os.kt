// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.multiplatform.linkToActual

class Os private constructor() {
  enum class Type {
    Windows, Linux, MacOS, Unknown
  }

  val name: String
    get() = getName()

  val version: String
    get() = getVersion()

  val arch: String
    get() = getArch()

  val type: Type
    get() {
      val normalizedName = this.name.lowercase()
      return when {
        normalizedName.startsWith("mac") -> Type.MacOS
        normalizedName.startsWith("win") -> Type.Windows
        normalizedName.contains("nix") || normalizedName.contains("nux") -> Type.Linux
        else -> Type.Unknown
      }
    }

  val isAarch64: Boolean
    get() {
      val arch = this.arch
      return "aarch64" == arch || "arm64" == arch
    }

  val isWasm: Boolean
    get() {
      val arch = this.arch
      return "wasm" == arch
    }

  val isMac: Boolean
    get() = this.type == Type.MacOS

  val isWindows: Boolean
    get() = this.type == Type.Windows

  val isLinux: Boolean
    get() = this.type == Type.Linux

  val isUnix: Boolean
    get() {
      val type = this.type
      return type == Type.Linux || type == Type.MacOS
    }

  companion object {
    val INSTANCE: Os = Os()
  }
}

internal fun getName(): String = linkToActual()

internal fun getVersion(): String = linkToActual()

internal fun getArch(): String = linkToActual()
