// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.registry.Registry

abstract class AbstractDependencyAnalyzerAction : AnAction(), DumbAware {
  abstract fun getSystemId(e: AnActionEvent): ProjectSystemId?

  abstract fun getExternalProjectPath(e: AnActionEvent): String?

  abstract fun getDependency(e: AnActionEvent): DependencyContributor.Dependency?

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val systemId = getSystemId(e) ?: return
    val externalProjectPath = getExternalProjectPath(e)
    val dependency = getDependency(e)
    val tab = DependencyAnalyzerEditorTab(project, systemId)
    if (externalProjectPath != null) {
      if (dependency != null) {
        tab.view.setSelectedDependency(externalProjectPath, dependency)
      }
      else {
        tab.view.setSelectedExternalProject(externalProjectPath)
      }
    }
    UIComponentEditorTab.show(project, tab)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = Registry.`is`("external.system.dependency.analyzer")
  }

  init {
    templatePresentation.icon = AllIcons.Actions.DependencyAnalyzer
    templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.action.name")
  }
}