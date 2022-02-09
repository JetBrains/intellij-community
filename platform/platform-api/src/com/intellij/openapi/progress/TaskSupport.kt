// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface TaskSupport {

  fun taskCancellationNonCancellableInternal(): TaskCancellation

  fun taskCancellationCancellableInternal(): TaskCancellation.Cancellable

  suspend fun <T> withBackgroundProgressIndicatorInternal(
    project: Project,
    title: @ProgressTitle String,
    cancellation: TaskCancellation,
    action: suspend CoroutineScope.() -> T
  ): T
}
