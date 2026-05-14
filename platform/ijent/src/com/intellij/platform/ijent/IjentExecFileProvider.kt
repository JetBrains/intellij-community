// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.eel.EelPlatform
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

/**
 * Gets the path to the IJent binary. See [getIjentBinary].
 */
@Internal
interface IjentExecFileProvider {
  /**
   * Gets the path to the IJent binary. Suggests to install the plugin via dialog windows, so the method may work unpredictably long.
   */
  @Throws(IjentMissingBinary::class)
  suspend fun getIjentBinary(targetPlatform: EelPlatform): Path
}

class IjentMissingBinary(platform: EelPlatform, cause: String? = null) : Exception("Failed to get an IJent binary for $platform" + cause?.let { ": $cause" }) {
  override fun toString(): String = "${javaClass.name}: $message"
}