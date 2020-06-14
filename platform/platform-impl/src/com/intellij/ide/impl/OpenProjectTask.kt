// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.FrameInfo
import com.intellij.projectImport.ProjectOpenedCallback
import org.jetbrains.annotations.ApiStatus
import java.util.function.Predicate

data class OpenProjectTask(val forceOpenInNewFrame: Boolean = false,
                           val projectToClose: Project? = null,
                           val isNewProject: Boolean = false,
                           /**
                            * Ignored if isNewProject is set to false.
                            */
                           val useDefaultProjectAsTemplate: Boolean = isNewProject,
                           /**
                            * Prepared project to open. If you just need to open newly created and prepared project (e.g. used by a new project action).
                            */
                           val project: Project? = null,
                           val projectName: String? = null,
                           val sendFrameBack: Boolean = false,
                           /**
                            * Whether to show welcome screen if failed to open project.
                            */
                           val showWelcomeScreen: Boolean = true,
                           @set:Deprecated(message = "Pass to constructor", level = DeprecationLevel.ERROR)
                           var callback: ProjectOpenedCallback? = null,
                           /**
                            * Ignored if project is explicitly set.
                            */
                           internal val beforeOpen: ((Project) -> Boolean)? = null,
                           internal val preparedToOpen: ((Module) -> Unit)? = null,
                           val frame: FrameInfo? = null,
                           val projectWorkspaceId: String? = null,
                           val line: Int = -1,
                           val column: Int = -1,
                           val isRefreshVfsNeeded: Boolean = true,
                           /**
                            * Whether to run DirectoryProjectConfigurator if a new project or no modules.
                            */
                           val runConfigurators: Boolean = false,
                           val runConversionBeforeOpen: Boolean = true,
                           internal val isProjectCreatedWithWizard: Boolean = false) {
  @ApiStatus.Internal
  fun withBeforeOpenCallback(callback: Predicate<Project>) = copy(beforeOpen = { callback.test(it) })

  @ApiStatus.Internal
  fun withProjectName(value: String?) = copy(projectName = value)

  @ApiStatus.Internal
  fun asNewProjectAndRunConfigurators() = copy(isNewProject = true, runConfigurators = true)

  @ApiStatus.Internal
  fun withRunConfigurators() = copy(runConfigurators = true)

  companion object {
    @JvmStatic
    @JvmOverloads
    fun newProject(runConfigurators: Boolean = false): OpenProjectTask {
      return OpenProjectTask(isNewProject = true, runConfigurators = runConfigurators)
    }

    @JvmStatic
    fun newProjectFromWizardAndRunConfigurators(projectToClose: Project?, isRefreshVfsNeeded: Boolean): OpenProjectTask {
      return OpenProjectTask(isNewProject = true,
                             projectToClose = projectToClose,
                             runConfigurators = true,
                             isProjectCreatedWithWizard = true,
                             isRefreshVfsNeeded = isRefreshVfsNeeded)
    }

    @JvmStatic
    @JvmOverloads
    fun withProjectToClose(projectToClose: Project?, forceOpenInNewFrame: Boolean = false): OpenProjectTask {
      return OpenProjectTask(projectToClose = projectToClose, forceOpenInNewFrame = forceOpenInNewFrame)
    }

    @JvmStatic
    fun withCreatedProject(project: Project?): OpenProjectTask {
      return OpenProjectTask(project = project)
    }
  }

  /** Used only by [ProjectUtil.openOrImport] */
  @JvmField
  internal var checkDirectoryForFileBasedProjects = true
}