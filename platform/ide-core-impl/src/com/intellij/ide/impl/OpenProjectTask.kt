// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DeprecatedCallableAddReplaceWith")
package com.intellij.ide.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.projectImport.ProjectOpenedCallback
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.util.function.Predicate

private data class OpenProjectTaskImplOptions(
  @JvmField val delegate: Any?,
  @JvmField val opensFileAfterProjectOpen: Boolean,
)

private fun Any?.asOpenProjectTaskImplOptions(): OpenProjectTaskImplOptions {
  return this as? OpenProjectTaskImplOptions ?: OpenProjectTaskImplOptions(delegate = this, opensFileAfterProjectOpen = false)
}

private fun Any?.withOpensFileAfterProjectOpen(value: Boolean): OpenProjectTaskImplOptions {
  return asOpenProjectTaskImplOptions().copy(opensFileAfterProjectOpen = value)
}

/** Project creation options. Do not compose directly; use [OpenProjectTaskBuilder] instead. */
class OpenProjectTask @ApiStatus.Internal @Deprecated("Use `OpenProjectTask { ... }`") constructor(
  val forceOpenInNewFrame: Boolean,
  val forceReuseFrame: Boolean,
  val projectToClose: Project?,
  val isNewProject: Boolean,
  val useDefaultProjectAsTemplate: Boolean = isNewProject,
  val project: Project?,
  val projectName: String?,
  val showWelcomeScreen: Boolean,
  val callback: ProjectOpenedCallback?,
  val line: Int,
  val column: Int,
  @ApiStatus.Internal
  @Deprecated("Unused; kept for binary compatibility")
  val isRefreshVfsNeeded: Boolean,
  val runConfigurators: Boolean?,
  val runConversionBeforeOpen: Boolean,
  val projectWorkspaceId: String?,
  @ApiStatus.Internal
  val projectFrameTypeId: String?,
  val isProjectCreatedWithWizard: Boolean,
  @TestOnly
  val preloadServices: Boolean,
  @ApiStatus.Internal
  val beforeInitTasks: List<((Project) -> Unit)>,
  @ApiStatus.Internal
  val beforeOpenTasks: List<(suspend (Project) -> Boolean)>,
  private val preparedToOpenTasks: List<(suspend (Module) -> Unit)>,
  val preventIprLookup: Boolean,
  val processorChooser: ((List<Any>) -> Any)?,
  @ApiStatus.Internal
  val implOptions: Any?,
  /** Used to register [com.intellij.workspaceModel.ide.ProjectRootEntity] for this directory. */
  @ApiStatus.Experimental
  val projectRootDir: Path?,
  @ApiStatus.Internal
  val createModule: Boolean,
) {
  /**
   * Whether whoever opens this project is going to open an editor of its own once opening has finished — a file named on the command
   * line, for instance.
   *
   * The editor area holds back what it would otherwise show while project open is still deciding what goes there, and it can only
   * hold back what it knows about; this reports work that outlives project open itself.
   *
   * This property is deliberately not a primary-constructor parameter: adding one changes the generated data-class ABI.
   */
  @get:ApiStatus.Internal
  val opensFileAfterProjectOpen: Boolean
    get() = (implOptions as? OpenProjectTaskImplOptions)?.opensFileAfterProjectOpen == true

  @get:ApiStatus.Internal
  val beforeInit: ((Project) -> Unit)?
    get() = beforeInitTasks.takeIf { it.isNotEmpty() }?.let {
      { project -> it.forEach { task -> task(project) } }
    }

  @get:ApiStatus.Internal
  val beforeOpen: (suspend (Project) -> Boolean)?
    get() = beforeOpenTasks.takeIf { it.isNotEmpty() }?.let {
      { project -> it.all { task -> task(project) } }
    }

  @get:ApiStatus.Internal
  val preparedToOpen: (suspend (Module) -> Unit)?
    get() = preparedToOpenTasks.firstOrNull()?.let {
      task -> { module -> task(module) }
    }

  @Deprecated("Use `OpenProjectTask { ... }`")
  @ApiStatus.Internal
  @Suppress("DEPRECATION")
  constructor(
    forceOpenInNewFrame: Boolean = false,
    projectToClose: Project? = null,
    isNewProject: Boolean = false,
    /** Ignored if [isNewProject] is set to false. */
    useDefaultProjectAsTemplate: Boolean = isNewProject,
  ) : this(
    forceOpenInNewFrame, forceReuseFrame = false, projectToClose, isNewProject, useDefaultProjectAsTemplate,
    project = null, projectName = null, showWelcomeScreen = true, callback = null, line = -1, column = -1, isRefreshVfsNeeded = false,
    runConfigurators = null, runConversionBeforeOpen = true, projectWorkspaceId = null, projectFrameTypeId = null,
    isProjectCreatedWithWizard = false, preloadServices = true, beforeInitTasks = emptyList(), beforeOpenTasks = emptyList(),
    preventIprLookup = false, preparedToOpenTasks = emptyList(), processorChooser = null,
    implOptions = OpenProjectTaskImplOptions(delegate = null, opensFileAfterProjectOpen = false),
    createModule = true, projectRootDir = null
  )

  @Deprecated("Use `OpenProjectTask { ... }`")
  @Suppress("DEPRECATION")
  fun copy(
    forceOpenInNewFrame: Boolean = this.forceOpenInNewFrame,
    forceReuseFrame: Boolean = this.forceReuseFrame,
    projectToClose: Project? = this.projectToClose,
    isNewProject: Boolean = this.isNewProject,
    useDefaultProjectAsTemplate: Boolean = this.useDefaultProjectAsTemplate,
    project: Project? = this.project,
    projectName: String? = this.projectName,
    showWelcomeScreen: Boolean = this.showWelcomeScreen,
    callback: ProjectOpenedCallback? = this.callback,
    line: Int = this.line,
    column: Int = this.column,
    isRefreshVfsNeeded: Boolean = this.isRefreshVfsNeeded,
    runConfigurators: Boolean? = this.runConfigurators,
    runConversionBeforeOpen: Boolean = this.runConversionBeforeOpen,
    projectWorkspaceId: String? = this.projectWorkspaceId,
    projectFrameTypeId: String? = this.projectFrameTypeId,
    isProjectCreatedWithWizard: Boolean = this.isProjectCreatedWithWizard,
    preloadServices: Boolean = this.preloadServices,
    beforeInitTasks: List<((Project) -> Unit)> = this.beforeInitTasks,
    beforeOpenTasks: List<(suspend (Project) -> Boolean)> = this.beforeOpenTasks,
    preparedToOpenTasks: List<(suspend (Module) -> Unit)> = this.preparedToOpenTasks,
    preventIprLookup: Boolean = this.preventIprLookup,
    processorChooser: ((List<Any>) -> Any)? = this.processorChooser,
    implOptions: Any? = this.implOptions,
    projectRootDir: Path? = this.projectRootDir,
    createModule: Boolean = this.createModule,
  ): OpenProjectTask = OpenProjectTask(
    forceOpenInNewFrame, forceReuseFrame, projectToClose, isNewProject, useDefaultProjectAsTemplate, project, projectName,
    showWelcomeScreen, callback, line, column, isRefreshVfsNeeded, runConfigurators, runConversionBeforeOpen, projectWorkspaceId,
    projectFrameTypeId, isProjectCreatedWithWizard, preloadServices, beforeInitTasks, beforeOpenTasks, preparedToOpenTasks,
    preventIprLookup, processorChooser, implOptions, projectRootDir, createModule
  )

  companion object {
    @Deprecated("Use `OpenProjectTask { ... }`")
    @JvmStatic
    fun build(): OpenProjectTask = OpenProjectTask {}
  }

  @Deprecated("Use `OpenProjectTask { ... }`")
  @Suppress("DEPRECATION")
  fun withForceOpenInNewFrame(forceOpenInNewFrame: Boolean): OpenProjectTask = copy(forceOpenInNewFrame = forceOpenInNewFrame)

  @Deprecated("Use `OpenProjectTask { ... }`")
  @Suppress("DEPRECATION")
  fun withProjectToClose(projectToClose: Project?): OpenProjectTask = copy(projectToClose = projectToClose)

  @Deprecated("Use `OpenProjectTask { ... }`")
  @Suppress("DEPRECATION")
  fun asNewProject(): OpenProjectTask = copy(isNewProject = true, useDefaultProjectAsTemplate = true)

  @Suppress("DEPRECATION")
  fun withProject(project: Project?): OpenProjectTask = copy(project = project)

  @Deprecated("Use `OpenProjectTask { ... }`")
  @Suppress("DEPRECATION")
  fun withProjectName(projectName: String?): OpenProjectTask = copy(projectName = projectName)

  @Deprecated("Use `OpenProjectTaskBuilder` or `OpenProjectTask { ... }`")
  @Suppress("DEPRECATION")
  fun withProjectRootDir(projectRootDir: Path?): OpenProjectTask = copy(projectRootDir = projectRootDir)

  @ApiStatus.Internal
  @Suppress("DEPRECATION")
  fun markAsOpeningFileAfterProjectOpen(): OpenProjectTask = copy(implOptions = implOptions.withOpensFileAfterProjectOpen(value = true))

  @ApiStatus.Internal
  @TestOnly
  @Suppress("DEPRECATION")
  fun prepareForTests(asNewProject: Boolean): OpenProjectTask = copy(preloadServices = false, isNewProject = asNewProject)
}

