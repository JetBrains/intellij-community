// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental

package com.intellij.openapi.progress

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ProgressTitle
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
 * The action receives [ProgressReporter] in the coroutine context, reporter updates are reflected in the UI during the execution.
 * The progress is not shown in the UI immediately to avoid flickering,
 * i.e. the user won't see anything if the [action] completes within the given timeout.
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
 * The action receives [ProgressReporter] in the coroutine context, reporter updates are reflected in the UI during the execution.
 * The progress dialog is not shown in the UI immediately to avoid flickering,
 * i.e. the user won't see anything if the [action] completes within the given timeout.
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

@RequiresEdt
fun <T> runBlockingModal(
  project: Project,
  title: @ProgressTitle String,
  action: suspend CoroutineScope.() -> T,
): T {
  return runBlockingModal(ModalTaskOwner.project(project), title, TaskCancellation.cancellable(), action)
}

/**
 * Shows a modal progress indicator, and runs the specified [action].
 * The action receives [ProgressReporter] in the coroutine context, reporter updates are reflected in the UI during the execution.
 * The progress dialog is not shown in the UI immediately to avoid flickering,
 * i.e. the user won't see anything if the [action] completes within the given timeout.
 *
 * Switches to [Dispatchers.EDT][com.intellij.openapi.application.EDT] are allowed inside the action,
 * as they are automatically scheduled with the correct modality, which is the newly entered one.
 *
 * ### Difference with [withModalProgressIndicator].
 *
 * This method blocks the called and doesn't return until the [action] is completed.
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
 *     withModalProgressIndicator(...) {
 *
 *       // continue execution on Dispatchers.Default
 *       ...
 *     }
 *   }
 * }
 * ```
 *
 * [runBlockingModal] is designed for cases when the caller requires the modality,
 * and the caller cannot afford to let go of the current EDT event:
 * ```
 * // on EDT
 * fun actionPerformed() {
 *
 *   // will enter the new modality synchronously in the current EDT event
 *   runBlockingModal(...) {
 *
 *     // continue execution on Dispatchers.Default
 *     ...
 *   }
 * }
 * ```
 *
 * @param owner in which frame the progress should be shown
 * @param cancellation controls the UI appearance, e.g. [TaskCancellation.nonCancellable] or [TaskCancellation.cancellable]
 * @throws CancellationException if the calling coroutine was cancelled,
 * or if the indicator was cancelled by the user in the UI
 */
@RequiresEdt
fun <T> runBlockingModal(
  owner: ModalTaskOwner,
  title: @ProgressTitle String,
  cancellation: TaskCancellation = TaskCancellation.cancellable(),
  action: suspend CoroutineScope.() -> T,
): T {
  return taskSupport().runBlockingModalInternal(owner, title, cancellation, action)
}

private fun taskSupport(): TaskSupport = ApplicationManager.getApplication().service()

@Deprecated(
  message = "This function installs `RawProgressReporter` into action context. " +
            "Migrate to `ProgressReporter` via `withBackgroundProgress`, " +
            "and use `withRawProgressReporter` to switch to raw reporter only if needed.",
  replaceWith = ReplaceWith("withBackgroundProgress(project, title) { withRawProgressReporter (action) }"),
)
suspend fun <T> withBackgroundProgressIndicator(
  project: Project,
  title: @ProgressTitle String,
  action: suspend CoroutineScope.() -> T
): T {
  return withBackgroundProgressIndicator(project, title, cancellable = true, action)
}

@Deprecated(
  message = "This function installs `RawProgressReporter` into action context. " +
            "Migrate to `ProgressReporter` via `withBackgroundProgress`, " +
            "and use `withRawProgressReporter` to switch to raw reporter only if needed.",
  replaceWith = ReplaceWith("withBackgroundProgress(project, title, cancellable) { withRawProgressReporter (action) }"),
)
suspend fun <T> withBackgroundProgressIndicator(
  project: Project,
  title: @ProgressTitle String,
  cancellable: Boolean,
  action: suspend CoroutineScope.() -> T
): T {
  val cancellation = if (cancellable) TaskCancellation.cancellable() else TaskCancellation.nonCancellable()
  return withBackgroundProgressIndicator(project, title, cancellation, action)
}

