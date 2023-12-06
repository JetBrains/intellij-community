// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.components.serviceAsync
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

private const val LINUX = "linux"
private const val DARWIN = "darwin"
private const val WINDOWS = "windows"

private val arm64Names = setOf("arm64", "aarch64")
private val amd64Names = setOf("amd64", "x86_64", "x86-64")

/**
 * Gets the path to the IJent binary. See [getIjentBinary].
 */
@Internal
interface IjentExecFileProvider {
  companion object {
    suspend fun getInstance(): IjentExecFileProvider = serviceAsync()
  }

  enum class SupportedPlatform(private val os: String, private val archNames: Set<String>) {
    AARCH64__DARWIN(DARWIN, arm64Names),
    AARCH64__LINUX(LINUX, arm64Names),
    X86_64__DARWIN(DARWIN, amd64Names),
    X86_64__LINUX(LINUX, amd64Names),
    X86_64__WINDOWS(WINDOWS, amd64Names);

    fun isFor(os: String, arch: String): Boolean {
      return os.lowercase().trim() == this.os && arch.lowercase().trim() in this.archNames
    }

    companion object {
      fun getFor(os: String, arch: String) = entries.firstOrNull { it.isFor(os, arch) }
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