// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.tracing

import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class TracingCoroutineElement(private val otelContext: Context) : ThreadContextElement<Scope>, AbstractCoroutineContextElement(
  TracingCoroutineElement) {
  companion object Key : CoroutineContext.Key<TracingCoroutineElement>

  override fun restoreThreadContext(context: CoroutineContext, oldState: Scope) {
    oldState.close()
  }

  override fun updateThreadContext(context: CoroutineContext): Scope {
    return otelContext.makeCurrent()
  }
}

fun Span.asContextElement(): CoroutineContext.Element {
  return TracingCoroutineElement(Context.current().with(this))
}