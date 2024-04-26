// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.lightEdit.LightEditServiceListener
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.startup.ProjectActivity

class WindowDressing : ProjectCloseListener, LightEditServiceListener {
  companion object {
    fun getWindowActionGroup(): ProjectWindowActionGroup {
      return ActionManager.getInstance().getAction("OpenProjectWindows") as ProjectWindowActionGroup
    }
  }

  override fun projectClosed(project: Project) {
    getWindowActionGroup().removeProject(project)
  }

  override fun lightEditWindowOpened(project: Project) {
    getWindowActionGroup().addProject(project)
  }

  override fun lightEditWindowClosed(project: Project) {
    getWindowActionGroup().removeProject(project)
  }
}

private class WindowDressingStartupActivity : ProjectActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    (serviceAsync<ActionManager>().getAction("OpenProjectWindows") as ProjectWindowActionGroup).addProject(project)
  }
}