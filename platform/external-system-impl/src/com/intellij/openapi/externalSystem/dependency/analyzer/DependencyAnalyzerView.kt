// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.Dependency
import javax.swing.JComponent

interface DependencyAnalyzerView {

  val component: JComponent

  fun setSelectedExternalProject(externalProjectPath: String)

  fun setSelectedDependency(externalProjectPath: String, dependency: Dependency)
}