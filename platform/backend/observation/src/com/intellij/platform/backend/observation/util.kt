// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TrackingUtil")
@file:Suppress("RedundantUnitReturnType")

package com.intellij.platform.backend.observation

import com.intellij.concurrency.currentThreadContext
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Starts tracking of all suspending activities that are invoked in [action].
 * The tracking data is processed within [PlatformActivityTrackerService], and is used to await complex configuration activities.
 * @see ActivityKey for high-level explanations
 */
suspend fun <R> Project.trackActivity(marker: ActivityKey, action: suspend () -> R): R {
  return PlatformActivityTrackerService.getInstanceAsync(this).trackConfigurationActivity(marker, action)
}

/**
 * Starts tracking of all asynchronous activities that are invoked in [action].
 * The tracking data is processed within [PlatformActivityTrackerService], and is used to await complex configuration activities.
 * This function is intended to be used in the blocking context,
 * and therefore it tracks all asynchronous activities spawned by means of IntelliJ Platform.
 * Its behavior is similar to [com.intellij.openapi.progress.blockingContextScope].
 *
 * **For usage in non-suspending Kotlin.**
 *
 * **The name means that the function is intended for blocking context, though it in fact does not block by itself.**
 * @see ActivityKey for high-level explanations
 */
@RequiresBlockingContext
fun <R> Project.trackActivityBlocking(marker: ActivityKey, action: () -> R): R {
  return PlatformActivityTrackerService.getInstance(this).trackConfigurationActivityBlocking(marker, action)
}

/**
 * The same as [trackActivityBlocking], but **for usage in Java**.
 * It still has a receiver parameter to avoid polluting the global namespace of Kotlin functions.
 * In Java, the receiver parameter will anyway be desugared to the first formal parameter.
 * @see ActivityKey for high-level explanations
 */
@RequiresBlockingContext
fun Project.trackActivity(key: ActivityKey, action: Runnable): Unit {
  return PlatformActivityTrackerService.getInstance(this).trackConfigurationActivityBlocking(key, action::run)
}

/**
 * Allows launching a computation on a separate coroutine scope that is still covered by the activity key.
 */
@RequiresBlockingContext
fun CoroutineScope.launchTracked(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> Unit) {
  val tracker = currentThreadContext()[PlatformActivityTrackerService.ObservationTracker]
  // since the `launch` is executed with the Job of `this`, we need to mimic the awaiting for the execution of `block` for `ObservationTracker`
  val childJob = Job(tracker?.currentJob)
  launch(context + (tracker ?: EmptyCoroutineContext), CoroutineStart.DEFAULT) {
    try {
      block()
    }
    finally {
      childJob.complete()
    }
  }
}

internal val EP_NAME: ExtensionPointName<ActivityTracker> = ExtensionPointName("com.intellij.activityTracker")