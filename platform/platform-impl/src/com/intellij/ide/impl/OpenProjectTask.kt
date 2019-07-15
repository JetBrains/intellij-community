// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.FrameInfo
import com.intellij.projectImport.ProjectOpenedCallback

data class OpenProjectTask @JvmOverloads constructor(val forceOpenInNewFrame: Boolean = false,
                                                     val projectToClose: Project? = null,
                                                     val frame: FrameInfo? = null,
                                                     val projectWorkspaceId: String? = null) {
  /**
   * Used only by [ProjectUtil.openOrImport].
   */
  var checkDirectoryForFileBasedProjects: Boolean = true
  var isTempProject = false
  var useDefaultProjectAsTemplate: Boolean = true

  var callback: ProjectOpenedCallback? = null

  var showWelcomeScreenIfNoProjectOpened = true
  var sendFrameBack = false

  var isNewProject = false
}