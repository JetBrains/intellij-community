// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.trustedProjects

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.nio.file.Path

interface ProjectLocator {

  fun getProjectRoots(project: Project): List<Path>

  fun getProjectRoots(projectRoot: Path, project: Project?): List<Path>

  companion object {

    val EP_NAME = ExtensionPointName<ProjectLocator>("com.intellij.projectLocator")
  }
}