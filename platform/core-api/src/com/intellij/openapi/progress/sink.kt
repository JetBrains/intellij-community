// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.openapi.progress

import com.intellij.openapi.application.ModalityState
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
fun ProgressSink.asContextElement(): CoroutineContext.Element {
  return ProgressSinkElement(this)
}

val CoroutineContext.progressSink: ProgressSink? get() = this[ProgressSinkKey]?.sink

val CoroutineScope.progressSink: ProgressSink? get() = coroutineContext.progressSink

private object ProgressSinkKey : CoroutineContext.Key<ProgressSinkElement>
private class ProgressSinkElement(val sink: ProgressSink) : AbstractCoroutineContextElement(ProgressSinkKey)

internal class ProgressIndicatorSink(private val indicator: ProgressIndicator) : ProgressSink {

  override fun update(text: @ProgressText String?, details: @ProgressDetails String?, fraction: Double?) {
    if (text != null) {
      indicator.text = text
    }
    if (details != null) {
      indicator.text2 = details
    }
    if (fraction != null) {
      indicator.fraction = fraction
    }
  }

  override fun text(text: @ProgressText String) {
    indicator.text = text
  }

  override fun details(details: @ProgressDetails String) {
    indicator.text2 = details
  }

  override fun fraction(fraction: Double) {
    indicator.fraction = fraction
  }
}

internal class ProgressSinkIndicator(
  private val sink: ProgressSink,
  contextModality: ModalityState,
) : EmptyProgressIndicator(contextModality) {

  override fun setText(text: String?) {
    if (text != null) {
      sink.text(text)
    }
  }

  override fun setText2(text: String?) {
    if (text != null) {
      sink.details(text)
    }
  }

  override fun setFraction(fraction: Double) {
    sink.fraction(fraction)
  }
}
