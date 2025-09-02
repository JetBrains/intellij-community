// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ExceptionWithAttachments
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import java.lang.reflect.Field

private val logger = logger<RemoteSerializedThrowable>()

/**
 * Throwable that replaces its type and other data like stacktrace with given from a remote Throwable
 *
 * @param message ordinary message
 * @param localizedMessage localized message
 * @param classFqn FQN of original throwable
 * @param stacktrace stacktrace of original throwable
 * @param cause nested throwable (can be any exception)
 * @param headerPrefix prefix that will be added in front of the throwable text
 */
@ApiStatus.Internal
class RemoteSerializedThrowable(
  message: String?,
  private val localizedMessage: String?,
  val classFqn: String,
  stacktrace: Array<StackTraceElement>,
  cause: Throwable? = null,
  private val headerPrefix: String? = null,
  private val attachments: Array<Attachment> = emptyArray(),
) : Throwable(message, cause), ExceptionWithAttachments {
  companion object {
    private val BACKTRACE_FIELD: Field? = try {
      Throwable::class.java.getDeclaredField("backtrace").also {
        it.isAccessible = true
      }
    }
    catch (t: Throwable) {
      logger.warn("Failed to get or make writable field 'backtrace'", t)
      null
    }

    private val STACKTRACE_FIELD: Field? = try {
      Throwable::class.java.getDeclaredField("stackTrace").also {
        it.isAccessible = true
      }
    }
    catch (t: Throwable) {
      logger.warn("Failed to get or make writable field 'stackTrace'", t)
      null
    }
  }

  init {
    try {
      BACKTRACE_FIELD?.set(this, null)
      STACKTRACE_FIELD?.set(this, stacktrace)
    }
    catch (t: Throwable) {
      logger.warn("Failed to set a field via reflection", t)
    }
  }

  override fun getLocalizedMessage(): String? = localizedMessage

  override fun getAttachments(): Array<out Attachment> = attachments

  override fun toString(): String {
    val typePrefix = if (headerPrefix != null) "[$headerPrefix] $classFqn" else classFqn
    val message = localizedMessage ?: message
    return if (message != null) "$typePrefix: $message" else typePrefix
  }
}
