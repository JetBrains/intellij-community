// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.actionSystem.AnActionEvent

abstract class AbstractDependencyAnalyzerAction<Data> : DependencyAnalyzerAction() {

  abstract fun getSelectedData(e: AnActionEvent): Data?

  abstract fun getExternalProjectPath(e: AnActionEvent, selectedData: Data): String?

  abstract fun getDependencyData(e: AnActionEvent, selectedData: Data): DependencyAnalyzerDependency.Data?

  abstract fun getDependencyScope(e: AnActionEvent, selectedData: Data): DependencyAnalyzerDependency.Scope?

  override fun isEnabledAndVisible(e: AnActionEvent): Boolean {
    val selectedData = getSelectedData(e) ?: return false
    return getExternalProjectPath(e, selectedData) != null
  }

  override fun setSelectedState(view: DependencyAnalyzerView, e: AnActionEvent) {
    val selectedData = getSelectedData(e) ?: return
    val externalProjectPath = getExternalProjectPath(e, selectedData) ?: return
    val dependencyData = getDependencyData(e, selectedData)
    val dependencyScope = getDependencyScope(e, selectedData)
    if (dependencyData != null && dependencyScope != null) {
      view.setSelectedDependency(externalProjectPath, dependencyData, dependencyScope)
    }
    else if (dependencyData != null) {
      view.setSelectedDependency(externalProjectPath, dependencyData)
    }
    else {
      view.setSelectedExternalProject(externalProjectPath)
    }
  }
}