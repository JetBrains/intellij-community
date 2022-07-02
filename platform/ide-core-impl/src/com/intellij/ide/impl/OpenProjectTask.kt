// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.projectImport.ProjectOpenedCallback
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.function.Predicate

data class OpenProjectTask internal constructor(val forceOpenInNewFrame: Boolean,
                                                val projectToClose: Project?,
                                                val isNewProject: Boolean = false,
                                                /** Ignored if [isNewProject] is set to false. */
                                                val useDefaultProjectAsTemplate: Boolean = isNewProject,
                                                /** When you just need to open an already created and prepared project; used e.g. by the "new project" action. */
                                                val project: Project? = null,
                                                val projectName: String?,
                                                /** Whether to show welcome screen if failed to open project. */
                                                val showWelcomeScreen: Boolean,
                                                val callback: ProjectOpenedCallback? = null,
                                                val line: Int = -1,
                                                val column: Int = -1,
                                                val isRefreshVfsNeeded: Boolean,
                                                /**
                                                 *  Whether to run [configurators][com.intellij.platform.DirectoryProjectConfigurator] if [isNewProject] or has no modules.
                                                 *
                                                 *  **NB**: if project was [loaded from cache][com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl.loadedFromCache],
                                                 *  but no serialized modules were found, configurators will be run regardless of [runConfigurators] value.
                                                 *  See com.intellij.platform.PlatformProjectOpenProcessor.Companion.isLoadedFromCacheButHasNoModules
                                                 */
                                                val runConfigurators: Boolean = false,
                                                val runConversionBeforeOpen: Boolean,
                                                val projectWorkspaceId: String? = null,
                                                val isProjectCreatedWithWizard: Boolean = false,
                                                @TestOnly
                                                val preloadServices: Boolean = true,
                                                val beforeInit: ((Project) -> Unit)?,
                                                /** Ignored if project is explicitly set. */
                                                val beforeOpen: (suspend (Project) -> Boolean)?,
                                                val preparedToOpen: ((Module) -> Unit)?,
                                                val preventIprLookup: Boolean,
                                                val processorChooser: ((List<Any>) -> Any)?,
                                                val implOptions: Any? = null) {
  constructor(forceOpenInNewFrame: Boolean = false,
              projectToClose: Project? = null,
              isNewProject: Boolean = false,
              /** Ignored if [isNewProject] is set to false. */
              useDefaultProjectAsTemplate: Boolean = isNewProject,
              /** When you just need to open an already created and prepared project; used e.g. by the "new project" action. */
              project: Project? = null,
              projectName: String? = null,
              /** Whether to show welcome screen if failed to open project. */
              showWelcomeScreen: Boolean = true,
              callback: ProjectOpenedCallback? = null,
              line: Int = -1,
              column: Int = -1,
              isRefreshVfsNeeded: Boolean = true,
              /**
               *  Whether to run [configurators][com.intellij.platform.DirectoryProjectConfigurator] if [isNewProject] or has no modules.
               *
               *  **NB**: if project was [loaded from cache][com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl.loadedFromCache],
               *  but no serialized modules were found, configurators will be run regardless of [runConfigurators] value.
               *  See com.intellij.platform.PlatformProjectOpenProcessor.Companion.isLoadedFromCacheButHasNoModules
               */
              runConfigurators: Boolean = false,
              runConversionBeforeOpen: Boolean = true,
              projectWorkspaceId: String? = null,
              isProjectCreatedWithWizard: Boolean = false,
              preloadServices: Boolean = true,
              implOptions: Any? = null) : this(
    forceOpenInNewFrame = forceOpenInNewFrame,
    projectToClose = projectToClose,
    isNewProject = isNewProject,
    useDefaultProjectAsTemplate = useDefaultProjectAsTemplate,

    project = project,
    projectName = projectName,

    showWelcomeScreen = showWelcomeScreen,
    callback = callback,
    line = line,
    column = column,
    isRefreshVfsNeeded = isRefreshVfsNeeded,

    runConfigurators = runConfigurators,
    runConversionBeforeOpen = runConversionBeforeOpen,
    projectWorkspaceId = projectWorkspaceId,
    isProjectCreatedWithWizard = isProjectCreatedWithWizard,

    preloadServices = preloadServices,
    beforeInit = null,

    beforeOpen = null,
    preventIprLookup = false,
    preparedToOpen = null,
    processorChooser = null,

    implOptions = implOptions,
  )

  companion object {
    @JvmStatic
    fun build(): OpenProjectTask = OpenProjectTask()

    @JvmStatic
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use build(), withProjectToClose(), withForceOpenInNewFrame()", level = DeprecationLevel.ERROR)
    fun withProjectToClose(projectToClose: Project?, forceOpenInNewFrame: Boolean): OpenProjectTask {
      return OpenProjectTask(projectToClose = projectToClose, forceOpenInNewFrame = forceOpenInNewFrame)
    }
  }

  fun withForceOpenInNewFrame(forceOpenInNewFrame: Boolean) = copy(forceOpenInNewFrame = forceOpenInNewFrame)
  fun withProjectToClose(projectToClose: Project?) = copy(projectToClose = projectToClose)
  fun asNewProject() = copy(isNewProject = true, useDefaultProjectAsTemplate = true)
  fun withProject(project: Project?) = copy(project = project)
  fun withProjectName(projectName: String?) = copy(projectName = projectName)
  fun withRunConfigurators() = copy(runConfigurators = true)
  fun withoutVfsRefresh() = copy(isRefreshVfsNeeded = false)
  fun withCreatedByWizard() = copy(isProjectCreatedWithWizard = true)
}

