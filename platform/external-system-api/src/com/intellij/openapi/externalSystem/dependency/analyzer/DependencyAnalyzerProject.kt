// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer

import org.jetbrains.annotations.Nls

class DependencyAnalyzerProject(
  val path: String,
  val title: @Nls String
) {
  override fun equals(other: Any?) = other is DependencyAnalyzerProject && path == other.path
  override fun hashCode() = path.hashCode()
  override fun toString() = title
}