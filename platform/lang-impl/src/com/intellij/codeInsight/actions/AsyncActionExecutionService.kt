// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 *  Uses to schedule execution on after specific action, if action executes asynchronously
 */
@ApiStatus.Internal
@ApiStatus.ScheduledForRemoval
@Deprecated("Use `AnActionListener` instead")
open class AsyncActionExecutionService {
  companion object {
    fun getInstance(project: Project): AsyncActionExecutionService = project.service<AsyncActionExecutionService>()
  }

  open fun withExecutionAfterAction(actionId: String, mainActionCallBlock: () -> Unit, onAfterAsyncActionExecute: () -> Unit) {
    mainActionCallBlock()
    onAfterAsyncActionExecute()
  }
}