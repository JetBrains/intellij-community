// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import com.intellij.configurationStore.StoreUtil
import com.intellij.configurationStore.saveSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

fun Project.use(save: Boolean = false, action: (Project) -> Unit) {
  if (ApplicationManager.getApplication().isDispatchThread) {
    try {
      action(this)
    }
    finally {
      forceCloseProject(save)
    }
  }
  else {
    val project = this
    try {
      action(project)
    }
    finally {
      val projectManager = ProjectManagerEx.getInstanceEx()
      runBlocking {
        if (save) {
          saveSettings(project, forceSavingAllSettings = true)
        }
        withContext(Dispatchers.EDT) {
          projectManager.forceCloseProject(project)
        }
      }
    }
  }
}

fun Project.forceCloseProject(save: Boolean = false) {
  runInEdtAndWait {
    val projectManager = ProjectManagerEx.getInstanceEx()
    if (save) {
      StoreUtil.saveSettings(this, forceSavingAllSettings = true)
    }
    projectManager.forceCloseProject(this)
  }
}
