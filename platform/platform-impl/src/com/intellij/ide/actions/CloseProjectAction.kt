// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.ui.IdeUICustomization

class CloseProjectAction : CloseProjectsActionBase() {
  init {
    @Suppress("DialogTitleCapitalization")
    templatePresentation.setText { IdeUICustomization.getInstance().projectMessage("action.close.project.text") }
    templatePresentation.setDescription { IdeUICustomization.getInstance().projectMessage("action.close.project.description") }
  }

  override fun canClose(project: Project, currentProject: Project) = project === currentProject

  override fun shouldShow(e: AnActionEvent) = e.project != null

  override fun update(e: AnActionEvent) {
    super.update(e)

    if (ProjectAttachProcessor.canAttachToProject() && e.project != null && ModuleManager.getInstance(e.project!!).modules.size > 1) {
      e.presentation.setText(IdeBundle.messagePointer("action.close.projects.in.current.window"))
    }
    else {
      @Suppress("DialogTitleCapitalization")
      e.presentation.text = IdeUICustomization.getInstance().projectMessage("action.close.project.text")
      e.presentation.description = IdeUICustomization.getInstance().projectMessage("action.close.project.description")
    }
  }
}