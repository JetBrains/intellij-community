// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.autoimport

import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware
import com.intellij.openapi.externalSystem.autoimport.*
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class ProjectAware(
  private val project: Project,
  override val projectId: ExternalSystemProjectId,
  private val autoImportAware: ExternalSystemAutoImportAware
) : ExternalSystemProjectAware {

  private val systemId = projectId.systemId
  private val projectPath = projectId.externalProjectPath

  override val settingsFiles: Set<String>
    get() {
      val pathMacroManager = PathMacroManager.getInstance(project)
      return externalProjectFiles.map {
        val path = FileUtil.toCanonicalPath(it.path)
        // The path string can be changed after serialization and deserialization inside persistent component state.
        // To avoid that we resolve the path using IDE path macros configuration.
        val collapsedPath = pathMacroManager.collapsePath(path)
        val expandedPath = pathMacroManager.expandPath(collapsedPath)
        expandedPath
      }.toSet()
    }

  private val externalProjectFiles: List<File>
    get() = autoImportAware.getAffectedExternalProjectFiles(projectPath, project)

  override fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
    val progressManager = ExternalSystemProgressNotificationManager.getInstance()
    progressManager.addNotificationListener(TaskNotificationListener(listener), parentDisposable)
  }

  override fun reloadProject(context: ExternalSystemProjectReloadContext) {
    val importSpec = ImportSpecBuilder(project, systemId)
    if (!context.isExplicitReload) {
      importSpec.dontReportRefreshErrors()
      importSpec.dontNavigateToError()
    }
    if (!project.isTrusted()) {
      importSpec.usePreviewMode()
    }
    ExternalSystemUtil.refreshProject(projectPath, importSpec)
  }

  private inner class TaskNotificationListener(
    val delegate: ExternalSystemProjectListener
  ) : ExternalSystemTaskNotificationListenerAdapter() {
    var externalSystemTaskId = AtomicReference<ExternalSystemTaskId?>(null)

    override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
      if (id.type != RESOLVE_PROJECT) return
      if (!FileUtil.pathsEqual(workingDir, projectPath)) return

      val task = ApplicationManager.getApplication().getService(ExternalSystemProcessingManager::class.java).findTask(id)
      if (task is ExternalSystemResolveProjectTask) {
        if (!autoImportAware.isApplicable(task.resolverPolicy)) {
          return
        }
      }
      externalSystemTaskId.set(id)
      delegate.onProjectReloadStart()
    }

    private fun afterProjectRefresh(id: ExternalSystemTaskId, status: ExternalSystemRefreshStatus) {
      if (id.type != RESOLVE_PROJECT) return
      if (!externalSystemTaskId.compareAndSet(id, null)) return
      delegate.onProjectReloadFinish(status)
    }

    override fun onSuccess(id: ExternalSystemTaskId) {
      afterProjectRefresh(id, ExternalSystemRefreshStatus.SUCCESS)
    }

    override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
      afterProjectRefresh(id, ExternalSystemRefreshStatus.FAILURE)
    }

    override fun onCancel(id: ExternalSystemTaskId) {
      afterProjectRefresh(id, ExternalSystemRefreshStatus.CANCEL)
    }
  }
}