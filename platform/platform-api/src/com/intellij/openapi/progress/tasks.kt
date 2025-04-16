// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental

package com.intellij.openapi.progress

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.*
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.util.progress.withRawProgressReporter
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental

@Deprecated(
  "Moved to com.intellij.platform.ide.progress",
  ReplaceWith(
    "withBackgroundProgress(project, title, action)",
    "com.intellij.platform.ide.progress.withBackgroundProgress",
  ),
  level = DeprecationLevel.ERROR,
)
suspend fun <T> withBackgroundProgress(
  project: Project,
  title: @ProgressTitle String,
  action: suspend CoroutineScope.() -> T
): T {
  return withBackgroundProgress(project, title, action)
}

@Deprecated(
  "Moved to com.intellij.platform.ide.progress",
  ReplaceWith(
    "withBackgroundProgress(project, title, cancellable, action)",
    "com.intellij.platform.ide.progress.withBackgroundProgress",
  ),
  level = DeprecationLevel.ERROR,
)
suspend fun <T> withBackgroundProgress(
  project: Project,
  title: @ProgressTitle String,
  cancellable: Boolean,
  action: suspend CoroutineScope.() -> T
): T {
  return withBackgroundProgress(project, title, cancellable, action)
}

@Deprecated(
  "Moved to com.intellij.platform.ide.progress",
  ReplaceWith(
    "withBackgroundProgress(project, title, cancellation, action)",
    "com.intellij.platform.ide.progress.withBackgroundProgress",
  ),
  level = DeprecationLevel.ERROR,
)
suspend fun <T> withBackgroundProgress(
  project: Project,
  title: @ProgressTitle String,
  cancellation: TaskCancellation,
  action: suspend CoroutineScope.() -> T
): T {
  return withBackgroundProgress(project, title, cancellation, action)
}

@ApiStatus.ScheduledForRemoval
@Deprecated(
  "Moved to com.intellij.platform.ide.progress",
  ReplaceWith(
    "withModalProgress(project, title, action)",
    "com.intellij.platform.ide.progress.withModalProgress",
  ),
  level = DeprecationLevel.ERROR,
)
suspend fun <T> withModalProgress(
  project: Project,
  title: @ProgressTitle String,
  action: suspend CoroutineScope.() -> T,
): T {
  return withModalProgress(project, title, action)
}

@ApiStatus.ScheduledForRemoval
@Deprecated(
  "Moved to com.intellij.platform.ide.progress",
  ReplaceWith(
    "withModalProgress(owner, title, cancellation, action)",
    "com.intellij.platform.ide.progress.withModalProgress",
  ),
  level = DeprecationLevel.ERROR,
)
suspend fun <T> withModalProgress(
  owner: ModalTaskOwner,
  title: @ProgressTitle String,
  cancellation: TaskCancellation,
  action: suspend CoroutineScope.() -> T,
): T {
  return withModalProgress(owner, title, cancellation, action)
}

@Deprecated(
  "Moved to com.intellij.platform.ide.progress",
  ReplaceWith(
    "runWithModalProgressBlocking(project, title, action)",
    "com.intellij.platform.ide.progress.runWithModalProgressBlocking",
  ),
  level = DeprecationLevel.ERROR,
)
@RequiresBlockingContext
@RequiresEdt
fun <T> runWithModalProgressBlocking(
  project: Project,
  title: @ProgressTitle String,
  action: suspend CoroutineScope.() -> T,
): T {
  return runWithModalProgressBlocking(project, title, action)
}

@Deprecated(
  "Moved to com.intellij.platform.ide.progress",
  ReplaceWith(
    "runWithModalProgressBlocking(owner, title, cancellation, action)",
    "com.intellij.platform.ide.progress.runWithModalProgressBlocking",
  ),
  level = DeprecationLevel.ERROR,
)
@RequiresBlockingContext
@RequiresEdt
fun <T> runWithModalProgressBlocking(
  owner: ModalTaskOwner,
  title: @ModalProgressTitle String,
  cancellation: TaskCancellation = TaskCancellation.cancellable(),
  action: suspend CoroutineScope.() -> T,
): T {
  return runWithModalProgressBlocking(owner, title, cancellation, action)
}

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

@ApiStatus.ScheduledForRemoval
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
  return withBackgroundProgress(project, title, cancellation) {
    withRawProgressReporter(action)
  }
}

@ApiStatus.ScheduledForRemoval
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
  return withModalProgress(owner = ModalTaskOwner.project(project), title = title, cancellation = TaskCancellation.cancellable()) {
    withRawProgressReporter(action = action)
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
  title: @ModalProgressTitle String,
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
  title: @ModalProgressTitle String,
  cancellation: TaskCancellation = TaskCancellation.cancellable(),
  action: suspend CoroutineScope.() -> T,
): T {
  return runWithModalProgressBlocking(owner, title, cancellation) {
    withRawProgressReporter(action)
  }
}

@ApiStatus.ScheduledForRemoval
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
  title: @ModalProgressTitle String,
  action: suspend CoroutineScope.() -> T,
): T {
  return runWithModalProgressBlocking(ModalTaskOwner.project(project), title, TaskCancellation.cancellable(), action)
}

@ApiStatus.ScheduledForRemoval
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
  title: @ModalProgressTitle String,
  cancellation: TaskCancellation = TaskCancellation.cancellable(),
  action: suspend CoroutineScope.() -> T,
): T {
  return runWithModalProgressBlocking(owner, title, cancellation, action)
}
