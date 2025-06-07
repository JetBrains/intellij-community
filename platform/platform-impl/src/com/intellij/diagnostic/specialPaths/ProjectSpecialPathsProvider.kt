// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.specialPaths

import com.intellij.openapi.project.Project

internal class ProjectSpecialPathsProvider : SpecialPathsProvider {
  override fun collectPaths(project: Project?): List<SpecialPathEntry> {
    if (project == null) return listOf()
    return listOf(
      SpecialPathEntry("PROJECT BasePath", project.basePath ?: "", SpecialPathEntry.Kind.Folder),
    )
  }
}
