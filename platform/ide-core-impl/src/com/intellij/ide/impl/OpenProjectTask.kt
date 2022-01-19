// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.projectImport.ProjectOpenedCallback
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.function.Consumer
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
                           /**
                            * Whether to show welcome screen if failed to open project.
                            */
                           val showWelcomeScreen: Boolean = true,
                           @set:Deprecated(message = "Pass to constructor", level = DeprecationLevel.ERROR)
                           @set:ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
                           var callback: ProjectOpenedCallback? = null,
                           val frameManager: Any? = null,
                           val line: Int = -1,
                           val column: Int = -1,
                           val isRefreshVfsNeeded: Boolean = true,
                           /**
                            * Whether to run DirectoryProjectConfigurator if a new project or no modules.
                            */
                           val runConfigurators: Boolean = false,
                           val runConversionBeforeOpen: Boolean = true,
                           val projectWorkspaceId: String? = null,
                           val isProjectCreatedWithWizard: Boolean = false,
                           @TestOnly
                           val preloadServices: Boolean = true,
                           val beforeInit: ((Project) -> Unit)? = null,
                           /**
                            * Ignored if project is explicitly set.
                            */
                           val beforeOpen: ((Project) -> Boolean)? = null,
                           val preparedToOpen: ((Module) -> Unit)? = null,
                           val openProcessorChooser: ((List<ProjectOpenProcessor>) -> ProjectOpenProcessor)? = null) {

  constructor(forceOpenInNewFrame: Boolean = false,
              projectToClose: Project? = null,
              isNewProject: Boolean = false,
              useDefaultProjectAsTemplate: Boolean = isNewProject,
              project: Project? = null,
              projectName: String? = null,
              showWelcomeScreen: Boolean = true,
              callback: ProjectOpenedCallback? = null,
              frameManager: Any? = null,
              line: Int = -1,
              column: Int = -1,
              isRefreshVfsNeeded: Boolean = true,
              runConfigurators: Boolean = false,
              runConversionBeforeOpen: Boolean = true,
              projectWorkspaceId: String? = null,
              isProjectCreatedWithWizard: Boolean = false,
              preloadServices: Boolean = true,
              beforeInit: ((Project) -> Unit)? = null,
              beforeOpen: ((Project) -> Boolean)? = null,
              preparedToOpen: ((Module) -> Unit)? = null) :
    this(forceOpenInNewFrame,
         projectToClose,
         isNewProject,
         useDefaultProjectAsTemplate,
         project,
         projectName,
         showWelcomeScreen,
         callback,
         frameManager,
         line,
         column,
         isRefreshVfsNeeded,
         runConfigurators,
         runConversionBeforeOpen,
         projectWorkspaceId,
         isProjectCreatedWithWizard,
         preloadServices,
         beforeInit,
         beforeOpen,
         preparedToOpen,
         null)

  @ApiStatus.Internal
  fun withBeforeOpenCallback(callback: Predicate<Project>) = copy(beforeOpen = { callback.test(it) })

  @ApiStatus.Internal
  fun withPreparedToOpenCallback(callback: Consumer<Module>) = copy(preparedToOpen = { callback.accept(it) })

  @ApiStatus.Internal
  fun withProjectName(value: String?) = copy(projectName = value)

  @ApiStatus.Internal
  fun asNewProjectAndRunConfigurators() = copy(isNewProject = true, runConfigurators = true, useDefaultProjectAsTemplate = true)

  @ApiStatus.Internal
  fun withRunConfigurators() = copy(runConfigurators = true)

  @ApiStatus.Internal
  fun withForceOpenInNewFrame(value: Boolean) = copy(forceOpenInNewFrame = value)

  @ApiStatus.Internal
  fun withOpenProcessorChooser(value: (List<ProjectOpenProcessor>) -> ProjectOpenProcessor) = copy(openProcessorChooser = value)

  private var _untrusted: Boolean = false

  val untrusted: Boolean
    get() = _untrusted

  fun untrusted(): OpenProjectTask {
    val copy = copy()
    copy._untrusted = true
    return copy
  }

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
    fun fromWizardAndRunConfigurators(): OpenProjectTask {
      return OpenProjectTask(runConfigurators = true,
                             isProjectCreatedWithWizard = true,
                             isRefreshVfsNeeded = false)
    }

    @JvmStatic
    @JvmOverloads
    fun withProjectToClose(projectToClose: Project?, forceOpenInNewFrame: Boolean = false): OpenProjectTask {
      return OpenProjectTask(projectToClose = projectToClose, forceOpenInNewFrame = forceOpenInNewFrame)
    }

    @JvmStatic
    fun withProjectToClose(newProject: Project?, projectToClose: Project?, forceOpenInNewFrame: Boolean): OpenProjectTask {
      return OpenProjectTask(
        forceOpenInNewFrame = forceOpenInNewFrame,
        projectToClose = projectToClose,
        project = newProject)
    }

    @JvmStatic
    fun withCreatedProject(project: Project?): OpenProjectTask {
      return OpenProjectTask(project = project)
    }
  }

  /** Used only by [ProjectUtil.openOrImport] */
  @JvmField
  var checkDirectoryForFileBasedProjects = true
}