class OpenProjectTaskBuilder internal constructor() {
  var projectName: String? = null

  var forceOpenInNewFrame: Boolean = false

  var isNewProject: Boolean = false
  var useDefaultProjectAsTemplate: Boolean = isNewProject
  var runConfigurators: Boolean = false
  var isProjectCreatedWithWizard: Boolean = false
  var runConversionBeforeOpen: Boolean = true
  var preventIprLookup: Boolean = false

  var projectToClose: Project? = null
  var isRefreshVfsNeeded: Boolean = true

  var beforeOpen: (suspend (Project) -> Boolean)? = null
  var beforeInit: ((Project) -> Unit)? = null
  var preparedToOpen: ((Module) -> Unit)? = null

  var showWelcomeScreen: Boolean = true

  fun withBeforeOpenCallback(callback: Predicate<Project>) {
    beforeOpen = { callback.test(it) }
  }

  @Internal
  var processorChooser: ((List<Any>) -> Any)? = null

  fun asNewProject() {
    isNewProject = true
    useDefaultProjectAsTemplate = true
  }

  internal inline fun build(builder: OpenProjectTaskBuilder.() -> Unit): OpenProjectTask {
    builder()
    return OpenProjectTask(
      forceOpenInNewFrame = forceOpenInNewFrame,

      projectToClose = projectToClose,
      isRefreshVfsNeeded = isRefreshVfsNeeded,

      projectName = projectName,
      isNewProject = isNewProject,
      useDefaultProjectAsTemplate = useDefaultProjectAsTemplate,
      runConfigurators = runConfigurators,
      isProjectCreatedWithWizard = isProjectCreatedWithWizard,
      runConversionBeforeOpen = runConversionBeforeOpen,
      showWelcomeScreen = showWelcomeScreen,

      beforeOpen = beforeOpen,
      beforeInit = beforeInit,
      preparedToOpen = preparedToOpen,

      preventIprLookup = preventIprLookup,
      processorChooser = processorChooser,

      implOptions = null,
    )
  }
}

fun OpenProjectTask(buildAction: OpenProjectTaskBuilder.() -> Unit): OpenProjectTask {
  return OpenProjectTaskBuilder().build(buildAction)
}