@get:ApiStatus.Internal
val OpenProjectTask.effectiveImplOptions: Any?
  get() = when (val options = implOptions) {
    is OpenProjectTaskImplOptions -> options.delegate
    else -> options
  }

@ApiStatus.Internal
fun OpenProjectTask.withImplOptions(implOptions: Any?): OpenProjectTask {
  val options = this.implOptions.asOpenProjectTaskImplOptions().copy(delegate = implOptions)
  @Suppress("DEPRECATION")
  return copy(implOptions = options)
}

class OpenProjectTaskBuilder @PublishedApi internal constructor() {
  var projectName: String? = null

  var forceOpenInNewFrame: Boolean = false
  var forceReuseFrame: Boolean = false

  var isNewProject: Boolean = false
  /** Ignored if [isNewProject] is set to `false`. */
  var useDefaultProjectAsTemplate: Boolean? = null

  /**
   *  Whether to run configurators (`DirectoryProjectConfigurator` instances) if [isNewProject] or has no modules.
   *
   *  **NB**: if a project was [loaded from cache][com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl.loadedFromCache],
   *  but no serialized modules were found, configurators will be run regardless of [runConfigurators] value.
   *  See com.intellij.platform.PlatformProjectOpenProcessor.Companion.isLoadedFromCacheButHasNoModules
   */
  var runConfigurators: Boolean? = null
  var preloadServices: Boolean = true

