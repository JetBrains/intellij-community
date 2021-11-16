// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project

interface DependencyAnalyzerExtension {

  fun getContributor(project: Project, systemId: ProjectSystemId): DependencyContributor?

  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName.create<DependencyAnalyzerExtension>("com.intellij.externalSystemDependencyAnalyzer")

    @JvmStatic
    fun getExtension(project: Project, systemId: ProjectSystemId): DependencyContributor {
      return EP_NAME.extensionList.firstNotNullOf { it.getContributor(project, systemId) }
    }
  }
}