// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DependencyAnalyzerEntities")

package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency
import org.jetbrains.annotations.Nls

class DAProject(
  override val path: String,
  override val title: @Nls String
) : UserDataHolderBase(), DependencyAnalyzerProject {
  override fun equals(other: Any?) = other is DependencyAnalyzerProject && path == other.path
  override fun hashCode() = path.hashCode()
  override fun toString() = title
}

data class DADependency(
  override val data: Dependency.Data,
  override val scope: Dependency.Scope,
  override val parent: Dependency?,
  override val status: List<Dependency.Status>
) : UserDataHolderBase(), Dependency {
  override fun toString() = "($scope) $data -> $parent"
}

data class DAModule(
  override val name: @Nls String
) : UserDataHolderBase(), Dependency.Data.Module {
  override fun toString() = name
}

data class DAArtifact(
  override val groupId: @Nls String,
  override val artifactId: @Nls String,
  override val version: @Nls String
) : UserDataHolderBase(), Dependency.Data.Artifact {
  override fun toString() = "$groupId:$artifactId:$version"
}

data class DAScope(
  override val name: @Nls String,
  override val title: @Nls(capitalization = Nls.Capitalization.Title) String
) : UserDataHolderBase(), Dependency.Scope {
  override fun toString() = title
}

object DAOmitted : UserDataHolderBase(), Dependency.Status.Omitted

class DAWarning(
  override val message: @Nls String
) : UserDataHolderBase(), Dependency.Status.Warning