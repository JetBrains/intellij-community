// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.preferences.isUsingMicroSpans
import fleet.tracing.SpanInfoBuilder
import fleet.tracing.span
import fleet.tracing.spannedScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope

private fun <T> maybeSpan(shouldSpan: Boolean, name: String, info: SpanInfoBuilder.() -> Unit = {}, body: () -> T): T =
  if (shouldSpan) {
    span(name, info, body)
  }
  else {
    body()
  }

private suspend fun <T> maybeSpannedScope(shouldSpan: Boolean,
                                          name: String,
                                          info: SpanInfoBuilder.() -> Unit = {},
                                          body: suspend CoroutineScope.() -> T): T =
  if (shouldSpan) {
    spannedScope(name, info, body)
  }
  else {
    coroutineScope(body)
  }


suspend fun <T> frequentSpannedScope(name: String,
                                     info: SpanInfoBuilder.() -> Unit = {},
                                     body: suspend CoroutineScope.() -> T): T =
  maybeSpannedScope(isUsingMicroSpans, name, info, body)
  
fun <T> frequentSpan(name: String, info: SpanInfoBuilder.() -> Unit = {}, body: () -> T) =
  maybeSpan(isUsingMicroSpans, name, info, body)