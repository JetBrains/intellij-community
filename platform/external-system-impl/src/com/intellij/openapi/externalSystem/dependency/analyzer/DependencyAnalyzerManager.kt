// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project

class DependencyAnalyzerManager(private val project: Project) {

  private val tabs = HashMap<ProjectSystemId, DependencyAnalyzerEditorTab>()

  fun getOrCreate(systemId: ProjectSystemId): DependencyAnalyzerView {
    val tab = tabs.getOrPut(systemId) { DependencyAnalyzerEditorTab(project, systemId) }
    tab.show()
    return tab.view
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<DependencyAnalyzerManager>()
  }
}