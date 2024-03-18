// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)

package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.StartUpMeasurer
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

private val defaultTracer = MeasureCoroutineTime(reporter = DefaultTraceReporter(reportScheduleTimeForRoot = true))

/**
 * Put this into coroutine context to enable [CoroutineName]-based time measuring of this coroutine and its children.
 *
 * Example:
 * ```
 * runBlocking(rootTask()) {
 *   launch(CoroutineName("task 1")) {
 *     ...
 *   }
 *   val stuff = async(CoroutineName("init stuff") {
 *     ...
 *   }
 * }
 * ```
 */
fun rootTask(): CoroutineContext = defaultTracer

internal suspend fun createSpan(traceReporter: TraceReporter): CoroutineContext {
  return CoroutineTimeMeasurer(reporter = traceReporter, parentActivity = coroutineContext[CoroutineTimeMeasurerKey]?.getActivity())
}

internal fun createRootSpan(traceReporter: TraceReporter): CoroutineContext {
  return CoroutineTimeMeasurer(reporter = traceReporter, parentActivity = null)
}

/**
 * This function is designed to be used instead of `withContext(CoroutineName("subtask")) { ... }`.
 * See https://github.com/Kotlin/kotlinx.coroutines/issues/3414
 */
@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
suspend fun <T> span(name: String, context: CoroutineContext = EmptyCoroutineContext, action: suspend CoroutineScope.() -> T): T {
  val namedContext = context + CoroutineName(name)
  val measurer = coroutineContext[CoroutineTimeMeasurerKey]
  return if (measurer == null) {
    withContext(namedContext, action)
  }
  else {
    withContext(namedContext + measurer.copyForChild(), action)
  }
}

@Internal
suspend fun getTraceActivity(): Activity? = coroutineContext[CoroutineTimeMeasurerKey]?.getActivity()

private val noActivity: Activity = object : Activity {
  override fun getName(): String = error("must not be invoked")

  override fun end(): Unit = error("must not be invoked")

  override fun setDescription(description: String): Unit = error("must not be invoked")

  override fun endAndStart(name: String): Activity = error("must not be invoked")

  override fun startChild(name: String): Activity = error("must not be invoked")
}

private object CoroutineTimeMeasurerKey : CoroutineContext.Key<CoroutineTimeMeasurer>

private class MeasureCoroutineTime(private val reporter: TraceReporter) : CopyableThreadContextElement<Unit> {
  override val key: CoroutineContext.Key<*>
    get() = CoroutineTimeMeasurerKey

  override fun updateThreadContext(context: CoroutineContext) = error("must not be invoked")

  override fun restoreThreadContext(context: CoroutineContext, oldState: Unit) = error("must not be invoked")

  override fun copyForChild(): CopyableThreadContextElement<Unit> = CoroutineTimeMeasurer(parentActivity = null, reporter = reporter)

  override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext = error("must not be invoked")

  override fun toString(): String = "MeasureCoroutineTime(reporter=$reporter)"
}

private class CoroutineTimeMeasurer(private val parentActivity: ActivityImpl?,
                                    private val reporter: TraceReporter) : CopyableThreadContextElement<Unit> {
  companion object {
    private val CURRENT_ACTIVITY: VarHandle
    private val LAST_SUSPENSION_TIME: VarHandle

    init {
      val lookup = MethodHandles.privateLookupIn(CoroutineTimeMeasurer::class.java, MethodHandles.lookup())
      CURRENT_ACTIVITY = lookup.findVarHandle(CoroutineTimeMeasurer::class.java, "currentActivity", Activity::class.java)
      LAST_SUSPENSION_TIME = lookup.findVarHandle(CoroutineTimeMeasurer::class.java, "lastSuspensionTime", Long::class.javaPrimitiveType)
    }
  }

  override val key: CoroutineContext.Key<*>
    get() = CoroutineTimeMeasurerKey

  private val creationTime: Long = System.nanoTime()

  @Suppress("unused")
  private var currentActivity: Activity? = null

  @Suppress("unused")
  private var lastSuspensionTime: Long = -1

  override fun toString(): String {
    val activity = CURRENT_ACTIVITY.getVolatile(this) as Activity?
    return when {
      activity === noActivity -> "no parent and no name"
      activity != null -> activity.name
      parentActivity != null -> "uninitialized child of ${parentActivity.name}"
      else -> "uninitialized"
    }
  }

  override fun updateThreadContext(context: CoroutineContext) {
    if (CURRENT_ACTIVITY.getVolatile(this) != null) {
      // already initialized
      return
    }

    val coroutineName = context[CoroutineName]?.name
    if (parentActivity != null) {
      if (coroutineName == null || parentActivity.name == coroutineName) {
        // name was not specified for child => propagate parent activity
        CURRENT_ACTIVITY.setVolatile(this, parentActivity)
        return
      }
    }
    if (coroutineName == null) {
      // no parent and no name => don't report
      CURRENT_ACTIVITY.setVolatile(this, noActivity)
      return
    }

    // report time between scheduling and start of the execution
    val activity = reporter.start(coroutineName = coroutineName, scheduleTime = creationTime, parentActivity = parentActivity)
    CURRENT_ACTIVITY.setVolatile(this, activity)

    val job = context.job
    job.invokeOnCompletion {
      val end = System.nanoTime()
      val lastSuspensionTime = LAST_SUSPENSION_TIME.getVolatile(this) as Long
      // this invokeOnCompletion handler is executed after all children are completed => end 'completing' activity
      reporter.end(activity = activity, coroutineName = coroutineName, end = end, lastSuspensionTime = lastSuspensionTime)
    }
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: Unit) {
    LAST_SUSPENSION_TIME.setVolatile(this, System.nanoTime())
  }

  override fun copyForChild(): CopyableThreadContextElement<Unit> {
    val activity = CURRENT_ACTIVITY.getVolatile(this)
    // don't format into one line - complicates debugging
    if (activity === noActivity || activity == null) {
      return CoroutineTimeMeasurer(parentActivity = null, reporter = reporter)
    }
    else {
      return CoroutineTimeMeasurer(parentActivity = activity as ActivityImpl, reporter = reporter)
    }
  }

  override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext = overwritingElement

  fun getActivity() = CURRENT_ACTIVITY.getVolatile(this) as ActivityImpl?
}

open class DefaultTraceReporter(private val reportScheduleTimeForRoot: Boolean) : TraceReporter {
  override fun start(coroutineName: String, scheduleTime: Long, parentActivity: ActivityImpl?): ActivityImpl {
    val start = System.nanoTime()

    if (reportScheduleTimeForRoot || parentActivity != null) {
      // the main activity cannot be a parent for meta "scheduled" activity since it is outside it (preceding the main activity)
      setEndAndAdd(ActivityImpl("$coroutineName: scheduled", scheduleTime, parentActivity), start)
    }

    // start the main activity right away
    return ActivityImpl(coroutineName, /* start = */start, parentActivity)
  }

  override fun end(activity: ActivityImpl, coroutineName: String, end: Long, lastSuspensionTime: Long) {
    if (lastSuspensionTime != -1L) {
      // after the last suspension, the coroutine was waiting for its children => end main activity
      setEndAndAdd(ActivityImpl("$coroutineName: completing", lastSuspensionTime, activity), end)
    }
    setEndAndAdd(activity, end)
  }

  open fun setEndAndAdd(activity: ActivityImpl, end: Long) {
    activity.setEnd(end)
    StartUpMeasurer.addActivity(activity)
  }
}