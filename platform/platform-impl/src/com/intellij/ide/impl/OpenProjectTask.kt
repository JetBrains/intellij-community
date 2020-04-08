// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.FrameInfo
import com.intellij.projectImport.ProjectOpenedCallback

data class OpenProjectTask(@JvmField val forceOpenInNewFrame: Boolean = false,
                           @JvmField val projectToClose: Project? = null,
                           @JvmField var useDefaultProjectAsTemplate: Boolean = true,
                           @JvmField var isNewProject: Boolean = false,
                           /**
                            * Prepared project to open. If you just need to open newly created and prepared project (e.g. used by a new project action).
                            */
                           val project: Project? = null,
                           val projectName: String? = null,
                           val isDummyProject: Boolean = false,
                           val sendFrameBack: Boolean = false,
                           val showWelcomeScreen: Boolean = true,
                           var callback: ProjectOpenedCallback? = null,
                           val frame: FrameInfo? = null,
                           val projectWorkspaceId: String? = null,
                           val line: Int = -1,
                           val column: Int = -1) {
  constructor(project: Project) : this(false, project = project)

  constructor(forceOpenInNewFrame: Boolean = false, projectToClose: Project?) : this(forceOpenInNewFrame = forceOpenInNewFrame, projectToClose = projectToClose, useDefaultProjectAsTemplate = true)

  companion object {
    @JvmStatic
    fun newProject(useDefaultProjectAsTemplate: Boolean): OpenProjectTask {
      return OpenProjectTask(useDefaultProjectAsTemplate = useDefaultProjectAsTemplate, isNewProject = true)
    }
  }

  /** Used only by [ProjectUtil.openOrImport] */
  @JvmField
  var checkDirectoryForFileBasedProjects = true

  @JvmField
  var isRefreshVfsNeeded = true
}