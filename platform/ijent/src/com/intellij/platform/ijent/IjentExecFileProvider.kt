// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.util.annotations.VisibleToClasses
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

  /** Reports a protocol mismatch for [binary] and returns the failure for the caller. */
  fun reportIncompatibleBinary(binary: Path, error: IjentIncompatibleBinary): EelUnavailableException {
    IjentLogger.LIFETIME_LOG.error(error.message, error)
    return error
  }
}

/** The IJent binary uses a protocol that is incompatible with this IDE. Absence of this exception does not mean that the binary is compatible. */
@Internal
@VisibleToClasses(
  "com.intellij.platform.ijent.execFileProvider.ProductionIjentExecFileProvider",
  "com.intellij.platform.ijent.impl.base.GrpcIjentSessionProviderKt",
  "com.intellij.platform.ijent.impl.base.IjentProtocolHashTest",
  "com.intellij.platform.ijent.spi.IjentDeployingOverShellProcessStrategyUnitTest",
)
class IjentIncompatibleBinary(message: String, cause: Throwable? = null) : EelUnavailableException.Conclusive(message, cause) {
  override fun copyForCaller(): EelUnavailableException = IjentIncompatibleBinary(message, this)
}

/**
 * Replaces the modal progress that [IjentExecFileProvider] shows while it provisions the IJent binary.
 * A caller puts this element into the coroutine context to show the progress in its own UI.
 *
 * The IJent session is shared between callers, so only the element of the caller that starts the session takes effect.
 */
@Internal
@VisibleToClasses(
  "com.intellij.platform.daemon.connection.api.InlineIjentProvisioningProgress",
  "com.intellij.platform.ijent.community.impl.IndicatorIjentProvisioningProgress",
  "com.intellij.platform.ijent.execFileProvider.ProductionIjentExecFileProvider",
  "com.intellij.platform.ijent.ssh.IndicatorIjentProvisioningProgressTest",
  "com.intellij.platform.ijent.ssh.impl.AbstractIjentSshManager",
  "com.intellij.platform.ijent.ssh.impl.PlatformSshEelApiFactory",
)
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
@VisibleToClasses(
  "com.intellij.platform.ijent.community.ui.actions.AbstractIjentVerificationAction",
  "com.intellij.platform.ijent.execFileProvider.ProductionIjentExecFileProvider",
  "com.intellij.platform.ijent.execFileProvider.ProductionIjentExecFileProviderKt",
  "com.intellij.platform.ijent.execFileProvider.UnknownThirdPartyPlugin",
  "com.intellij.platform.ijent.spi.IjentDeployingOverShellProcessStrategyUnitTest",
)
open class IjentMissingBinary private constructor(message: String, cause: Throwable?) : EelUnavailableException.Conclusive(message, cause) {
  constructor(
    platform: EelPlatform,
    cause: String? = null,
  ) : this("Failed to get an IJent binary for $platform" + cause?.let { ": $cause" }, null)

  override fun copyForCaller(): EelUnavailableException = IjentMissingBinary(message, this)

  override fun toString(): String = "${javaClass.name}: $message"
}