  var isProjectCreatedWithWizard: Boolean = false
  var runConversionBeforeOpen: Boolean = true
  var preventIprLookup: Boolean = false

  var projectToClose: Project? = null
  var isRefreshVfsNeeded: Boolean = true

  @ApiStatus.Internal
  var beforeInitTasks: List<((Project) -> Unit)> = emptyList()

  @Deprecated("Use `beforeInitTasks` instead")
  @ApiStatus.Internal
  var beforeInit: ((Project) -> Unit)? = null

  /** Ignored if a [project] is explicitly set. */
  @ApiStatus.Internal
  var beforeOpenTasks: List<(suspend (Project) -> Boolean)> = emptyList()

  @Deprecated("Use `beforeOpenTasks` instead")
  @ApiStatus.Internal
  var beforeOpen: (suspend (Project) -> Boolean)? = null

  var preparedToOpen: (suspend (Module) -> Unit)? = null

  var callback: ProjectOpenedCallback? = null

  /** Whether to show the welcome screen if failed to open a project. */
  var showWelcomeScreen: Boolean = true

  var projectWorkspaceId: String? = null
  var projectFrameTypeId: String? = null
  var implOptions: Any? = null

  var line: Int = -1
  var column: Int = -1

  /** See [OpenProjectTask.opensFileAfterProjectOpen]. */
  @ApiStatus.Internal
  var opensFileAfterProjectOpen: Boolean = false

  /** A shim for Java clients. */
  fun withBeforeOpenCallback(callback: Predicate<Project>) {
    beforeOpenTasks += { callback.test(it) }
  }

  var projectRootDir: Path? = null

  @ApiStatus.Internal
  var processorChooser: ((List<Any>) -> Any)? = null

  @ApiStatus.Internal
  var createModule: Boolean = true

  /** When you just need to open an already created and prepared project; used e.g., by the "new project" action. */
  var project: Project? = null
    set(value) {
      field = value
      createModule = false
    }

  @Deprecated("Pass the builder to `OpenProjectTask` or use `apply { ... }`", level = DeprecationLevel.ERROR)
  @PublishedApi
  internal fun build(builder: OpenProjectTaskBuilder.() -> Unit): OpenProjectTask = apply { builder() }.build()

  @PublishedApi
  internal fun build(): OpenProjectTask {
    if (project != null && createModule) {
      thisLogger().warn("Project is explicitly set (name=${project?.name}), but createModule is true")
    }
    @Suppress("DEPRECATION")
    return OpenProjectTask(
      forceOpenInNewFrame, forceReuseFrame, projectToClose, isNewProject,
      useDefaultProjectAsTemplate = useDefaultProjectAsTemplate ?: isNewProject,
      project, projectName, showWelcomeScreen, callback, line, column,
      isRefreshVfsNeeded = false,
      runConfigurators, runConversionBeforeOpen, projectWorkspaceId,
      projectFrameTypeId, isProjectCreatedWithWizard, preloadServices,
      beforeInitTasks = beforeInitTasks + listOfNotNull(beforeInit),
      beforeOpenTasks = beforeOpenTasks + listOfNotNull(beforeOpen),
      preparedToOpenTasks = listOfNotNull(preparedToOpen),
      preventIprLookup, processorChooser,
      implOptions = implOptions.withOpensFileAfterProjectOpen(opensFileAfterProjectOpen),
      projectRootDir, createModule
    )
  }
}

inline fun OpenProjectTask(buildAction: OpenProjectTaskBuilder.() -> Unit): OpenProjectTask =
  OpenProjectTaskBuilder().apply { buildAction() }.build()