@Deprecated(
  message = "This function installs `RawProgressReporter` into action context. " +
            "Migrate to `ProgressReporter` via `withBackgroundProgress`, " +
            "and use `withRawProgressReporter` to switch to raw reporter only if needed.",
  replaceWith = ReplaceWith("withBackgroundProgress(project, title, cancellation) { withRawProgressReporter (action) }"),
)
suspend fun <T> withBackgroundProgressIndicator(
  project: Project,
  title: @ProgressTitle String,
  cancellation: TaskCancellation = TaskCancellation.cancellable(),
  action: suspend CoroutineScope.() -> T
): T {
  return taskSupport().withBackgroundProgressInternal(project, title, cancellation) {
    withRawProgressReporter(action)
  }
}

@Deprecated(
  message = "This function installs `RawProgressReporter` into action context. " +
            "Migrate to `ProgressReporter` via `withModalProgress`, " +
            "and use `withRawProgressReporter` to switch to raw reporter only if needed.",
  replaceWith = ReplaceWith("withModalProgress(project, title) { withRawProgressReporter(action) }"),
)
suspend fun <T> withModalProgressIndicator(
  project: Project,
  title: @ProgressTitle String,
  action: suspend CoroutineScope.() -> T,
): T {
  return withModalProgressIndicator(owner = ModalTaskOwner.project(project), title = title, action = action)
}

@Deprecated(
  message = "This function installs `RawProgressReporter` into action context. " +
            "Migrate to `ProgressReporter` via `withModalProgress`, " +
            "and use `withRawProgressReporter` to switch to raw reporter only if needed.",
  replaceWith = ReplaceWith("withModalProgress(owner, title, cancellation) { withRawProgressReporter(action) }"),
)
suspend fun <T> withModalProgressIndicator(
  owner: ModalTaskOwner,
  title: @ProgressTitle String,
  cancellation: TaskCancellation = TaskCancellation.cancellable(),
  action: suspend CoroutineScope.() -> T,
): T {
  return taskSupport().withModalProgressInternal(owner, title, cancellation) {
    withRawProgressReporter(action)
  }
}

@Deprecated(
  message = "This function installs `RawProgressReporter` into action context. " +
            "Migrate to `ProgressReporter` via `runBlockingModal`, " +
            "and use `withRawProgressReporter` to switch to raw reporter only if needed.",
  replaceWith = ReplaceWith("runBlockingModal(project, title) { withRawProgressReporter(action) }"),
)
@RequiresEdt
fun <T> runBlockingModalWithRawProgressReporter(
  project: Project,
  title: @ProgressTitle String,
  action: suspend CoroutineScope.() -> T,
): T {
  return runBlockingModalWithRawProgressReporter(ModalTaskOwner.project(project), title, TaskCancellation.cancellable(), action)
}

@Deprecated(
  message = "This function installs `RawProgressReporter` into action context. " +
            "Migrate to `ProgressReporter` via `runBlockingModal`, " +
            "and use `withRawProgressReporter` to switch to raw reporter only if needed.",
  replaceWith = ReplaceWith("runBlockingModal(owner, title, cancellation) { withRawProgressReporter(action) }"),
)
@RequiresEdt
fun <T> runBlockingModalWithRawProgressReporter(
  owner: ModalTaskOwner,
  title: @ProgressTitle String,
  cancellation: TaskCancellation = TaskCancellation.cancellable(),
  action: suspend CoroutineScope.() -> T,
): T {
  return taskSupport().runBlockingModalInternal(owner, title, cancellation) {
    withRawProgressReporter(action)
  }
}
