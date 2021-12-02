// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.Nls

interface DependencyContributor {

  fun whenDataChanged(listener: () -> Unit, parentDisposable: Disposable)

  fun getExternalProjects(): List<ExternalProject>

  fun getDependencyScopes(externalProjectPath: String): List<Scope>

  fun getDependencies(externalProjectPath: String): List<Dependency>

  class ExternalProject(val path: String, val title: @Nls String) {
    override fun equals(other: Any?) = other is ExternalProject && path == other.path
    override fun hashCode() = path.hashCode()
    override fun toString() = title
  }

  class Scope(val id: String, val name: @Nls String, val title: @Nls(capitalization = Nls.Capitalization.Title) String) {
    override fun equals(other: Any?) = other is Scope && id == other.id
    override fun hashCode() = id.hashCode()
    override fun toString() = title
  }

  data class Dependency(val data: Data, val scope: Scope, val usage: Dependency?, val status: List<Status>) {

    override fun toString() = "($scope) $data -> $usage"

    sealed interface Data {

      data class Module(val name: String) : Data {
        override fun toString() = name
      }

      data class Artifact(val groupId: String, val artifactId: String, val version: String) : Data {
        override fun toString() = "$groupId:$artifactId:$version"
      }
    }
  }

  sealed interface Status {

    object Omitted : Status

    class Warning(val message: @Nls String) : Status
  }
}