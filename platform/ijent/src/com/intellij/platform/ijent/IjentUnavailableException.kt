// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ExceptionWithAttachments
import com.intellij.platform.eel.EelUnavailableException
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * This error declares that communication with a specific IJent is impossible anymore.
 * To keep working with a remote machine, a new IJent should be launched.
 */
sealed class IjentUnavailableException : EelUnavailableException, ExceptionWithAttachments {
  private val attachments: Array<out Attachment>

  constructor(message: String, cause: Throwable?, vararg attachments: Attachment) : super(message, cause) {
    this.attachments = attachments
  }

  /**
   * The IDE or the user ended the session on purpose. It is not a failure, and it is never an IDE error report.
   *
   * The use cases and the other error kinds are in `platform/ijent/docs/internal/scope-lifetime.md`.
   */
  class ClosedByApplication(message: String, cause: Throwable?) : IjentUnavailableException(message, cause)

  /**
   * The session ended because of a failure.
   * The failure is an IDE error report, unless it is [diagnosed].
   *
   * The use cases and the other error kinds are in `platform/ijent/docs/internal/scope-lifetime.md`.
   */
  class CommunicationFailure(
    message: String,
    cause: Throwable?,
    vararg attachments: Attachment,
  ) : IjentUnavailableException(message, cause, *attachments) {
    /**
     * The failure has a cause the IDE could name and has already put in front of the user: a condition of the
     * environment, not a defect. It still ends the session, but it is not an IDE error report.
     *
     * The flag has an effect only when the failure is the exit reason of [IjentScope].
     * So destroy the scope with it and `isRootCause = true`.
     */
    var diagnosed: Boolean = false
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
        throw unwrapFromCancellationExceptions(initialError) ?: initialError
      }

    @Internal
    @JvmStatic
    fun unwrapFromCancellationExceptions(initialError: Throwable): IjentUnavailableException? {
      var err: Throwable? = initialError
      while (true) {
        when (err) {
          is CancellationException -> err = err.cause

          is IjentUnavailableException -> return err

          else -> break
        }
      }
      return null
    }

    /**
     * The default bound used by [resolveDeadSessionReason] when awaiting the canonical exit reason.
     * Aligned with the exit-code consumer await in `GrpcIjentChildProcess`.
     */
    @Internal
    val DEAD_SESSION_RESOLVE_TIMEOUT: Duration = 3.seconds  // 3 seconds are taken at random, feel free to experiment with the value.
  }
}