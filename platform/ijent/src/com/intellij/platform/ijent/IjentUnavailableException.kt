// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ExceptionWithAttachments
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * This error declares that communication with a specific IJent is impossible anymore.
 * To keep working with a remote machine, a new IJent should be launched.
 */
sealed class IjentUnavailableException : IOException, ExceptionWithAttachments {
  private val attachments: Array<out Attachment>

  constructor(message: String) : super(message) {
    attachments = emptyArray()
  }

  constructor(message: String, cause: Throwable) : super(message, cause) {
    attachments = emptyArray()
  }

  constructor(message: String, vararg attachments: Attachment) : super(message) {
    this.attachments = attachments
  }

  constructor(message: String, cause: Throwable, vararg attachments: Attachment) : super(message, cause) {
    this.attachments = attachments
  }

  class ClosedByApplication : IjentUnavailableException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
  }

  class CommunicationFailure : IjentUnavailableException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(message: String, vararg attachments: Attachment) : super(message, *attachments)
    constructor(message: String, cause: Throwable, vararg attachments: Attachment) : super(message, cause, *attachments)

    var exitedExpectedly: Boolean = false
      internal set
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
  }
}