// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.lightEdit.LightEditServiceListener
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.startup.ProjectPostStartupActivity
import kotlinx.coroutines.coroutineScope

class WindowDressing : ProjectCloseListener, LightEditServiceListener {
  companion object {
    @JvmStatic
    val windowActionGroup: ProjectWindowActionGroup
      get() = ActionManager.getInstance().getAction("OpenProjectWindows") as ProjectWindowActionGroup
  }

  private class MyStartupActivity : ProjectPostStartupActivity {
    override suspend fun execute(project: Project) {
      coroutineScope {
        ActionManagerEx.withLazyActionManager(scope = this) {
          (it.getAction("OpenProjectWindows") as ProjectWindowActionGroup).addProject(project)
        }
      }
    }
  }

  override fun projectClosed(project: Project) {
    windowActionGroup.removeProject(project)
  }

  override fun lightEditWindowOpened(project: Project) {
    windowActionGroup.addProject(project)
  }

  override fun lightEditWindowClosed(project: Project) {
    windowActionGroup.removeProject(project)
  }
}