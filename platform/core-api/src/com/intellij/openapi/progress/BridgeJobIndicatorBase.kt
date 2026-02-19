// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

/**
 * A base class for progress indicators that depend on an instance of a job.
 *
 * The motivation is the following:
 * ```kotlin
 * coroutineToIndicator { indicator ->
 *   runProcess({}, indicator)
 * }
 * ```
 * Here, [ProgressManager.runProcess] calls [ProgressIndicator.start] on `indicator`,
 * which would lead to removal of cancellations status for [EmptyProgressIndicator]. To avoid it, we reimplement [EmptyProgressIndicatorBase] directly
 */
@ApiStatus.Internal
@Suppress("UsagesOfObsoleteApi")
internal abstract class BridgeJobIndicatorBase(modalityState: ModalityState): EmptyProgressIndicatorBase(modalityState), StandardProgressIndicator {
  companion object {
    private val PLACEHOLDER: Throwable = Throwable(
      "Dummy throwable that indicates cancellation of EmptyProgressIndicator.\nSet `ide.rich.cancellation.traces` to `true` to get real origin of cancellation.")
  }

  @Volatile
  private var cancellationCause: Throwable? = null

  override fun cancel() {
    cancellationCause = if (Registry.`is`("ide.rich.cancellation.traces", false)) {
      Throwable("Origin of cancellation of $this")
    }
    else {
      PLACEHOLDER
    }
  }

  override fun isCanceled(): Boolean {
    return cancellationCause != null
  }

  override fun getCancellationCause(): Throwable? {
    return cancellationCause.takeIf { it != PLACEHOLDER }
  }
}
