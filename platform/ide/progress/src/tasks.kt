// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental

package com.intellij.platform.ide.progress

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.platform.util.progress.reportProgress
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Experimental

suspend fun <T> withBackgroundProgress(
  project: Project,
  title: @ProgressTitle String,
  action: suspend CoroutineScope.() -> T
): T {
  return withBackgroundProgress(project, title, TaskCancellation.cancellable(), action)
}

suspend fun <T> withBackgroundProgress(
  project: Project,
  title: @ProgressTitle String,
  cancellable: Boolean,
  action: suspend CoroutineScope.() -> T
): T {
  val cancellation = if (cancellable) TaskCancellation.cancellable() else TaskCancellation.nonCancellable()
  return withBackgroundProgress(project, title, cancellation, action)
}

/**
 * Shows a background progress indicator, and runs the specified [action].
 * The action receives [a fresh progress step][com.intellij.platform.util.progress.currentProgressStep] in the coroutine context,
 * which can be used via [reportProgress], [reportSequentialProgress], [reportRawProgress].
 * Corresponding reporter updates are reflected in the UI during the execution.
 * The progress is not shown in the UI immediately to avoid flickering,
 * i.e. the user won't see anything if the [action] completes within the given timeout.
 *
 * ### Threading
 *
 * The [action] is run with the calling coroutine dispatcher.
 *
 * @param project in which frame the progress should be shown
 * @param cancellation controls the UI appearance, e.g. [TaskCancellation.nonCancellable] or [TaskCancellation.cancellable]
 * @throws CancellationException if the calling coroutine was cancelled, or if the indicator was cancelled by the user in the UI
 */
suspend fun <T> withBackgroundProgress(
  project: Project,
  title: @ProgressTitle String,
  cancellation: TaskCancellation,
  action: suspend CoroutineScope.() -> T
): T {
  return taskSupport().withBackgroundProgressInternal(project, title, cancellation, action)
}

suspend fun <T> withModalProgress(
  project: Project,
  title: @ProgressTitle String,
  action: suspend CoroutineScope.() -> T,
): T {
  return withModalProgress(ModalTaskOwner.project(project), title, TaskCancellation.cancellable(), action)
}

/**
 * Shows a modal progress indicator, and runs the specified [action].
 * The action receives [a fresh progress step][com.intellij.platform.util.progress.currentProgressStep] in the coroutine context,
 * which can be used via [reportProgress], [reportSequentialProgress], [reportRawProgress].
 * Corresponding reporter updates are reflected in the UI during the execution.
 * The progress dialog is not shown in the UI immediately to avoid flickering,
 * i.e. the user won't see anything if the [action] completes within the given timeout.
 *
 * ### Threading
 *
 * The [action] is run with the calling coroutine dispatcher.
 *
 * Switches to [Dispatchers.EDT][com.intellij.openapi.application.EDT] are allowed inside the action,
 * as they are automatically scheduled with the correct modality, which is the newly entered one.
 *
 * @param owner in which frame the progress should be shown
 * @param cancellation controls the UI appearance, e.g. [TaskCancellation.nonCancellable] or [TaskCancellation.cancellable]
 * @throws CancellationException if the calling coroutine was cancelled,
 * or if the indicator was cancelled by the user in the UI
 */
suspend fun <T> withModalProgress(
  owner: ModalTaskOwner,
  title: @ProgressTitle String,
  cancellation: TaskCancellation,
  action: suspend CoroutineScope.() -> T,
): T {
  return taskSupport().withModalProgressInternal(owner, title, cancellation, action)
}

@RequiresBlockingContext
@RequiresEdt
fun <T> runWithModalProgressBlocking(
  project: Project,
  title: @ProgressTitle String,
  action: suspend CoroutineScope.() -> T,
): T {
  return runWithModalProgressBlocking(ModalTaskOwner.project(project), title, TaskCancellation.cancellable(), action)
}

/**
 * Shows a modal progress indicator, and runs the specified [action].
 * The action receives [a fresh progress step][com.intellij.platform.util.progress.currentProgressStep] in the coroutine context,
 * which can be used via [reportProgress], [reportSequentialProgress], [reportRawProgress].
 * Corresponding reporter updates are reflected in the UI during the execution.
 * The progress dialog is not shown in the UI immediately to avoid flickering,
 * i.e. the user won't see anything if the [action] completes within the given timeout.
 *
 * ### Threading
 *
 * **Important**: the [action] is run with [Dispatchers.Default][kotlinx.coroutines.Dispatchers.Default].
 *
 * Switches to [Dispatchers.EDT][com.intellij.openapi.application.EDT] are allowed inside the action,
 * as they are automatically scheduled with the correct modality, which is the newly entered one.
 *
 * ### Difference with [withModalProgress].
 *
 * This method blocks the caller and doesn't return until the [action] is completed.
 * Runnables, scheduled by various `invokeLater` calls within the given modality, are processed by the nested event loop.
 *
 * Usually the usage look like this:
 * ```
 * // on EDT
 * fun actionPerformed() {
 *
 *   // this will schedule a new coroutine in default dispatcher
 *   myCoroutineScope.launch(Dispatchers.Default) {
 *
 *     // the coroutine will schedule modal action,
 *     // which will enter the modality some time later
 *     withModalProgress(...) {
 *
 *       // continue execution on Dispatchers.Default
 *       ...
 *     }
 *   }
 * }
 * ```
 *
 * [runWithModalProgressBlocking] is designed for cases when the caller requires the modality,
 * and the caller cannot afford to let go of the current EDT event:
 * ```
 * // on EDT
 * fun actionPerformed() {
 *
 *   // will enter the new modality synchronously in the current EDT event
 *   runWithModalProgressBlocking(...) {
 *
 *     // continue execution on Dispatchers.Default
 *     ...
 *   }
 * }
 * ```
 *
 * ### Focus
 *
 * Currently, there is no guarantee that focus requests produced by [action] will be applied properly.
 * It is highly recommended to request input focus after [runWithModalProgressBlocking].
 *
 * @param owner in which frame the progress should be shown
 * @param cancellation controls the UI appearance, e.g. [TaskCancellation.nonCancellable] or [TaskCancellation.cancellable]
 * @throws com.intellij.openapi.progress.ProcessCanceledException if the calling coroutine was cancelled,
 * or if the indicator was cancelled by the user in the UI
 */
@RequiresBlockingContext
@RequiresEdt
fun <T> runWithModalProgressBlocking(
  owner: ModalTaskOwner,
  title: @ProgressTitle String,
  cancellation: TaskCancellation = TaskCancellation.cancellable(),
  action: suspend CoroutineScope.() -> T,
): T {
  return taskSupport().runWithModalProgressBlockingInternal(owner, title, cancellation, action)
}

private fun taskSupport(): TaskSupport = ApplicationManager.getApplication().service()
