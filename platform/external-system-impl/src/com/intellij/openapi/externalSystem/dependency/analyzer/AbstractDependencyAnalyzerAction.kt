// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.Module

abstract class AbstractDependencyAnalyzerAction<Data> : DependencyAnalyzerAction() {

  abstract fun getSelectedData(e: AnActionEvent): Data?

  abstract fun getModule(e: AnActionEvent, selectedData: Data): Module?

  abstract fun getDependencyData(e: AnActionEvent, selectedData: Data): DependencyAnalyzerDependency.Data?

  abstract fun getDependencyScope(e: AnActionEvent, selectedData: Data): String?

  override fun isEnabledAndVisible(e: AnActionEvent): Boolean {
    val selectedData = getSelectedData(e) ?: return false
    return getModule(e, selectedData) != null
  }

  override fun setSelectedState(view: DependencyAnalyzerView, e: AnActionEvent) {
    val selectedData = getSelectedData(e) ?: return
    val module = getModule(e, selectedData) ?: return
    val dependencyData = getDependencyData(e, selectedData)
    val dependencyScope = getDependencyScope(e, selectedData)
    if (dependencyData != null && dependencyScope != null) {
      view.setSelectedDependency(module, dependencyData, dependencyScope)
    }
    else if (dependencyData != null) {
      view.setSelectedDependency(module, dependencyData)
    }
    else {
      view.setSelectedExternalProject(module)
    }
  }
}