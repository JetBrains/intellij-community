// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects.impl

import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.openapi.util.io.toNioPath
import com.intellij.openapi.project.Project
import java.nio.file.Path

class DefaultTrustedProjectsLocator : TrustedProjectsLocator {

  override fun getProjectRoots(project: Project): List<Path> {
    return listOfNotNull(project.basePath?.toNioPath())
  }

  override fun getProjectRoots(projectRoot: Path, project: Project?): List<Path> {
    return listOf(projectRoot)
  }
}