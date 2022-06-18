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

  abstract fun getContributors(): List<Contributor<*>>

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val systemId = getSystemId(e) ?: return
    val dependencyAnalyzerManager = DependencyAnalyzerManager.getInstance(project)
    val dependencyAnalyzerView = dependencyAnalyzerManager.getOrCreate(systemId)
    for (contributor in getContributors()) {
      if (dependencyAnalyzerView.setSelectedState(contributor, e)) {
        break
      }
    }
  }

  private fun <D : Any> DependencyAnalyzerView.setSelectedState(contributor: Contributor<D>, e: AnActionEvent): Boolean {
    val selectedData = contributor.getSelectedData(e) ?: return false
    val externalProjectPath = contributor.getExternalProjectPath(e, selectedData) ?: return false
    val dependencyData = contributor.getDependencyData(e, selectedData)
    val dependencyScope = contributor.getDependencyScope(e, selectedData)
    if (dependencyData != null && dependencyScope != null) {
      setSelectedDependency(externalProjectPath, dependencyData, dependencyScope)
    }
    else if (dependencyData != null) {
      setSelectedDependency(externalProjectPath, dependencyData)
    }
    else {
      setSelectedExternalProject(externalProjectPath)
    }
    return true
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible =
      Registry.`is`("external.system.dependency.analyzer")
      && getContributors().any { isApplicable(it, e) }
  }

  private fun <D : Any> isApplicable(contributor: Contributor<D>, e: AnActionEvent): Boolean {
    val selectedData = contributor.getSelectedData(e) ?: return false
    return contributor.getExternalProjectPath(e, selectedData) != null
  }

  init {
    templatePresentation.icon = AllIcons.Actions.DependencyAnalyzer
    templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.action.name")
  }

  interface Contributor<Data : Any> {

    fun getSelectedData(e: AnActionEvent): Data?

    fun getExternalProjectPath(e: AnActionEvent, selectedData: Data): String?

    fun getDependencyData(e: AnActionEvent, selectedData: Data): DependencyAnalyzerDependency.Data?

    fun getDependencyScope(e: AnActionEvent, selectedData: Data): DependencyAnalyzerDependency.Scope?
  }
}