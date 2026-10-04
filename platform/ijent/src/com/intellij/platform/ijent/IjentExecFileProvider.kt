// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.EelUnavailableException
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

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

/**
 * Replaces the modal progress that [IjentExecFileProvider] shows while it provisions the IJent binary.
 * A caller puts this element into the coroutine context to show the progress in its own UI.
 *
 * The IJent session is shared between callers, so only the element of the caller that starts the session takes effect.
 */
@Internal
interface IjentProvisioningProgress : CoroutineContext.Element {
  /**
   * Runs [task] and shows [text] as its progress.
   * A cancellation by the user throws [kotlinx.coroutines.CancellationException].
   */
  suspend fun <T> withProgress(text: @Nls String, task: suspend () -> T): T

  override val key: CoroutineContext.Key<*> get() = Key

  companion object Key : CoroutineContext.Key<IjentProvisioningProgress>
}

@Suppress("HardCodedStringLiteral") // Internal diagnostic message, not user-facing UI text.
class IjentMissingBinary(
  platform: EelPlatform,
  cause: String? = null,
) : EelUnavailableException("Failed to get an IJent binary for $platform" + cause?.let { ": $cause" }) {
  override fun toString(): String = "${javaClass.name}: $message"
}