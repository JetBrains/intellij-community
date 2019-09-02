// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.FrameInfo
import com.intellij.projectImport.ProjectOpenedCallback

class OpenProjectTask @JvmOverloads constructor(@JvmField val forceOpenInNewFrame: Boolean = false,
                                                @JvmField val projectToClose: Project? = null) {
  @JvmField
  var frame: FrameInfo? = null

  @JvmField
  var projectWorkspaceId: String? = null

  @JvmField
  var showWelcomeScreen = true

  @JvmField
  var sendFrameBack = false

  @JvmField
  var isNewProject = false

  /** Used only by [ProjectUtil.openOrImport] */
  @JvmField
  var checkDirectoryForFileBasedProjects = true

  @JvmField
  var useDefaultProjectAsTemplate = true

  @JvmField
  var callback: ProjectOpenedCallback? = null

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
    return copy
  }
}