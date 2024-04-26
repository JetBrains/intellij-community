// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.NioPathPrefixTreeFactory
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Locates project roots for `Trust Project` check.
 */
interface TrustedProjectsLocator {

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

  /**
   * Represents the main project properties for project trust check.
   */
  @ApiStatus.NonExtendable
  interface LocatedProject {

    /**
     * Collected project roots which can contain malicious code for [project].
     */
    val projectRoots: List<Path>

    /**
     * Project instance can be null if we need to make "trust check" when project isn't open yet.
     */
    val project: Project?
  }

  companion object {

    val EP_NAME: ExtensionPointName<TrustedProjectsLocator> = ExtensionPointName("com.intellij.trustedProjectsLocator")

    fun locateProject(projectRoot: Path, project: Project?): LocatedProject {
      return locateProject(project) { getProjectRoots(projectRoot, project) }
    }

    fun locateProject(project: Project): LocatedProject {
      return locateProject(project) { getProjectRoots(project) }
    }

    private fun locateProject(
      project: Project?,
      getProjectRoots: TrustedProjectsLocator.() -> List<Path>
    ): LocatedProject {
      val projectRoots = NioPathPrefixTreeFactory.createSet()
      EP_NAME.forEachExtensionSafe { locator ->
        for (projectRoot in locator.getProjectRoots()) {
          projectRoots.add(projectRoot)
        }
      }
      return object : LocatedProject {
        override val projectRoots = projectRoots.getRoots().toList()
        override val project = project
      }
    }
  }
}