// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DependencyAnalyzerEntities")

package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.annotations.Nls
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency

class DAProject(
  override val module: Module,
  override val title: @Nls String
) : UserDataHolderBase(), DependencyAnalyzerProject {
  override fun equals(other: Any?): Boolean = other is DependencyAnalyzerProject && module == other.module
  override fun hashCode(): Int = module.hashCode()
  override fun toString(): @Nls String = title
}

data class DADependency(
  override val data: Dependency.Data,
  override val scope: Dependency.Scope,
  override val parent: Dependency?,
  override val status: List<Dependency.Status>
) : UserDataHolderBase(), Dependency {
  override fun toString(): String = "($scope) $data -> $parent"
}

data class DAModule(
  override val name: @Nls String
) : UserDataHolderBase(), Dependency.Data.Module {
  override fun toString(): @Nls String = name
}

data class DAArtifact(
  override val groupId: @Nls String,
  override val artifactId: @Nls String,
  override val version: @Nls String
) : UserDataHolderBase(), Dependency.Data.Artifact {
  override fun toString(): String = "$groupId:$artifactId:$version"
}

data class DAScope(
  override val name: @Nls String,
  override val title: @Nls(capitalization = Nls.Capitalization.Title) String
) : UserDataHolderBase(), Dependency.Scope {
  override fun toString(): @Nls(capitalization = Nls.Capitalization.Title) String = title
}

object DAOmitted : UserDataHolderBase(), Dependency.Status.Omitted

class DAWarning(
  override val message: @Nls String
) : UserDataHolderBase(), Dependency.Status.Warning