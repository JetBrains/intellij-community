// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.Nls

interface DependencyContributor {

  fun whenDataChanged(listener: () -> Unit, parentDisposable: Disposable)

  fun getExternalProjects(): List<ExternalProject>

  fun getDependencyScopes(externalProjectPath: String): List<Scope>

  fun getDependencyGroups(externalProjectPath: String): List<DependencyGroup>

  fun getInspectionResult(externalProjectPath: String, dependency: Dependency): List<InspectionResult>

  data class ExternalProject(val path: String, val title: @Nls String) {
    override fun toString() = title
  }

  data class Scope(val id: String, val name: @Nls String, val title: @Nls String) {
    override fun toString() = id
  }

  data class Dependency(val data: Data, val scope: Scope, val usage: Dependency?) {

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