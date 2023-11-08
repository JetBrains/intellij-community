// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface TaskSupport {

  suspend fun <T> withBackgroundProgressInternal(
    project: Project,
    title: @ProgressTitle String,
    cancellation: TaskCancellation,
    action: suspend CoroutineScope.() -> T
  ): T

  suspend fun <T> withModalProgressInternal(
    owner: ModalTaskOwner,
    title: @ProgressTitle String,
    cancellation: TaskCancellation,
    action: suspend CoroutineScope.() -> T,
  ): T

  fun <T> runWithModalProgressBlockingInternal(
    owner: ModalTaskOwner,
    title: @ProgressTitle String,
    cancellation: TaskCancellation,
    action: suspend CoroutineScope.() -> T,
  ): T
}
