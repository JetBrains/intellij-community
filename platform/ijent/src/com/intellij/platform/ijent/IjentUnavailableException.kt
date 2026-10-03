// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.platform.eel.EelUnavailableException
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

/**
 * This error declares that communication with a specific IJent is impossible anymore.
 * To keep working with a remote machine, a new IJent should be launched.
 */
@Deprecated("Use EelUnavailableException instead")
class IjentUnavailableException(message: @Nls String) : EelUnavailableException(message) {
  /**
   * The IDE or the user ended the session on purpose. It is an [EelUnavailableException.ClosedByApplication].
   * New code uses [EelUnavailableException.ClosedByApplication].
   *
   * The use cases and the other error kinds are in `platform/ijent/docs/internal/scope-lifetime.md`.
   */
  @Deprecated("Use EelUnavailableException instead")
  class ClosedByApplication(
    message: String,
    cause: Throwable?,
  ) : EelUnavailableException.ClosedByApplication(message, cause)

  /**
   * The communication with IJent broke. It is an [EelUnavailableException.CommunicationFailure].
   *
   * New code uses [EelUnavailableException.CommunicationFailure].
   * Use this class only to pass [attachments]. The Eel module does not know [Attachment].
   *
   * The use cases and the other error kinds are in `platform/ijent/docs/internal/scope-lifetime.md`.
   */
  @Deprecated("Use EelUnavailableException instead")
  class CommunicationFailure(
    message: String,
    cause: Throwable?,
    private vararg val attachments: Attachment,
  ) : EelUnavailableException.CommunicationFailure(message, cause)

  companion object {
    @Internal
    @JvmStatic
    inline fun <T> unwrapFromCancellationExceptions(body: () -> T): T =
      EelUnavailableException.unwrapFromCancellationExceptions(body)

    @Internal
    @JvmStatic
    fun unwrapFromCancellationExceptions(initialError: Throwable): EelUnavailableException? =
      EelUnavailableException.unwrapFromCancellationExceptions(initialError)
  }
}