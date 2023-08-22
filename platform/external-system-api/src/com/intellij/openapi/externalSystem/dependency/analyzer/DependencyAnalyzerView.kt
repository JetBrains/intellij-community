// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.module.Module
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency

/**
 * Represents dependency analyzer view model.
 */
interface DependencyAnalyzerView : DataProvider {

  /**
   * Sets selected external project in analyzer project combobox.
   */
  fun setSelectedExternalProject(module: Module)

  /**
   * Sets selected external project, finds and selects dependency with corresponding [data].
   * @see setSelectedExternalProject
   */
  fun setSelectedDependency(module: Module, data: Dependency.Data)

  /**
   * Sets selected external project, finds and selects dependency with corresponding [data] and [scope].
   * @see setSelectedExternalProject
   */
  fun setSelectedDependency(module: Module, data: Dependency.Data, scope: String)

  /**
   * Sets selected external project, finds and selects dependency with corresponding dependency [path].
   * @param path is a list of dependencies from target dependency to dependency tree root.
   * @see setSelectedExternalProject
   */
  fun setSelectedDependency(module: Module, path: List<Dependency.Data>)

  /**
   * Sets selected external project, finds and selects dependency with corresponding dependency [path] and [scope].
   * @param path is a list of dependencies from target dependency to dependency tree root.
   * @see setSelectedExternalProject
   */
  fun setSelectedDependency(module: Module, path: List<Dependency.Data>, scope: String)

  companion object {
    const val ACTION_PLACE: String = "ExternalSystem.DependencyAnalyzerView.ActionPlace"
    val VIEW: DataKey<DependencyAnalyzerView> = DataKey.create<DependencyAnalyzerView>("ExternalSystem.DependencyAnalyzerView.View")
    val DEPENDENCY: DataKey<DependencyAnalyzerDependency> = DataKey.create<Dependency>("ExternalSystem.DependencyAnalyzerView.Dependency")
    val DEPENDENCIES: DataKey<DependencyAnalyzerDependency> = DataKey.create<Dependency>("ExternalSystem.DependencyAnalyzerView.Dependencies")
  }
}