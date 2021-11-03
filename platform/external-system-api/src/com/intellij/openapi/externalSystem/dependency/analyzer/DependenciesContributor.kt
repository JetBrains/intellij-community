// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe

interface DependenciesContributor {

  fun getProjectIds(): List<ExternalSystemProjectId>

  fun getProjectName(projectId: ExternalSystemProjectId): @NlsContexts.Label String

  fun getRoot(projectId: ExternalSystemProjectId): Dependency

  fun getDependencyScopes(projectId: ExternalSystemProjectId): List<@NlsSafe String>

  fun getDependencyScope(projectId: ExternalSystemProjectId, dependency: Dependency): @NlsSafe String

  fun getDependencies(projectId: ExternalSystemProjectId, dependency: Dependency): List<Dependency>

  fun getVariances(projectId: ExternalSystemProjectId, dependency: Dependency): List<Dependency>

  fun getInspectionResult(projectId: ExternalSystemProjectId, dependency: Dependency): List<InspectionResult>

  data class Dependency(val data: Data, val usage: Dependency?) {

    sealed interface Data {

      data class Module(val name: String) : Data

      data class Artifact(val groupId: String, val artifactId: String, val version: String) : Data
    }
  }

  interface InspectionResult {

    object Omitted : InspectionResult

    object Duplicate : InspectionResult

    class VersionConflict(val conflicted: Dependency.Data.Artifact) : InspectionResult
  }
}