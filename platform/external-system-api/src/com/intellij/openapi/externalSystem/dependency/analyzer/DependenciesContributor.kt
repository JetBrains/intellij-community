// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe

interface DependenciesContributor {

  fun getExternalProjectPaths(): List<String>

  fun getExternalProjectName(externalProjectPath: String): @NlsContexts.Label String

  fun getRoot(externalProjectPath: String): Dependency

  fun getDependencyScopes(externalProjectPath: String): List<@NlsSafe String>

  fun getDependencyScope(externalProjectPath: String, dependency: Dependency): @NlsSafe String

  fun getDependencies(externalProjectPath: String, dependency: Dependency): List<Dependency>

  fun getVariances(externalProjectPath: String, dependency: Dependency): List<Dependency>

  fun getInspectionResult(externalProjectPath: String, dependency: Dependency): List<InspectionResult>

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