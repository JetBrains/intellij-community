// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectInitialActivitiesNotifier
import com.intellij.openapi.project.ProjectInitialActivitiesNotifierImpl
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector
import com.intellij.openapi.vfs.newvfs.refreshVFSAsync
import com.intellij.util.SystemProperties
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

internal fun scheduleInitialVfsRefresh(project: Project, log: Logger) {
  if (skipInitialRefresh()) {
    notifyVfsRefreshIsFinished(project)
    return
  }

  val projectId: String = project.locationHash
  log.info("$projectId: marking roots for initial VFS refresh")
  ProjectRootManagerEx.getInstanceEx(project).markRootsForRefresh()
  log.info("$projectId: starting initial VFS refresh")
  val app = ApplicationManager.getApplication()
  val t = System.nanoTime()
  if (!app.isCommandLine || CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode()) {
    @Suppress("DEPRECATION")
    project.coroutineScope.launch {
      refreshVFSAsync()
      timeInitialVfsRefresh(t, project, log)
      notifyVfsRefreshIsFinished(project)
    }
  }
  else {
    ApplicationManager.getApplication().invokeAndWait {
      VirtualFileManager.getInstance().syncRefresh()
      timeInitialVfsRefresh(t, project, log)
      notifyVfsRefreshIsFinished(project)
    }
  }
}

private fun notifyVfsRefreshIsFinished(project: Project) {
  (project.service<ProjectInitialActivitiesNotifier>() as ProjectInitialActivitiesNotifierImpl).notifyInitialVfsRefreshFinished()
}

private fun timeInitialVfsRefresh(t: Long, project: Project, log: Logger) {
  val duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t)
  log.info(project.locationHash + ": initial VFS refresh finished " + duration + " ms")
  VfsUsageCollector.logInitialRefresh(project, duration)
}

private fun skipInitialRefresh(): Boolean {
  return SystemProperties.getBooleanProperty("ij.indexes.skip.initial.refresh", false) ||
         ApplicationManager.getApplication().isUnitTestMode
}
