// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.IncompleteDependenciesService
import com.intellij.openapi.project.IncompleteDependenciesService.IncompleteDependenciesAccessToken
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.util.concurrent.Callable

private val INCOMPLETE_MODE_ACCESS_TOKEN = Key.create<IncompleteDependenciesAccessToken>("ToggleIncompleteDependenciesModeAction")

private fun isToggledIncomplete(project: Project): Boolean {
  return project.getUserData(INCOMPLETE_MODE_ACCESS_TOKEN) != null
}

internal class ToggleIncompleteDependenciesModeAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project == null) return

    WriteAction.run<Throwable> {
      val existingToken = project.getUserData(INCOMPLETE_MODE_ACCESS_TOKEN)
      if (existingToken != null) {
        existingToken.finish()
        project.putUserData(INCOMPLETE_MODE_ACCESS_TOKEN, null)
      }
      else {
        val token = project.getService(IncompleteDependenciesService::class.java).enterIncompleteState(this@ToggleIncompleteDependenciesModeAction)
        project.putUserData(INCOMPLETE_MODE_ACCESS_TOKEN, token)
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabled = false
      return
    }
    val isIncomplete = ReadAction.nonBlocking(Callable {
      !project.getService(IncompleteDependenciesService::class.java).getState().isComplete
    }).executeSynchronously()

    e.presentation.isEnabled = !isIncomplete || isToggledIncomplete(project)
    e.presentation.text = if (isIncomplete) "Exit Incomplete Dependencies Mode"
    else ActionsBundle.message("action.ToggleIncompleteMode.text")
  }
}
