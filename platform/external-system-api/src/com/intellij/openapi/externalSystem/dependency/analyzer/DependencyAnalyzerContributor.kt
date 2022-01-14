// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency

/**
 * Contributor dependencies data for dependency analyzer.
 *
 * All functions, which give access for external system dependencies data,
 * are called from non-modal background thread to free UI thread when data is being loaded.
 * @see DependencyAnalyzerExtension.createContributor
 */
interface DependencyAnalyzerContributor {

  /**
   * @param listener should be called when dependencies data changed.
   */
  fun whenDataChanged(listener: () -> Unit, parentDisposable: Disposable)

  /**
   * Gets all projects that should be shown in dependency analyzer for current external system.
   */
  fun getProjects(): List<DependencyAnalyzerProject>

  /**
   * Gets scopes/configurations (e.g. compile, runtime, test, etc.) for specified external project.
   */
  fun getDependencyScopes(externalProjectPath: String): List<Dependency.Scope>

  /**
   * Gets dependencies for specified external project.
   */
  fun getDependencies(externalProjectPath: String): List<Dependency>
}