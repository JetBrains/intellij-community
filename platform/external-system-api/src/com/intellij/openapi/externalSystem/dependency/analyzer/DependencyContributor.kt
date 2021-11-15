// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe

interface DependencyContributor {

  fun whenDataChanged(listener: () -> Unit, parentDisposable: Disposable)

  fun getExternalProjectPaths(): List<String>

  fun getExternalProjectName(externalProjectPath: String): @NlsContexts.Label String

  fun getDependencyGroups(externalProjectPath: String): List<DependencyGroup>

  fun getDependencyScopes(externalProjectPath: String): List<@NlsSafe String>

  fun getDependencyScope(externalProjectPath: String, dependency: Dependency): @NlsSafe String

  fun getInspectionResult(externalProjectPath: String, dependency: Dependency): List<InspectionResult>

  data class Dependency(val data: Data, val usage: Dependency?) {

    override fun toString() = "$data -> $usage"

    sealed interface Data {

      data class Module(val name: String) : Data {
        override fun toString() = name
      }

      data class Artifact(val groupId: String, val artifactId: String, val version: String) : Data {
        override fun toString() = "$groupId:$artifactId:$version"
      }
    }
  }

  data class DependencyGroup(val data: Dependency.Data, val variances: List<Dependency>) {
    override fun toString() = data.toString()
  }

  interface InspectionResult {

    object Omitted : InspectionResult

    object Duplicate : InspectionResult

    class VersionConflict(val conflicted: Dependency.Data.Artifact) : InspectionResult
  }
}