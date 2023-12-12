// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.components.serviceAsync
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

/**
 * Gets the path to the IJent binary. See [getIjentBinary].
 */
@Internal
interface IjentExecFileProvider {
  companion object {
    suspend fun getInstance(): IjentExecFileProvider = serviceAsync()
  }

  enum class SupportedPlatform(private val os: OS, private val arch: Arch) {
    AARCH64__DARWIN(OS.DARWIN, Arch.AARCH64),
    AARCH64__LINUX(OS.LINUX, Arch.AARCH64),
    X86_64__DARWIN(OS.DARWIN, Arch.X86_64),
    X86_64__LINUX(OS.LINUX, Arch.X86_64),
    X86_64__WINDOWS(OS.WINDOWS, Arch.X86_64);

    enum class Arch(internal val names: Set<String>) {
      AARCH64(setOf("arm64", "aarch64")),
      X86_64(setOf("amd64", "x86_64", "x86-64")),
    }

    enum class OS {
      DARWIN,
      LINUX,
      WINDOWS,
    }

    companion object {
      fun getFor(os: String, arch: String): SupportedPlatform? {
        val osEnum =
          OS.entries.find { it.name.equals(os, ignoreCase = true) }
          ?: return null
        val archEnum =
          Arch.entries.find { arch.lowercase() in it.names }
          ?: return null
        return entries.find { it.os == osEnum && it.arch == archEnum }
      }
    }
  }

  /**
   * Gets the path to the IJent binary. Suggests to install the plugin via dialog windows, so the method may work unpredictably long.
   */
  @Throws(IjentMissingBinary::class)
  suspend fun getIjentBinary(targetPlatform: SupportedPlatform): Path
}

class IjentMissingBinary(platform: IjentExecFileProvider.SupportedPlatform) : Exception("Failed to get an IJent binary for $platform") {
  override fun getLocalizedMessage(): String = IjentBundle.message("failed.to.get.ijent.binary")
}

val IjentExecFileProvider.SupportedPlatform.executableName: String
  get() = when (this) {
    IjentExecFileProvider.SupportedPlatform.AARCH64__DARWIN -> "ijent-aarch64-apple-darwin-release"
    IjentExecFileProvider.SupportedPlatform.AARCH64__LINUX -> "ijent-aarch64-unknown-linux-musl-release"
    IjentExecFileProvider.SupportedPlatform.X86_64__DARWIN -> "ijent-x86_64-apple-darwin-release"
    IjentExecFileProvider.SupportedPlatform.X86_64__LINUX -> "ijent-x86_64-unknown-linux-musl-release"
    IjentExecFileProvider.SupportedPlatform.X86_64__WINDOWS -> "ijent-x86_64-pc-windows-gnu-release.exe"
  }

internal class DefaultIjentExecFileProvider : IjentExecFileProvider {
  override suspend fun getIjentBinary(targetPlatform: IjentExecFileProvider.SupportedPlatform): Nothing =
    throw IjentMissingBinary(targetPlatform)
}