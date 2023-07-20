// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.actionSystem

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class AnAsyncAction : AnAction() {
  @Service
  private class AnAsyncActionAppService(val coroutineScope: CoroutineScope)

  @Service(Service.Level.PROJECT)
  private class AnAsyncActionProjectService(val coroutineScope: CoroutineScope)

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    val cs = project?.getService(AnAsyncActionProjectService::class.java)?.coroutineScope
             ?: ApplicationManager.getApplication().getService(AnAsyncActionAppService::class.java).coroutineScope
    cs.launch { actionPerformedAsync(e) }
  }

  abstract suspend fun actionPerformedAsync(e: AnActionEvent)
}