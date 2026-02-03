// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.eel.EelPlatform
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

  /**
   * Gets the path to the IJent binary. Suggests to install the plugin via dialog windows, so the method may work unpredictably long.
   */
  @Throws(IjentMissingBinary::class)
  suspend fun getIjentBinary(targetPlatform: EelPlatform): Path
}

class IjentMissingBinary(platform: EelPlatform) : Exception("Failed to get an IJent binary for $platform") {
  override fun getLocalizedMessage(): String = IjentBundle.message("failed.to.get.ijent.binary")
}

internal class DefaultIjentExecFileProvider : IjentExecFileProvider {
  override suspend fun getIjentBinary(targetPlatform: EelPlatform): Nothing =
    throw IjentMissingBinary(targetPlatform)
}