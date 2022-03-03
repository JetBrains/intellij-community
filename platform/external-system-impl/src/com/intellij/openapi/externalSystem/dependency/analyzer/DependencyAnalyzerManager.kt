// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

class DependencyAnalyzerManager(private val project: Project) {

  private val tabs = HashMap<ProjectSystemId, DependencyAnalyzerEditorTab>()

  fun getOrCreate(systemId: ProjectSystemId): DependencyAnalyzerView {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return getOrCreateEditorTab(systemId)
      .also(DependencyAnalyzerEditorTab::show)
      .let(DependencyAnalyzerEditorTab::view)
  }

  private fun getOrCreateEditorTab(systemId: ProjectSystemId): DependencyAnalyzerEditorTab {
    if (systemId !in tabs) {
      val tab = DependencyAnalyzerEditorTab(project, systemId)
      tabs[systemId] = tab
      Disposer.register(tab, Disposable { tabs.remove(systemId) })
    }
    return tabs.getValue(systemId)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<DependencyAnalyzerManager>()
  }
}