// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
                           /** Ignored if [isNewProject] is set to false. */
                           val useDefaultProjectAsTemplate: Boolean = isNewProject,
                           /** When you just need to open an already created and prepared project; used e.g. by the "new project" action. */
                           val project: Project? = null,
                           val projectName: String? = null,
                           /** Whether to show welcome screen if failed to open project. */
                           val showWelcomeScreen: Boolean = true,
                           val callback: ProjectOpenedCallback? = null,
                           val frameManager: Any? = null,
                           val line: Int = -1,
                           val column: Int = -1,
                           val isRefreshVfsNeeded: Boolean = true,
                           /** Whether to run `DirectoryProjectConfigurator` if a new project or no modules. */
                           val runConfigurators: Boolean = false,
                           val runConversionBeforeOpen: Boolean = true,
                           val projectWorkspaceId: String? = null,
                           val isProjectCreatedWithWizard: Boolean = false,
                           @TestOnly
                           val preloadServices: Boolean = true,
                           val beforeInit: ((Project) -> Unit)? = null,
                           /** Ignored if project is explicitly set. */
                           val beforeOpen: ((Project) -> Boolean)? = null,
                           val preparedToOpen: ((Module) -> Unit)? = null) {

  private var _checkDirectoryForFileBasedProjects = true
  private var _openProcessorChooser: ((List<ProjectOpenProcessor>) -> ProjectOpenProcessor)? = null

  @get:ApiStatus.Internal
  val checkDirectoryForFileBasedProjects: Boolean by ::_checkDirectoryForFileBasedProjects

  @get:ApiStatus.Internal
  val openProcessorChooser: ((List<ProjectOpenProcessor>) -> ProjectOpenProcessor)? by ::_openProcessorChooser

  fun withForceOpenInNewFrame(forceOpenInNewFrame: Boolean) = copy(forceOpenInNewFrame = forceOpenInNewFrame)
  fun withProjectToClose(projectToClose: Project?) = copy(projectToClose = projectToClose)
  fun asNewProject() = copy(isNewProject = true, useDefaultProjectAsTemplate = true)
  fun withProject(project: Project?) = copy(project = project)
  fun withProjectName(projectName: String?) = copy(projectName = projectName)
  fun withRunConfigurators() = copy(runConfigurators = true)
  fun withoutVfsRefresh() = copy(isRefreshVfsNeeded = false)
  fun withCreatedByWizard() = copy(isProjectCreatedWithWizard = true)

  @ApiStatus.Internal
  fun withBeforeOpenCallback(callback: Predicate<Project>) = copy(beforeOpen = { callback.test(it) })

  @ApiStatus.Internal
  fun withPreparedToOpenCallback(callback: Consumer<Module>) = copy(preparedToOpen = { callback.accept(it) })

  @ApiStatus.Internal
  fun doNotCheckDirectoryForFileBasedProjects() = copy().also { it._checkDirectoryForFileBasedProjects = false }

  @ApiStatus.Internal
  fun withOpenProcessorChooser(openProcessorChooser: (List<ProjectOpenProcessor>) -> ProjectOpenProcessor): OpenProjectTask =
    copy().also { it._openProcessorChooser = openProcessorChooser }

  companion object {
    @JvmStatic
    fun build(): OpenProjectTask = OpenProjectTask()

    @JvmStatic
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use build(), withProjectToClose(), withForceOpenInNewFrame()", level = DeprecationLevel.ERROR)
    fun withProjectToClose(projectToClose: Project?, forceOpenInNewFrame: Boolean): OpenProjectTask =
      OpenProjectTask(projectToClose = projectToClose, forceOpenInNewFrame = forceOpenInNewFrame)

    @JvmStatic
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use build(), withProject()", level = DeprecationLevel.ERROR)
    fun withCreatedProject(project: Project?): OpenProjectTask = OpenProjectTask(project = project)
  }
}
