// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.FrameInfo
import com.intellij.projectImport.ProjectOpenedCallback

class OpenProjectTask @JvmOverloads constructor(@JvmField val forceOpenInNewFrame: Boolean = false,
                                                @JvmField val projectToClose: Project? = null,
                                                @JvmField var useDefaultProjectAsTemplate: Boolean = true,
                                                @JvmField var isNewProject: Boolean = false,
                                                /**
                                                 * Prepared project to open. If you just need to open newly created and prepared project (e.g. used by a new project action).
                                                 */
                                                val project: Project? = null) {
 constructor(project: Project) : this(false, project = project)

  @JvmField
  var frame: FrameInfo? = null

  @JvmField
  var projectWorkspaceId: String? = null

  @JvmField
  var showWelcomeScreen = true

  @JvmField
  var sendFrameBack = false

  /** Used only by [ProjectUtil.openOrImport] */
  @JvmField
  var checkDirectoryForFileBasedProjects = true

  @JvmField
  var isRefreshVfsNeeded = true

  @JvmField
  var callback: ProjectOpenedCallback? = null

  @JvmField
  var line: Int = -1

  @JvmField
  var column: Int = -1

  var dummyProjectName: String? = null

  fun copy(): OpenProjectTask {
    val copy = OpenProjectTask(forceOpenInNewFrame, projectToClose)
    copy.frame = frame
    copy.projectWorkspaceId = projectWorkspaceId
    copy.showWelcomeScreen = showWelcomeScreen
    copy.sendFrameBack = sendFrameBack
    copy.isNewProject = isNewProject
    copy.checkDirectoryForFileBasedProjects = checkDirectoryForFileBasedProjects
    copy.useDefaultProjectAsTemplate = useDefaultProjectAsTemplate
    copy.callback = callback
    copy.isRefreshVfsNeeded = isRefreshVfsNeeded
    copy.dummyProjectName = dummyProjectName
    copy.line = line
    copy.column = column
    return copy
  }
}