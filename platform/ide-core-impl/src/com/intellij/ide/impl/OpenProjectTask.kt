// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.projectImport.ProjectOpenedCallback
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.function.Predicate

data class OpenProjectTask @Internal constructor(
  val forceOpenInNewFrame: Boolean,
  val forceReuseFrame: Boolean = false,
  val projectToClose: Project?,
  val isNewProject: Boolean = false,
  /** Ignored if [isNewProject] is set to false. */
  val useDefaultProjectAsTemplate: Boolean = isNewProject,
  /** When you just need to open an already created and prepared project; used e.g., by the "new project" action. */
  val project: Project?,
  val projectName: String?,
  /** Whether to show welcome screen if failed to open a project. */
  val showWelcomeScreen: Boolean,
  val callback: ProjectOpenedCallback?,
  val line: Int,
  val column: Int,
  val isRefreshVfsNeeded: Boolean,
  /**
   *  Whether to run [configurators][com.intellij.platform.DirectoryProjectConfigurator] if [isNewProject] or has no modules.
   *
   *  **NB**: if a project was [loaded from cache][com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl.loadedFromCache],
   *  but no serialized modules were found, configurators will be run regardless of [runConfigurators] value.
   *  See com.intellij.platform.PlatformProjectOpenProcessor.Companion.isLoadedFromCacheButHasNoModules
   */
  val runConfigurators: Boolean,
  val runConversionBeforeOpen: Boolean,
  val projectWorkspaceId: String?,
  val isProjectCreatedWithWizard: Boolean,
  @TestOnly
  val preloadServices: Boolean,
  val beforeInit: ((Project) -> Unit)?,
  /** Ignored if a project is explicitly set. */
  val beforeOpen: (suspend (Project) -> Boolean)?,
  val preparedToOpen: (suspend (Module) -> Unit)?,
  val preventIprLookup: Boolean,
  val processorChooser: ((List<Any>) -> Any)?,
  val implOptions: Any?,
) {
  @Internal
  constructor(
    forceOpenInNewFrame: Boolean = false,
    projectToClose: Project? = null,
    isNewProject: Boolean = false,
    /** Ignored if [isNewProject] is set to false. */
    useDefaultProjectAsTemplate: Boolean = isNewProject,
  ) : this(
    forceOpenInNewFrame = forceOpenInNewFrame,
    projectToClose = projectToClose,
    isNewProject = isNewProject,
    useDefaultProjectAsTemplate = useDefaultProjectAsTemplate,

    project = null,
    projectName = null,

    showWelcomeScreen = true,
    callback = null,
    line = -1,
    column = -1,
    isRefreshVfsNeeded = true,

    runConfigurators = false,
    runConversionBeforeOpen = true,
    projectWorkspaceId = null,
    isProjectCreatedWithWizard = false,

    preloadServices = true,
    beforeInit = null,

    beforeOpen = null,
    preventIprLookup = false,
    preparedToOpen = null,
    processorChooser = null,

    implOptions = null,
  )

  companion object {
    @JvmStatic
    fun build(): OpenProjectTask = OpenProjectTask()
  }

  fun withForceOpenInNewFrame(forceOpenInNewFrame: Boolean): OpenProjectTask = copy(forceOpenInNewFrame = forceOpenInNewFrame)
  fun withProjectToClose(projectToClose: Project?): OpenProjectTask = copy(projectToClose = projectToClose)
  fun asNewProject(): OpenProjectTask = copy(isNewProject = true, useDefaultProjectAsTemplate = true)
  fun withProject(project: Project?): OpenProjectTask = copy(project = project)
  fun withProjectName(projectName: String?): OpenProjectTask = copy(projectName = projectName)
}

class OpenProjectTaskBuilder internal constructor() {
  var projectName: String? = null

  var forceOpenInNewFrame: Boolean = false
  var forceReuseFrame: Boolean = false

  var isNewProject: Boolean = false
  var useDefaultProjectAsTemplate: Boolean? = null

  /**
   *  Whether to run [configurators][com.intellij.platform.DirectoryProjectConfigurator] if [isNewProject] or has no modules.
   *
   *  **NB**: if a project was [loaded from cache][com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl.loadedFromCache],
   *  but no serialized modules were found, configurators will be run regardless of [runConfigurators] value.
   *  See com.intellij.platform.PlatformProjectOpenProcessor.Companion.isLoadedFromCacheButHasNoModules
   */
  var runConfigurators: Boolean = false
  var preloadServices: Boolean = true

  var isProjectCreatedWithWizard: Boolean = false
  var runConversionBeforeOpen: Boolean = true
  var preventIprLookup: Boolean = false

  var projectToClose: Project? = null
  var isRefreshVfsNeeded: Boolean = true

  @Internal
  var beforeOpen: (suspend (Project) -> Boolean)? = null

  @Internal
  var beforeInit: ((Project) -> Unit)? = null
  var preparedToOpen: (suspend (Module) -> Unit)? = null
  var callback: ProjectOpenedCallback? = null

  var showWelcomeScreen: Boolean = true

  var projectWorkspaceId: String? = null
  var implOptions: Any? = null

  var line: Int = -1
  var column: Int = -1

  /**  Shim for Java clients  */
  fun withBeforeOpenCallback(callback: Predicate<Project>) {
    beforeOpen = { callback.test(it) }
  }

  @Internal
  var processorChooser: ((List<Any>) -> Any)? = null

  var project: Project? = null

  internal inline fun build(builder: OpenProjectTaskBuilder.() -> Unit): OpenProjectTask {
    builder()
    return OpenProjectTask(
      forceOpenInNewFrame = forceOpenInNewFrame,
      forceReuseFrame = forceReuseFrame,
      preloadServices = preloadServices,

      projectToClose = projectToClose,
      isRefreshVfsNeeded = isRefreshVfsNeeded,

      projectName = projectName,
      isNewProject = isNewProject,
      useDefaultProjectAsTemplate = useDefaultProjectAsTemplate ?: isNewProject,
      runConfigurators = runConfigurators,
      isProjectCreatedWithWizard = isProjectCreatedWithWizard,
      runConversionBeforeOpen = runConversionBeforeOpen,
      showWelcomeScreen = showWelcomeScreen,

      beforeOpen = beforeOpen,
      beforeInit = beforeInit,
      preparedToOpen = preparedToOpen,
      callback = callback,

      preventIprLookup = preventIprLookup,
      processorChooser = processorChooser,

      projectWorkspaceId = projectWorkspaceId,
      implOptions = implOptions,

      line = line,
      column = column,

      project = project,
    )
  }
}

@Internal
fun OpenProjectTask(buildAction: OpenProjectTaskBuilder.() -> Unit): OpenProjectTask {
  return OpenProjectTaskBuilder().build(buildAction)
}
