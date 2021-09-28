// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.util.NlsContexts

interface DependenciesContributor {

  fun getDependencyScopes(projectId: ExternalSystemProjectId): List<String>

  fun getProjectIds(): List<ExternalSystemProjectId>

  fun getProjectName(projectId: ExternalSystemProjectId): @NlsContexts.Label String

  fun getDependencies(projectId: ExternalSystemProjectId): List<DependencyData>

  fun getUsages(projectId: ExternalSystemProjectId, dependency: DependencyData): List<DependencyData>
}