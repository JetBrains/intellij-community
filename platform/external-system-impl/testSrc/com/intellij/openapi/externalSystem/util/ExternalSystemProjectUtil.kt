// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import com.intellij.configurationStore.saveSettings
import com.intellij.ide.impl.runBlockingUnderModalProgress
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import kotlinx.coroutines.runBlocking

fun Project.use(save: Boolean = false, action: (Project) -> Unit) {
  val projectManager = ProjectManagerEx.getInstanceEx()
  if (ApplicationManager.getApplication().isDispatchThread) {
    try {
      action(this)
    }
    finally {
      if (save) {
        runBlockingUnderModalProgress {
          saveSettings(this, forceSavingAllSettings = true)
        }
      }
      projectManager.forceCloseProject(this)
    }
  }
  else {
    val project = this
    try {
      action(project)
    }
    finally {
      runBlocking {
        if (save) {
          saveSettings(project, forceSavingAllSettings = true)
        }
        projectManager.forceCloseProjectAsync(project)
      }
    }
  }
}