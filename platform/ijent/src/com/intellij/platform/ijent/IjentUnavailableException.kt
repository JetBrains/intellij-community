// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ExceptionWithAttachments
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * This error declares that communication with a specific IJent is impossible anymore.
 * To keep working with a remote machine, a new IJent should be launched.
 */
sealed class IjentUnavailableException : IOException, ExceptionWithAttachments {
  private val attachments: Array<out Attachment>

  constructor(message: String, cause: Throwable?, vararg attachments: Attachment) : super(message, cause) {
    this.attachments = attachments
  }

  class ClosedByApplication(message: String, cause: Throwable?) : IjentUnavailableException(message, cause)

  class CommunicationFailure(
    message: String,
    cause: Throwable?,
    vararg attachments: Attachment,
  ) : IjentUnavailableException(message, cause, *attachments) {
    var exitedExpectedly: Boolean = false
  }

  override fun getAttachments(): Array<out Attachment> = attachments

  companion object {
    @Internal
    @JvmStatic
    inline fun <T> unwrapFromCancellationExceptions(body: () -> T): T =
      try {
        body()
      }
      catch (initialError: Throwable) {
        throw unwrapFromCancellationExceptions(initialError)
      }

    @Internal
    @JvmStatic
    fun unwrapFromCancellationExceptions(initialError: Throwable): Throwable {
      var err: Throwable? = initialError
      while (true) {
        when (err) {
          is CancellationException -> err = err.cause

          is IjentUnavailableException -> return err

          else -> break
        }
      }
      return initialError
    }

    /**
     * The default bound used by [resolveDeadSessionReason] when awaiting the canonical exit reason.
     * Aligned with the exit-code consumer await in `GrpcIjentChildProcess`.
     */
    @Internal
    val DEAD_SESSION_RESOLVE_TIMEOUT: Duration = 3.seconds

    /**
     * Resolves the canonical dead-session [IjentUnavailableException] for [initialError].
     *
     * First unwraps [CancellationException]s; if that already yields an [IjentUnavailableException], it is returned
     * immediately. Otherwise, if the current coroutine runs inside an [IjentScope.IjentContext], its authoritative
     * [IjentScope.IjentContext.exitReason] is awaited for at most [timeout] (in a [NonCancellable] section, so a
     * cancelled boundary can still obtain the reason). Falls back to the unwrapped [initialError] if no canonical
     * reason becomes available within the bound.
     */
    @Internal
    suspend fun resolveDeadSessionReason(
      initialError: Throwable,
      timeout: Duration = DEAD_SESSION_RESOLVE_TIMEOUT,
    ): Throwable {
      val unwrapped = unwrapFromCancellationExceptions(initialError)
      if (unwrapped is IjentUnavailableException) return unwrapped
      val ijentContext = currentCoroutineContext()[IjentScope.IjentContext.Key] ?: return unwrapped
      val resolved = withContext(NonCancellable) { ijentContext.resolveExitReason(timeout) }
      return resolved ?: unwrapped
    }
  }
}

/**
 * A reusable [com.intellij.platform.eel.SafeDeferred] dead-session mapper for IJent-owned deferreds.
 *
 * When the backing deferred fails, this maps the failure to the canonical [IjentUnavailableException] (resolving it
 * from the ambient [IjentScope.IjentContext] if necessary) so that `SafeDeferred.await` wraps the canonical
 * [IjentUnavailableException] (instead of the low-level failure) into `SafeDeferred.FailedDeferred`.
 * Returns `null` for failures that are not attributable to a dead session, preserving the default `FailedDeferred`
 * behavior.
 */
@Internal
val IJENT_DEAD_SESSION_SAFE_DEFERRED_MAPPER: suspend (Throwable) -> Throwable? = { err ->
  IjentUnavailableException.resolveDeadSessionReason(err).takeIf { it is IjentUnavailableException }
}