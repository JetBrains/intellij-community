// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental

package com.intellij.openapi.progress

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ProgressTitle
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
 * The action receives [ProgressReporter] in the coroutine context, reporter updates are reflected in the UI during the execution.
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
 * The action receives [ProgressReporter] in the coroutine context, reporter updates are reflected in the UI during the execution.
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
 * The action receives [ProgressReporter] in the coroutine context, reporter updates are reflected in the UI during the execution.
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
 * @throws ProcessCanceledException if the calling coroutine was cancelled,
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

//<editor-fold desc="Deprecated stuff">
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
  @Suppress("DEPRECATION")
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
  @Suppress("DEPRECATION")
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
  @Suppress("DEPRECATION")
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
            "Migrate to `ProgressReporter` via `runWithModalProgressBlocking`, " +
            "and use `withRawProgressReporter` to switch to raw reporter only if needed.",
  replaceWith = ReplaceWith("runWithModalProgressBlocking(project, title) { withRawProgressReporter(action) }"),
)
@RequiresBlockingContext
@RequiresEdt
fun <T> runBlockingModalWithRawProgressReporter(
  project: Project,
  title: @ProgressTitle String,
  action: suspend CoroutineScope.() -> T,
): T {
  @Suppress("DEPRECATION")
  return runBlockingModalWithRawProgressReporter(ModalTaskOwner.project(project), title, TaskCancellation.cancellable(), action)
}

@Deprecated(
  message = "This function installs `RawProgressReporter` into action context. " +
            "Migrate to `ProgressReporter` via `runWithModalProgressBlocking`, " +
            "and use `withRawProgressReporter` to switch to raw reporter only if needed.",
  replaceWith = ReplaceWith("runWithModalProgressBlocking(owner, title, cancellation) { withRawProgressReporter(action) }"),
)
@RequiresBlockingContext
@RequiresEdt
fun <T> runBlockingModalWithRawProgressReporter(
  owner: ModalTaskOwner,
  title: @ProgressTitle String,
  cancellation: TaskCancellation = TaskCancellation.cancellable(),
  action: suspend CoroutineScope.() -> T,
): T {
  return taskSupport().runWithModalProgressBlockingInternal(owner, title, cancellation) {
    withRawProgressReporter(action)
  }
}

@Deprecated(
  message = "Function was renamed to `runWithModalProgressBlocking`",
  replaceWith = ReplaceWith(
    "runWithModalProgressBlocking(project, title, action)",
    "com.intellij.openapi.progress.runWithModalProgressBlocking",
  ),
)
@RequiresBlockingContext
@RequiresEdt
fun <T> runBlockingModal(
  project: Project,
  title: @ProgressTitle String,
  action: suspend CoroutineScope.() -> T,
): T {
  return runWithModalProgressBlocking(ModalTaskOwner.project(project), title, TaskCancellation.cancellable(), action)
}

@Deprecated(
  message = "Function was renamed to `runWithModalProgressBlocking`",
  replaceWith = ReplaceWith(
    "runWithModalProgressBlocking(owner, title, cancellation, action)",
    "com.intellij.openapi.progress.runWithModalProgressBlocking",
  ),
)
@RequiresBlockingContext
@RequiresEdt
fun <T> runBlockingModal(
  owner: ModalTaskOwner,
  title: @ProgressTitle String,
  cancellation: TaskCancellation = TaskCancellation.cancellable(),
  action: suspend CoroutineScope.() -> T,
): T {
  return runWithModalProgressBlocking(owner, title, cancellation, action)
}
//</editor-fold>
