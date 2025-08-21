package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.project.Project
import java.nio.file.Path
import kotlin.io.path.absolute

object GoWelcomeScreenUtil2 {
  const val WELCOME_SCREEN_PROJECT_NAME: String = "GoLandWorkspace"

  /**
   * See [com.jetbrains.ds.workspace.WorkspaceUtil.Companion#isWorkspace]
   * TODO: Is it reliable, considering the possibility of naming collisions with existing projects?
   */
  @JvmStatic
  fun isWelcomeScreenProject2(project: Project): Boolean = project.name == WELCOME_SCREEN_PROJECT_NAME

  @JvmStatic
  fun getWelcomeScreenProjectPath2(): Path = ProjectUtil.getProjectPath(WELCOME_SCREEN_PROJECT_NAME).absolute()
}