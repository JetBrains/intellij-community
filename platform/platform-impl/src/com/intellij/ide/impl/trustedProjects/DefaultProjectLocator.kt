// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.trustedProjects

import com.intellij.openapi.file.CanonicalPathUtil.toNioPath
import com.intellij.openapi.project.Project
import java.nio.file.Path

class DefaultProjectLocator : ProjectLocator {

  override fun getProjectRoots(project: Project): List<Path> {
    return listOfNotNull(project.basePath?.toNioPath())
  }

  override fun getProjectRoots(projectRoot: Path, project: Project?): List<Path> {
    return listOf(projectRoot)
  }
}