// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.issue.quickfix

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
class ReimportQuickFix(private val myProjectPath: String, private val systemId: ProjectSystemId) : BuildIssueQuickFix {
  override val id: String = "reimport"
  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> = requestImport(
    project, myProjectPath, systemId)

  companion object {
    fun requestImport(project: Project, projectPath: String, systemId: ProjectSystemId): CompletableFuture<Nothing> {
      val future = CompletableFuture<Nothing>()
      ExternalSystemUtil.refreshProject(projectPath, ImportSpecBuilder(project, systemId)
        .callback(object : ExternalProjectRefreshCallback {
          override fun onSuccess(externalProject: DataNode<ProjectData>?) {
            if (externalProject != null) {
              try {
                ProjectDataManager.getInstance().importData(externalProject, project  /* synchronous */)
              }
              catch (pce: ProcessCanceledException) {
                future.completeExceptionally(CancellationException())
              }
              catch (e: Exception) {
                future.completeExceptionally(e)
              }
            }
            future.complete(null)
          }

          override fun onFailure(errorMessage: String, errorDetails: String?) {
            future.completeExceptionally(RuntimeException(errorMessage))
          }
        })
      )
      return future
    }
  }
}
