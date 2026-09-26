// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A [CoroutineContext.Element] that allows to control how unhandled exceptions are logged
 * by the [com.intellij.openapi.application.impl.CoroutineExceptionHandlerImpl].
 *
 * When present, this element will override the default exception handling behavior.
 *
 * See [com.intellij.openapi.application.impl.processUnhandledException].
 */
@ApiStatus.Internal
sealed class UnhandledExceptionLoggingMode : AbstractCoroutineContextElement(Key) {
  /**
   * With this mode, all exceptions are displayed to the user immediately.
   */
  class Interactive(val action: @Nls String) : UnhandledExceptionLoggingMode()

  /**
   * With this mode, all exceptions are going to be logged into the log file.
   */
  object NonInteractive : UnhandledExceptionLoggingMode()

  companion object Key : CoroutineContext.Key<UnhandledExceptionLoggingMode>
}

/**
 * Kept for binary compatibility reasons.
 *
 * Use [UnhandledExceptionLoggingMode.Interactive] instead.
 */
@Deprecated("Use UnhandledExceptionLoggingMode.Interactive instead")
@ApiStatus.Internal
class Interactive(val action: @Nls String) : AbstractCoroutineContextElement(Key) {
  @Suppress("DEPRECATION")
  companion object Key : CoroutineContext.Key<Interactive>
}


/**
 * Launches code in [UnhandledExceptionLoggingMode.Interactive] mode
 */
@ApiStatus.Internal
fun CoroutineScope.launchInteractive(action: @Nls String, context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> Unit) {
  launch(context + UnhandledExceptionLoggingMode.Interactive(action)) { block() }
}
