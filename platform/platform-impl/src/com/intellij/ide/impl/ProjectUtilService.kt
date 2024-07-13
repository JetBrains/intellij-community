// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt

open class ProjectUtilService(protected val project: Project) {
  @RequiresEdt
  open fun focusProjectWindow(stealFocusIfAppInactive: Boolean = false) {
    ProjectUtil.focusProjectWindow(project, stealFocusIfAppInactive)
  }

  companion object {
    fun getInstance(project: Project): ProjectUtilService = project.service<ProjectUtilService>()
  }
}