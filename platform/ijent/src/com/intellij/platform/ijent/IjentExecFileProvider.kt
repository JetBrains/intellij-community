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

  sealed interface SupportedPlatform {
    sealed interface Posix : SupportedPlatform
    sealed interface Linux : Posix
    sealed interface Darwin : Posix
    sealed interface Windows : SupportedPlatform

    data object Arm64Darwin : Darwin
    data object Aarch64Linux : Linux
    data object X8664Darwin : Darwin
    data object X8664Linux : Linux
    data object X64Windows : Windows

    companion object {
      fun getFor(os: String, arch: String): SupportedPlatform? =
        when (os.lowercase()) {
          "darwin" -> when (arch.lowercase()) {
            "arm64", "aarch64" -> Arm64Darwin
            "amd64", "x86_64", "x86-64" -> X8664Darwin
            else -> null
          }
          "linux" -> when (arch.lowercase()) {
            "arm64", "aarch64" -> Aarch64Linux
            "amd64", "x86_64", "x86-64" -> X8664Linux
            else -> null
          }
          "windows" -> when (arch.lowercase()) {
            "amd64", "x86_64", "x86-64" -> X64Windows
            else -> null
          }
          else -> null
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
    IjentExecFileProvider.SupportedPlatform.Arm64Darwin -> "ijent-aarch64-apple-darwin-release"
    IjentExecFileProvider.SupportedPlatform.X8664Darwin -> "ijent-x86_64-apple-darwin-release"
    IjentExecFileProvider.SupportedPlatform.Aarch64Linux -> "ijent-aarch64-unknown-linux-musl-release"
    IjentExecFileProvider.SupportedPlatform.X8664Linux -> "ijent-x86_64-unknown-linux-musl-release"
    IjentExecFileProvider.SupportedPlatform.X64Windows -> "ijent-x86_64-pc-windows-gnu-release.exe"
  }

internal class DefaultIjentExecFileProvider : IjentExecFileProvider {
  override suspend fun getIjentBinary(targetPlatform: IjentExecFileProvider.SupportedPlatform): Nothing =
    throw IjentMissingBinary(targetPlatform)
}