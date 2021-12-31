// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental

package com.intellij.openapi.progress

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Shows a background progress indicator, and runs the specified [action].
 * The action receives [ProgressSink] in the coroutine context, progress sink updates are reflected in the UI during the action.
 * The indicator is not showed immediately to avoid flickering,
 * i.e. the user won't see anything if the [action] completes within the given timeout.
 *
 * @param project in which frame the progress should be shown
 * @throws CancellationException if the calling coroutine was cancelled
 */
suspend fun <T> withBackgroundProgressIndicator(
  project: Project,
  title: @ProgressTitle String,
  action: suspend CoroutineScope.() -> T
): T {
  val service = ApplicationManager.getApplication().getService(TaskSupport::class.java)
  return service.withBackgroundProgressIndicatorInternal(
    project, title, action
  )
}
