// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental

package com.intellij.openapi.progress

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Experimental

suspend fun <T> withBackgroundProgressIndicator(
  project: Project,
  title: @ProgressTitle String,
  action: suspend CoroutineScope.() -> T
): T {
  return withBackgroundProgressIndicator(project, title, cancellable = true, action)
}

suspend fun <T> withBackgroundProgressIndicator(
  project: Project,
  title: @ProgressTitle String,
  cancellable: Boolean,
  action: suspend CoroutineScope.() -> T
): T {
  val cancellation = if (cancellable) TaskCancellation.cancellable() else TaskCancellation.nonCancellable()
  return withBackgroundProgressIndicator(project, title, cancellation, action)
}

/**
 * Shows a background progress indicator, and runs the specified [action].
 * The action receives [ProgressSink] in the coroutine context, progress sink updates are reflected in the UI during the action.
 * The indicator is not showed immediately to avoid flickering,
 * i.e. the user won't see anything if the [action] completes within the given timeout.
 *
 * @param project in which frame the progress should be shown
 * @param cancellation controls the UI appearance, e.g. [TaskCancellation.nonCancellable] or [TaskCancellation.cancellable]
 * @throws CancellationException if the calling coroutine was cancelled,
 * or if the indicator was cancelled by the user in the UI
 */
suspend fun <T> withBackgroundProgressIndicator(
  project: Project,
  title: @ProgressTitle String,
  cancellation: TaskCancellation,
  action: suspend CoroutineScope.() -> T
): T {
  val service = ApplicationManager.getApplication().getService(TaskSupport::class.java)
  return service.withBackgroundProgressIndicatorInternal(
    project, title, cancellation, action
  )
}
