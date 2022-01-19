// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import javax.swing.JComponent

/**
 * Represents dependency analyzer view model.
 */
interface DependencyAnalyzerView : DataProvider {

  /**
   * Dependency analyzer component to show in editor panel tab.
   */
  val component: JComponent

  /**
   * Sets selected external project in analyzer project combobox.
   */
  fun setSelectedExternalProject(externalProjectPath: String)

  /**
   * Sets selected external project and selected dependency in analyzer dependency list/tree.
   * @see setSelectedExternalProject
   */
  fun setSelectedDependency(externalProjectPath: String, dependency: Dependency)

  /**
   * Sets selected external project, finds and selects dependency with corresponding [data].
   * @see setSelectedExternalProject
   */
  fun setSelectedDependency(externalProjectPath: String, data: Dependency.Data)

  /**
   * Sets selected external project, finds and selects dependency with corresponding [data] and [scope].
   * @see setSelectedExternalProject
   */
  fun setSelectedDependency(externalProjectPath: String, data: Dependency.Data, scope: Dependency.Scope)

  companion object {
    const val ACTION_PLACE = "ExternalSystem.DependencyAnalyzerView.ActionPlace"
    val VIEW = DataKey.create<DependencyAnalyzerView>("ExternalSystem.DependencyAnalyzerView.View")
    val PROJECT = CommonDataKeys.PROJECT
    val EXTERNAL_SYSTEM_ID = DataKey.create<ProjectSystemId>("external.system.id")
    val EXTERNAL_PROJECT_PATH = DataKey.create<String>("ExternalSystem.DependencyAnalyzerView.ProjectPath")
    val DEPENDENCY = DataKey.create<Dependency>("ExternalSystem.DependencyAnalyzerView.Dependency")
    val DEPENDENCIES = DataKey.create<Dependency>("ExternalSystem.DependencyAnalyzerView.Dependencies")
  }
}