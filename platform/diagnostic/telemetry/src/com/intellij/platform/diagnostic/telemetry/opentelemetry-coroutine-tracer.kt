// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)

package com.intellij.platform.diagnostic.telemetry

import com.intellij.diagnostic.StartUpMeasurer
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadableSpan
import kotlinx.coroutines.*
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

fun Span.asContextElement(tracer: Tracer, parentContext: Context = Context.current()): CoroutineContext {
  return CoroutineTimeMeasurer(span = this, parentContext = parentContext, tracer = tracer, parentSpan = null)
}

/**
 * This function is designed to be used instead of `withContext(CoroutineName("subtask")) { ... }`.
 * See https://github.com/Kotlin/kotlinx.coroutines/issues/3414
 */
@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
suspend fun <T> subtask(
  name: String,
  context: CoroutineContext = EmptyCoroutineContext,
  action: suspend CoroutineScope.() -> T,
): T {
  val namedContext = context + CoroutineName(name)
  val measurer = coroutineContext[CoroutineTimeMeasurerKey]
  return if (measurer == null) {
    withContext(namedContext, action)
  }
  else {
    withContext(namedContext + measurer.copyForChild(), action)
  }
}

private object CoroutineTimeMeasurerKey : CoroutineContext.Key<CoroutineTimeMeasurer>

private class CoroutineTimeMeasurer(span: Span?,
                                    private val parentSpan: ReadableSpan?,
                                    private val parentContext: Context,
                                    private val tracer: Tracer) : CopyableThreadContextElement<Unit> {
  companion object {
    private val CURRENT_SPAN: VarHandle
    private val LAST_SUSPENSION_TIME: VarHandle

    init {
      val lookup = MethodHandles.privateLookupIn(CoroutineTimeMeasurer::class.java, MethodHandles.lookup())
      CURRENT_SPAN = lookup.findVarHandle(CoroutineTimeMeasurer::class.java, "currentSpan", ReadableSpan::class.java)
      LAST_SUSPENSION_TIME = lookup.findVarHandle(CoroutineTimeMeasurer::class.java, "lastSuspensionTime", Long::class.javaPrimitiveType)
    }
  }

  init {
    if (span != null) {
      CURRENT_SPAN.setVolatile(this, span)
    }
  }

  override val key: CoroutineContext.Key<*>
    get() = CoroutineTimeMeasurerKey

  private val creationTime: Long = System.nanoTime()

  @Suppress("unused")
  private var currentSpan: ReadableSpan? = null

  @Suppress("unused")
  private var lastSuspensionTime: Long = -1

  override fun toString(): String {
    val span = CURRENT_SPAN.getVolatile(this) as ReadableSpan?
    return when {
      //activity === noActivity -> "no parent and no name"
      span != null -> span.toString()
      else -> "uninitialized child of $parentContext"
    }
  }

  override fun updateThreadContext(context: CoroutineContext) {
    if (CURRENT_SPAN.getVolatile(this) != null) {
      // already initialized
      return
    }

    val coroutineName = context[CoroutineName]?.name
    if (coroutineName == null || parentSpan!!.name == coroutineName) {
      // name was not specified for child => propagate parent activity
      CURRENT_SPAN.setVolatile(this, parentSpan)
      return
    }

    val startTimeUnixNanoDiff = StartUpMeasurer.getStartTimeUnixNanoDiff()
    val start = startTimeUnixNanoDiff + System.nanoTime()

    // report time between scheduling and start of the execution
    tracer.spanBuilder("$coroutineName: scheduled")
      .setStartTimestamp(startTimeUnixNanoDiff + creationTime, TimeUnit.NANOSECONDS)
      .setParent(parentContext)
      .startSpan()
      .end(start, TimeUnit.NANOSECONDS)
    val span = tracer.spanBuilder(coroutineName)
      .setStartTimestamp(start, TimeUnit.NANOSECONDS)
      .setParent(parentContext)
      .startSpan()

    CURRENT_SPAN.setVolatile(this, span)

    val job = context.job
    job.invokeOnCompletion {
      val end = startTimeUnixNanoDiff + System.nanoTime()
      val lastSuspensionTime = LAST_SUSPENSION_TIME.getVolatile(this) as Long
      if (lastSuspensionTime != -1L) {
        // after the last suspension, the coroutine was waiting for its children => end main activity
        tracer.spanBuilder("$coroutineName: completing")
          .setStartTimestamp(startTimeUnixNanoDiff + lastSuspensionTime, TimeUnit.NANOSECONDS)
          .setParent(parentContext.with(span))
          .startSpan()
          .end(end, TimeUnit.NANOSECONDS)
      }

      // this invokeOnCompletion handler is executed after all children are completed => end 'completing' activity
      span.end(end, TimeUnit.NANOSECONDS)
    }
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: Unit) {
    LAST_SUSPENSION_TIME.setVolatile(this, System.nanoTime())
  }

  override fun copyForChild(): CopyableThreadContextElement<Unit> {
    val span = checkNotNull(CURRENT_SPAN.getVolatile(this)) {
      "updateThreadContext was never called"
    }
    return CoroutineTimeMeasurer(span = null,
                                 parentSpan = span as ReadableSpan,
                                 parentContext = parentContext.with(span as Span),
                                 tracer = tracer)
  }

  override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext = overwritingElement
}