// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.openapi.progress

import com.intellij.openapi.util.NlsContexts.ProgressDetails
import com.intellij.openapi.util.NlsContexts.ProgressText
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Usage example:
 * ```
 * val sink = new MyCoolSink()
 * // launch a coroutine to update UI with data from MyCoolSink
 * withContext(sink.asContextElement()) {
 *   // available on CoroutineScope as a property
 *   // safe null operator allows to skip the evaluation of the resource bundle part
 *   progressSink?.text(ResourceBundle.message("starting.progress.text"))
 *   ...
 *   doStuff()
 * }
 * suspend fun doStuff() {
 *   // available inside suspend functions as a function
 *   progressSink()?.details("stuff")
 * }
 * ```
 */
@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated(message = "Migrate to `ProgressReporter`")
fun ProgressSink.asContextElement(): CoroutineContext.Element {
  return ProgressSinkElement(this)
}

/**
 * @see progressReporter
 * @see rawProgressReporter
 */
@Deprecated(message = "Migrate to `ProgressReporter`")
val CoroutineContext.progressSink: ProgressSink? get() = this[ProgressSinkKey]?.sink ?: fromReporter()

private fun CoroutineContext.fromReporter(): ProgressSink? {
  val reporter = this.rawProgressReporter
                 ?: return null
  return object : ProgressSink {
    override fun update(text: @ProgressText String?, details: @ProgressDetails String?, fraction: Double?) {
      if (text != null) {
        reporter.text(text)
      }
      if (details != null) {
        reporter.details(details)
      }
      if (fraction != null) {
        reporter.fraction(fraction)
      }
    }
  }
}

/**
 * @see progressReporter
 * @see rawProgressReporter
 */
@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated(message = "Migrate to `ProgressReporter`")
val CoroutineScope.progressSink: ProgressSink? get() = coroutineContext.progressSink

private object ProgressSinkKey : CoroutineContext.Key<ProgressSinkElement>
private class ProgressSinkElement(val sink: ProgressSink) : AbstractCoroutineContextElement(ProgressSinkKey)
