// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TrackingUtil")
@file:Suppress("RedundantUnitReturnType")

package com.intellij.platform.backend.observation

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBlockingContext

/**
 * Starts tracking of all suspending activities that are invoked in [action].
 * The tracking data is processed within [ActivityInProgressService], and is used to await complex configuration activities.
 * @see ActivityKey for high-level explanations
 */
suspend fun <R> Project.trackActivity(marker: ActivityKey, action: suspend () -> R): R {
  return ActivityInProgressService.getInstanceAsync(this).trackConfigurationActivity(marker, action)
}

/**
 * Starts tracking of all asynchronous activities that are invoked in [action].
 * The tracking data is processed within [ActivityInProgressService], and is used to await complex configuration activities.
 * This function is intended to be used in the blocking context,
 * and therefore it tracks all asynchronous activities spawned by the means of IntelliJ Platform.
 * Its behavior is similar to [com.intellij.openapi.progress.blockingContextScope].
 *
 * **For usage in non-suspending Kotlin.**
 * @see ActivityKey for high-level explanations
 */
@RequiresBlockingContext
fun Project.trackActivityBlocking(marker: ActivityKey, action: () -> Unit): Unit {
  return ActivityInProgressService.getInstance(this).trackConfigurationActivityBlocking(marker, action)
}

/**
 * The same as [trackActivityBlocking], but **for usage in java**.
 * It still has a receiver argument to avoid polluting the global namespace of kotlin functions.
 * In java, the receiver parameter will anyway desugar to the first formal parameter.
 * @see ActivityKey for high-level explanations
 */
@RequiresBlockingContext
fun Project.trackActivity(key: ActivityKey, action: Runnable): Unit {
  return ActivityInProgressService.getInstance(this).trackConfigurationActivityBlocking(key, action::run)
}