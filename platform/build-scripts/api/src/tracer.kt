// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The tracer of the current build task.
 *
 * An entry point installs a real tracer. The default one records nothing, so a module that traces its work costs
 * nothing in a run that asks for no trace. This holder exists because a module of the build model must not depend
 * on the exporters of the telemetry module.
 */
@ApiStatus.Internal
object BuildTracer {
  private val noopTracer: Tracer = TracerProvider.noop().get("build-script")

  @Volatile
  private var tracer: Tracer = noopTracer

  /** Installs [tracer] until the returned handle closes. A handle closes in the reverse order of the installs. */
  fun install(tracer: Tracer): AutoCloseable {
    val handle = Handle(previousTracer = this.tracer)
    this.tracer = tracer
    return handle
  }

  fun spanBuilder(name: String): SpanBuilder = tracer.spanBuilder(name)

  private class Handle(private val previousTracer: Tracer) : AutoCloseable {
    private val isClosed = AtomicBoolean()

    override fun close() {
      if (isClosed.compareAndSet(false, true)) {
        tracer = previousTracer
      }
    }
  }
}

/**
 * Runs [operation] in a span that is current on the calling thread.
 *
 * A span that [operation] starts gets this span as its parent. The span ends even when [operation] throws, and a
 * failure marks the span as an error.
 */
@ApiStatus.Internal
inline fun <T> buildSpan(name: String, crossinline operation: (Span) -> T): T {
  val span = BuildTracer.spanBuilder(name).startSpan()
  try {
    return span.makeCurrent().use { operation(span) }
  }
  catch (e: Throwable) {
    span.recordException(e)
    span.setStatus(StatusCode.ERROR)
    throw e
  }
  finally {
    span.end()
  }
}
