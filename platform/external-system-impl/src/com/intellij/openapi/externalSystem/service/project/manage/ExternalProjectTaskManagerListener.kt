// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskManager
import com.intellij.task.impl.ProjectTaskManagerListenerExtensionPoint

private class ExternalProjectTaskManagerListener : ProjectTaskManagerListenerExtensionPoint {
  override fun beforeRun(project: Project, context: ProjectTaskContext) {
    ExternalProjectsManagerImpl.getInstance(project).projectTasksBeforeRun(context)
  }

  override fun afterRun(project: Project, result: ProjectTaskManager.Result) {
    ExternalProjectsManagerImpl.getInstance(project).projectTasksAfterRun(result)
  }
}