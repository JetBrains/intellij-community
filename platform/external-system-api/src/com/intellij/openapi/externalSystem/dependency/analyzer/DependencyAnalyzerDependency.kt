// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer

import org.jetbrains.annotations.Nls

data class DependencyAnalyzerDependency(
  val data: Data,
  val scope: Scope,
  val parent: DependencyAnalyzerDependency?,
  val status: List<Status>
) {

  override fun toString() = "($scope) $data -> $parent"

  sealed interface Data {

    data class Module(
      val name: @Nls String
    ) : Data {
      override fun toString() = name
    }

    data class Artifact(
      val groupId: @Nls String,
      val artifactId: @Nls String,
      val version: @Nls String
    ) : Data {
      override fun toString() = "$groupId:$artifactId:$version"
    }
  }

  class Scope(
    val id: String,
    val name: @Nls String,
    val title: @Nls(capitalization = Nls.Capitalization.Title) String
  ) {
    override fun equals(other: Any?) = other is Scope && id == other.id
    override fun hashCode() = id.hashCode()
    override fun toString() = title
  }

  sealed interface Status {

    object Omitted : Status

    class Warning(val message: @Nls String) : Status
  }
}