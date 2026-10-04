// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Thrown when an EEL cannot be accessed or initialized.
 *
 * This exception indicates that the target execution environment (such as a remote machine,
 * Docker container, or WSL instance) is temporarily or permanently unavailable.
 *
 * Common scenarios include:
 * - Docker daemon connection failures
 * - Container not found or stopped
 * - Remote SSH connection issues
 * - Environment-specific setup errors
 *
 * This exception is typically thrown during:
 * - Eel initialization
 * - Project opening when the remote environment is unavailable
 *
 * The exception should contain a localized user-facing message explaining the specific
 * reason for unavailability, and optionally wrap the underlying cause.
 *
 * @param message Localized user-facing error message explaining why the EEL is unavailable
 * @param cause Optional underlying exception that caused the unavailability
 *
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
open class EelUnavailableException @ApiStatus.Internal constructor(
  override val message: @Nls String,
  cause: Throwable? = null,
) : IOException(message, cause) {
  /**
   * The environment was closed on purpose by the IDE or by the user.
   * To keep working with the environment, the caller has to start it again.
   */
  @Suppress("HardCodedStringLiteral")  // It's unfeasible to translate all possible low-level messages.
  open class ClosedByApplication @ApiStatus.Internal constructor(
    message: String,
    cause: Throwable?,
  ) : EelUnavailableException(message, cause)

  /**
   * The communication with the environment broke, and the environment cannot be used anymore.
   * To keep working with the environment, the caller has to start it again.
   */
  @Suppress("HardCodedStringLiteral")  // It's unfeasible to translate all possible low-level messages.
  open class CommunicationFailure @ApiStatus.Internal constructor(
    message: String,
    cause: Throwable?,
  ) : EelUnavailableException(message, cause) {
    /**
     * The failure has a cause the IDE could name and has already put in front of the user: a condition of the
     * environment, not a defect. It still ends the session, but it is not an IDE error report.
     */
    var diagnosed: Boolean = false
  }

  companion object {
    // TODO Not the best place for these functions.
    @ApiStatus.Internal
    @JvmStatic
    inline fun <T> unwrapFromCancellationExceptions(body: () -> T): T =
      try {
        body()
      }
      catch (initialError: Throwable) {
        throw unwrapFromCancellationExceptions(initialError) ?: initialError
      }

    @ApiStatus.Internal
    @JvmStatic
    fun unwrapFromCancellationExceptions(initialError: Throwable): EelUnavailableException? {
      var err: Throwable? = initialError
      while (true) {
        when (err) {
          is CancellationException -> err = err.cause

          is EelUnavailableException -> return err

          else -> break
        }
      }
      return null
    }
  }
}
