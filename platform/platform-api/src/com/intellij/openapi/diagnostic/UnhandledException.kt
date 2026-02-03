// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import org.jetbrains.annotations.ApiStatus

/**
 * Exception that hit the top of a thread stack without being caught.
 * If [isInteractive] then it interrupted user action (i.e. modal dialog).
 * Otherwise, it was a background exception (which is also a bug, but probably less important).
 * See IJPL-100
 */
@ApiStatus.Internal
class UnhandledException(override val cause: Throwable, val isInteractive: Boolean) : Exception(cause) {
  override val message: String? = cause.message

  override fun getLocalizedMessage(): String? = cause.localizedMessage

  override fun getStackTrace(): Array<out StackTraceElement> = cause.stackTrace
}