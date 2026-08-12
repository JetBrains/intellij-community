// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.projectImport.ProjectOpenedCallback
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
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
  @Deprecated("Not used")
  val isRefreshVfsNeeded: Boolean,
  /**
   *  Whether to run [configurators][com.intellij.platform.DirectoryProjectConfigurator] if [isNewProject] or has no modules.
   *
   *  **NB**: if a project was [loaded from cache][com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl.loadedFromCache],
   *  but no serialized modules were found, configurators will be run regardless of [runConfigurators] value.
   *  See com.intellij.platform.PlatformProjectOpenProcessor.Companion.isLoadedFromCacheButHasNoModules
   */
  val runConfigurators: Boolean?,
  val runConversionBeforeOpen: Boolean,
  val projectWorkspaceId: String?,
  @JvmField @Internal val projectFrameTypeId: String?,
  val isProjectCreatedWithWizard: Boolean,
  @TestOnly
  val preloadServices: Boolean,
  val beforeInitTasks: List<((Project) -> Unit)>,
  /** Ignored if a project is explicitly set. */
  val beforeOpenTasks: List<(suspend (Project) -> Boolean)>,
  val preparedToOpenTasks: List<(suspend (Module) -> Unit)>,
  val preventIprLookup: Boolean,
  val processorChooser: ((List<Any>) -> Any)?,
  val implOptions: Any?,
  @ApiStatus.Experimental
  /**
   * Used to register [com.intellij.workspaceModel.ide.ProjectRootEntity] for this directory
   */
  val projectRootDir: Path?,
  @Internal
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
  @get:Internal
  val opensFileAfterProjectOpen: Boolean
    get() = (implOptions as? OpenProjectTaskImplOptions)?.opensFileAfterProjectOpen == true

  val beforeInit: ((Project) -> Unit)?
    get() = if (beforeInitTasks.isEmpty()) null
    else { project ->
      beforeInitTasks.forEach { task -> task(project) }
    }

  val beforeOpen: (suspend (Project) -> Boolean)?
    get() = if (beforeOpenTasks.isEmpty()) null
    else { project ->
      // iteration will stop on the first "false" result
      beforeOpenTasks.all { task -> task(project) }
    }

  val preparedToOpen: (suspend (Module) -> Unit)?
    get() = if (preparedToOpenTasks.isEmpty()) null
    else { module ->
      preparedToOpenTasks.forEach { task -> task(module) }
    }

  /**
   * Compatibility bridge for plugins compiled against builds where [opensFileAfterProjectOpen] was a primary-constructor parameter.
   */
  @Internal
  constructor(
    forceOpenInNewFrame: Boolean,
    forceReuseFrame: Boolean,
    projectToClose: Project?,
    isNewProject: Boolean,
    useDefaultProjectAsTemplate: Boolean,
    project: Project?,
    projectName: String?,
    showWelcomeScreen: Boolean,
    callback: ProjectOpenedCallback?,
    line: Int,
    column: Int,
    opensFileAfterProjectOpen: Boolean,
    isRefreshVfsNeeded: Boolean,
    runConfigurators: Boolean?,
    runConversionBeforeOpen: Boolean,
    projectWorkspaceId: String?,
    projectFrameTypeId: String?,
    isProjectCreatedWithWizard: Boolean,
    preloadServices: Boolean,
    beforeInit: ((Project) -> Unit)?,
    beforeOpen: (suspend (Project) -> Boolean)?,
    preparedToOpen: (suspend (Module) -> Unit)?,
    preventIprLookup: Boolean,
    processorChooser: ((List<Any>) -> Any)?,
    implOptions: Any?,
    projectRootDir: Path?,
    createModule: Boolean,
  ) : this(
    forceOpenInNewFrame = forceOpenInNewFrame,
    forceReuseFrame = forceReuseFrame,
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
    projectFrameTypeId = projectFrameTypeId,
    isProjectCreatedWithWizard = isProjectCreatedWithWizard,
    preloadServices = preloadServices,
    beforeInitTasks = if (beforeInit != null) listOf(beforeInit) else emptyList(),
    beforeOpenTasks = if (beforeOpen != null) listOf(beforeOpen) else emptyList(),
    preparedToOpenTasks = if (preparedToOpen != null) listOf(preparedToOpen) else emptyList(),
    preventIprLookup = preventIprLookup,
    processorChooser = processorChooser,
    implOptions = implOptions.withOpensFileAfterProjectOpen(opensFileAfterProjectOpen),
    projectRootDir = projectRootDir,
    createModule = createModule,
  )

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

    runConfigurators = null,
    runConversionBeforeOpen = true,
    projectWorkspaceId = null,
    projectFrameTypeId = null,
    isProjectCreatedWithWizard = false,

    preloadServices = true,
    beforeInitTasks = emptyList(),

    beforeOpenTasks = emptyList(),
    preventIprLookup = false,
    preparedToOpenTasks = emptyList(),
    processorChooser = null,

    implOptions = OpenProjectTaskImplOptions(delegate = null, opensFileAfterProjectOpen = false),
    createModule = true,

    projectRootDir = null,
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

  @Internal
  fun markAsOpeningFileAfterProjectOpen(): OpenProjectTask = copy(implOptions = implOptions.withOpensFileAfterProjectOpen(value = true))
}

@get:Internal
val OpenProjectTask.effectiveImplOptions: Any?
  get() = when (val options = implOptions) {
    is OpenProjectTaskImplOptions -> options.delegate
    else -> options
  }

@Internal
fun OpenProjectTask.withImplOptions(implOptions: Any?): OpenProjectTask {
  val options = this.implOptions.asOpenProjectTaskImplOptions().copy(delegate = implOptions)
  return copy(implOptions = options)
}

class OpenProjectTaskBuilder @PublishedApi internal constructor() {
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
  var runConfigurators: Boolean? = null
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
  var projectFrameTypeId: String? = null
  var implOptions: Any? = null

  var line: Int = -1
  var column: Int = -1

  /** See [OpenProjectTask.opensFileAfterProjectOpen]. */
  @Internal
  var opensFileAfterProjectOpen: Boolean = false

  /**  Shim for Java clients  */
  fun withBeforeOpenCallback(callback: Predicate<Project>) {
    beforeOpen = { callback.test(it) }
  }

  var projectRootDir: Path? = null

  @Internal
  var processorChooser: ((List<Any>) -> Any)? = null

  @Internal
  var createModule: Boolean = true

  var project: Project? = null
    set(value) {
      field = value
      createModule = false
    }

  @PublishedApi
  internal fun build(builder: OpenProjectTaskBuilder.() -> Unit): OpenProjectTask {
    builder()
    if (project != null && createModule) {
      thisLogger().warn("Project is explicitly set (name=${project?.name}), but createModule is true")
    }
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
      projectFrameTypeId = projectFrameTypeId,
      implOptions = implOptions,
      createModule = createModule,

      line = line,
      column = column,
      opensFileAfterProjectOpen = opensFileAfterProjectOpen,

      project = project,
      projectRootDir = projectRootDir,
    )
  }
}

@Internal
inline fun OpenProjectTask(buildAction: OpenProjectTaskBuilder.() -> Unit): OpenProjectTask {
  val builder = OpenProjectTaskBuilder()
  builder.buildAction()
  // Keep the pre-existing build(Function1) call in client bytecode, but do not inline the builder implementation into clients.
  return builder.build {}
}
