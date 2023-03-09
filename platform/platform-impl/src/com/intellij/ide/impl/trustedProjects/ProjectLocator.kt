// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.trustedProjects

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * Locates project roots for `Trust Project` check.
 */
interface ProjectLocator {

  /**
   * Locates project roots for [project].
   * This function is used for a general case on Project.isTrusted check.
   * Note: This function can be called hundreds of times per second.
   */
  fun getProjectRoots(project: Project): List<Path>

  /**
   * Locates project roots for an existed project which isn't opened or linked.
   * This function is used for cases before loading or linking a new project or
   * module (the project or modules aren't initialised).
   *
   * @param projectRoot is a directory to open or link a new project or module
   * @param project current project. It isn't null for a link module case
   */
  fun getProjectRoots(projectRoot: Path, project: Project?): List<Path>

  companion object {

    val EP_NAME = ExtensionPointName<ProjectLocator>("com.intellij.projectLocator")
  }
}