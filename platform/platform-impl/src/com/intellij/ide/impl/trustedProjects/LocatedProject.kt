// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.trustedProjects

import com.intellij.openapi.project.Project
import java.nio.file.Path

class LocatedProject(
  val projectRoots: List<Path>,
  val project: Project?
) {
  companion object {

    fun locateProject(projectRoot: Path, project: Project?): LocatedProject {
      return locateProject(project) { getProjectRoots(projectRoot, project) }
    }

    fun locateProject(project: Project): LocatedProject {
      return locateProject(project) { getProjectRoots(project) }
    }

    private fun locateProject(
      project: Project?,
      getProjectRoots: ProjectLocator.() -> List<Path>
    ): LocatedProject {
      val projectRoots = LinkedHashSet<Path>()
      ProjectLocator.EP_NAME.forEachExtensionSafe { locator ->
        projectRoots.addAll(locator.getProjectRoots())
      }
      return LocatedProject(projectRoots.toList(), project)
    }
  }
}