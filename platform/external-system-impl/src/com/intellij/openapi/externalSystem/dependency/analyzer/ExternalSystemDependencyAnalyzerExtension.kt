// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project


class ExternalSystemDependencyAnalyzerExtension : DependencyAnalyzerExtension {
  override fun getContributor(project: Project, systemId: ProjectSystemId): DependenciesContributor? {
    ExternalSystemApiUtil.getManager(systemId) ?: return null
    return DummyDependenciesContributor(project)
  }
}