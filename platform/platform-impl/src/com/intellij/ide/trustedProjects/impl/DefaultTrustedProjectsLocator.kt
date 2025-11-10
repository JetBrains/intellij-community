// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects.impl

import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.openapi.project.Project
import com.intellij.project.ProjectStoreOwner
import java.nio.file.Path

private class DefaultTrustedProjectsLocator : TrustedProjectsLocator {
  override fun getProjectRoots(project: Project): List<Path> {
    if (project !is ProjectStoreOwner) {
      return emptyList()
    }
    val projectStoreDescriptor = project.componentStore
      .storeDescriptor
    return listOf(
      projectStoreDescriptor.projectIdentityFile,
      //projectStoreDescriptor.historicalProjectBasePath,
    )
  }

  override fun getProjectRoots(projectRoot: Path, project: Project?): List<Path> = listOf(projectRoot)
}