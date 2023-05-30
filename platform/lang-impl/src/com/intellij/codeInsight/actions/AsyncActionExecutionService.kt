package com.intellij.codeInsight.actions

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 *  Uses to schedule execution on after specific action, if action executes asynchronously
 */
open class AsyncActionExecutionService {

  companion object {
    fun getInstance(project: Project): AsyncActionExecutionService = project.service<AsyncActionExecutionService>()
  }


  open fun withExecutionAfterAction(actionId: String, mainActionCallBlock: () -> Unit, onAfterAsyncActionExecute: () -> Unit) {
    mainActionCallBlock()
    onAfterAsyncActionExecute()
  }
}