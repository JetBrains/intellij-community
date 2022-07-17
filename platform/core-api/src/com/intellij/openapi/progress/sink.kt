// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.openapi.progress

import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

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

internal val CoroutineContext.progressSink: ProgressSink? get() = this[ProgressSinkKey]?.sink

val CoroutineScope.progressSink: ProgressSink? get() = coroutineContext.progressSink

// kotlin doesn't allow 'suspend' modifier on properties
suspend fun progressSink(): ProgressSink? = coroutineContext.progressSink

private object ProgressSinkKey : CoroutineContext.Key<ProgressSinkElement>
private class ProgressSinkElement(val sink: ProgressSink) : AbstractCoroutineContextElement(ProgressSinkKey)

internal class ProgressIndicatorSink(private val indicator: ProgressIndicator) : ProgressSink {

  override fun text(text: String) {
    indicator.text = text
  }

  override fun details(details: String) {
    indicator.text2 = details
  }

  override fun fraction(fraction: Double) {
    indicator.fraction = fraction
  }
}

internal class ProgressSinkIndicator(private val sink: ProgressSink) : EmptyProgressIndicator() {

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
