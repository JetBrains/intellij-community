// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class DependencyAnalyzerManager(private val project: Project) {

  private val files = HashMap<ProjectSystemId, DependencyAnalyzerVirtualFile>()

  fun getOrCreate(systemId: ProjectSystemId): DependencyAnalyzerView {
    val fileEditorManager = FileEditorManager.getInstance(project)
    val file = files.getOrPut(systemId) {
      DependencyAnalyzerVirtualFile(project, systemId).also { file ->
        DependencyAnalyzerExtension.createExtensionDisposable(systemId, project).also { extensionDisposable ->
          extensionDisposable.whenDisposed {
            fileEditorManager.closeFile(file)
            files.remove(systemId)
          }
        }
      }
    }
    fileEditorManager.openFile(file, true, true)
    return requireNotNull(file.getViews().firstOrNull()) {
      "DependencyAnalyzerView should be created during file open"
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<DependencyAnalyzerManager>()
  }
}