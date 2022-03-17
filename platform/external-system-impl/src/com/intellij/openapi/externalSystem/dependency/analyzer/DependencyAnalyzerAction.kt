// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.registry.Registry

abstract class DependencyAnalyzerAction : DumbAwareAction() {

  abstract fun getSystemId(e: AnActionEvent): ProjectSystemId?

  abstract fun isEnabledAndVisible(e: AnActionEvent): Boolean

  abstract fun setSelectedState(view: DependencyAnalyzerView, e: AnActionEvent)

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val systemId = getSystemId(e) ?: return
    val dependencyAnalyzerManager = DependencyAnalyzerManager.getInstance(project)
    val dependencyAnalyzerView = dependencyAnalyzerManager.getOrCreate(systemId)
    setSelectedState(dependencyAnalyzerView, e)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = Registry.`is`("external.system.dependency.analyzer") && isEnabledAndVisible(e)
  }

  init {
    templatePresentation.icon = AllIcons.Actions.DependencyAnalyzer
    templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.action.name")
  }
}