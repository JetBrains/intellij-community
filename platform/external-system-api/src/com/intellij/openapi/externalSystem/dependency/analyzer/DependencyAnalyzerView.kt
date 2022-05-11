// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency

/**
 * Represents dependency analyzer view model.
 */
interface DependencyAnalyzerView : DataProvider {

  /**
   * Sets selected external project in analyzer project combobox.
   */
  fun setSelectedExternalProject(externalProjectPath: String)

  /**
   * Sets selected external project, finds and selects dependency with corresponding [data].
   * @see setSelectedExternalProject
   */
  fun setSelectedDependency(externalProjectPath: String, data: Dependency.Data)

  /**
   * Sets selected external project, finds and selects dependency with corresponding [data] and [scope].
   * @see setSelectedExternalProject
   */
  fun setSelectedDependency(externalProjectPath: String, data: Dependency.Data, scope: String)

  /**
   * Sets selected external project, finds and selects dependency with corresponding [dependencyPath].
   * @param dependencyPath is a list of dependencies from target dependency to dependency tree root.
   *  It is uniquely identifies dependency in dependency analyzer.
   * @see setSelectedExternalProject
   */
  fun setSelectedDependency(externalProjectPath: String, dependencyPath: List<Pair<Dependency.Data, String>>)

  companion object {
    const val ACTION_PLACE = "ExternalSystem.DependencyAnalyzerView.ActionPlace"
    val VIEW = DataKey.create<DependencyAnalyzerView>("ExternalSystem.DependencyAnalyzerView.View")
    val EXTERNAL_PROJECT_PATH = DataKey.create<String>("ExternalSystem.DependencyAnalyzerView.ProjectPath")
    val DEPENDENCY = DataKey.create<Dependency>("ExternalSystem.DependencyAnalyzerView.Dependency")
    val DEPENDENCIES = DataKey.create<Dependency>("ExternalSystem.DependencyAnalyzerView.Dependencies")
  }
}