// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "OVERRIDE_DEPRECATION", "LiftReturnOrAssignment")

package com.intellij.ide

import com.intellij.diagnostic.runActivity
import com.intellij.ide.RecentProjectsManager.RecentProjectsChange
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.ProjectUtil.isSameProject
import com.intellij.ide.impl.ProjectUtilService
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.vcs.RecentProjectsBranchesProvider
import com.intellij.idea.AppMode
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectStorePathManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.createIdeFrame
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.eel.provider.EelInitialization
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.project.stateStore
import com.intellij.ui.mac.createMacDelegate
import com.intellij.ui.win.createWinDockDelegate
import com.intellij.util.PathUtilRt
import com.intellij.util.PlatformUtils
import com.intellij.util.application
import com.intellij.util.io.createParentDirectories
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.LongAdder
import javax.swing.Icon
import javax.swing.JFrame
import kotlin.collections.Map.Entry
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<RecentProjectsManager>()

/**
 * Used directly by IntelliJ IDEA.
 */
@OptIn(FlowPreview::class)
@State(name = "RecentProjectsManager",
       category = SettingsCategory.SYSTEM,
       storages = [Storage(value = "recentProjects.xml", roamingType = RoamingType.DISABLED)])
open class RecentProjectsManagerBase(coroutineScope: CoroutineScope) :
  RecentProjectsManager, PersistentStateComponent<RecentProjectManagerState>, ModificationTracker {
  companion object {
    const val MAX_PROJECTS_IN_MAIN_MENU: Int = 6

    @JvmStatic
    fun getInstanceEx(): RecentProjectsManagerBase = RecentProjectsManager.getInstance() as RecentProjectsManagerBase

    @JvmName("isFileSystemPath")
    internal fun isFileSystemPath(path: String): Boolean = path.indexOf('/') != -1 || path.indexOf('\\') != -1
  }

  private val modCounter = LongAdder()
  private val projectIconHelper by lazy(::RecentProjectIconHelper)
  private val namesToResolve = HashSet<String>(MAX_PROJECTS_IN_MAIN_MENU)

  private val nameCache: MutableMap<String, String> = Collections.synchronizedMap(HashMap())

  private val disableUpdatingRecentInfo = AtomicBoolean()

  private val nameResolveRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val updateDockRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val stateLock = Any()
  private var state = RecentProjectManagerState()

  init {
    coroutineScope.launch {
      nameResolveRequests
        .debounce(50.milliseconds)
        .collectLatest {
          val paths = synchronized(namesToResolve) {
            val paths = namesToResolve.toList()
            namesToResolve.clear()
            paths
          }
          for (p in paths) {
            nameCache.put(p, readProjectName(p))
          }
        }
    }

    if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
      coroutineScope.launch {
        val delegate = when {
                         SystemInfoRt.isMac -> createMacDelegate()
                         SystemInfoRt.isWindows -> createWinDockDelegate()
                         else -> null
                       } ?: return@launch

        updateDockRequests
          .debounce(50.milliseconds)
          .collectLatest {
            runActivity("system dock menu") {
              runCatching {
                delegate.updateRecentProjectsMenu()
              }.getOrLogException(LOG)
            }
          }
      }
    }

    application.messageBus.connect().subscribe(RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC, object : RecentProjectsChange {
      override fun change() {
        updateSystemDockMenu()
      }
    })
  }

  private fun updateSystemDockMenu() {
    check(updateDockRequests.tryEmit(Unit))
  }

  final override fun getState(): RecentProjectManagerState = state

  @Internal
  fun getProjectMetaInfo(projectStoreBaseDir: Path): RecentProjectMetaInfo? {
    val path = getProjectPath(projectStoreBaseDir) ?: return null
    return getProjectMetaInfo(path)
  }

  @Internal
  fun getProjectMetaInfo(path: String): RecentProjectMetaInfo? {
    synchronized(stateLock) {
      return state.additionalInfo.get(path)
    }
  }

  /**
   * Fixes recent project paths after they were modified by Monolith IDE.
   * See IJPL-179484 for details.
   */
  private fun transformFrontendPathIfNeeded(key: String): String {
    LOG.runAndLogException {
      val configDir = PathManager.getConfigDir()
      val originalConfigDir = PathManager.getOriginalConfigDir()

      val projectDir = key.toNioPathOrNull() ?: return key
      if (projectDir.exists()) return key
      if (!projectDir.startsWith(configDir)) return key

      // `projectDir` is a valid path, leads to a non-existing location, inside the config folder
      val relativeProjectDir = projectDir.relativeTo(configDir)
      val fixedProjectDir = originalConfigDir.resolve(relativeProjectDir)
      return FileUtilRt.toSystemIndependentName(fixedProjectDir.toString())
    }
    return key
  }

  final override fun loadState(state: RecentProjectManagerState) {
    synchronized(stateLock) {
      this.state = state
      state.pid = null

      for ((path, value) in state.additionalInfo) {
        checkForNonsenseBounds("com.intellij.ide.RecentProjectsManagerBase.loadState(path=$path)", value.frame?.bounds)
      }

      // Remote Development: Restore correct paths after monolith settings save
      if (PlatformUtils.isJetBrainsClient()) {
        val newAdditionalInfo = linkedMapOf<String, RecentProjectMetaInfo>()
        state.additionalInfo.forEach { key, value ->
          newAdditionalInfo.put(transformFrontendPathIfNeeded(key), value)
        }

        if (newAdditionalInfo != state.additionalInfo) {
          state.additionalInfo.clear()
          state.additionalInfo.putAll(newAdditionalInfo)
        }
      }

      // IDEA <= 2019.2 doesn't delete project info from additionalInfo on project delete
      @Suppress("DEPRECATION")
      val recentPaths = state.recentPaths
      if (recentPaths.isNotEmpty()) {
        convertToSystemIndependentPaths(recentPaths)

        // replace system-dependent paths to system-independent
        for (key in state.additionalInfo.keys.toList()) {
          val normalizedKey = FileUtilRt.toSystemIndependentName(key)
          if (normalizedKey != key) {
            state.additionalInfo.remove(key)?.let {
              state.additionalInfo.put(normalizedKey, it)
            }
          }
        }

        // ensure that additionalInfo contains entries in a reversed order of recentPaths (IDEA <= 2019.2 order of additionalInfo maybe not correct)
        val newAdditionalInfo = linkedMapOf<String, RecentProjectMetaInfo>()
        for (recentPath in recentPaths.asReversed()) {
          val value = state.additionalInfo.get(recentPath) ?: continue
          newAdditionalInfo.put(recentPath, value)
        }

        if (newAdditionalInfo != state.additionalInfo) {
          state.additionalInfo.clear()
          state.additionalInfo.putAll(newAdditionalInfo)
        }

        @Suppress("DEPRECATION")
        state.recentPaths.clear()
      }
    }

    updateSystemDockMenu()
  }

  override fun removePath(path: String) {
    synchronized(stateLock) {
      if (state.additionalInfo.remove(path) != null) {
        modCounter.increment()
      }
      for (group in state.groups) {
        if (group.removeProject(path)) {
          modCounter.increment()
        }
      }
      fireChangeEvent()
    }
  }

  override fun hasPath(path: String?): Boolean {
    synchronized(stateLock) {
      return state.additionalInfo.containsKey(path)
    }
  }


  override var lastProjectCreationLocation: String?
    get() {
      synchronized(stateLock) {
        return state.lastProjectLocation
      }
    }
    set(value) {
      val newValue = value?.takeIf { it.isNotBlank() }?.let { FileUtilRt.toSystemIndependentName(it) }
      synchronized(stateLock) {
        state.lastProjectLocation = newValue
      }
    }

  override fun updateLastProjectPath() {
    if (PlatformUtils.isJetBrainsClient()) {
      LOG.info("Skipping last project path update for thin client")
      return
    }
    val openProjects = ProjectManagerEx.getOpenProjects()
    synchronized(stateLock) {
      for (info in state.additionalInfo.values) {
        info.opened = false
      }

      for (project in openProjects) {
        val path = getProjectPath(project) ?: continue
        val info = state.additionalInfo.get(path) ?: continue
        info.opened = true
        info.displayName = getProjectDisplayName(project)
      }
      modCounter.increment()
    }
  }

  protected open fun getProjectDisplayName(project: Project): String? = null

  fun getProjectIcon(path: String, isProjectValid: Boolean): Icon {
    return projectIconHelper.getProjectIcon(path, isProjectValid)
  }

  fun getProjectIcon(path: String, isProjectValid: Boolean, unscaledIconSize: Int, name: String? = null): Icon {
    return projectIconHelper.getProjectIcon(path, isProjectValid, unscaledIconSize, name)
  }

  fun getNonLocalProjectIcon(id: String, isProjectValid: Boolean, unscaledIconSize: Int, name: String?): Icon {
    return projectIconHelper.getNonLocalProjectIcon(id = id, isProjectValid = isProjectValid, iconSize = unscaledIconSize, name = name)
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun getRecentProjectsActions(addClearListItem: Boolean): Array<AnAction> {
    return RecentProjectListActionProvider.getInstance().getActions(addClearListItem = addClearListItem).toTypedArray()
  }

  fun markPathRecent(path: String, project: Project): RecentProjectMetaInfo {
    synchronized(stateLock) {
      for (group in state.groups) {
        if (group.markProjectFirst(path)) {
          modCounter.increment()
          break
        }
      }

      // remove instead of get to re-order
      val info = state.additionalInfo.remove(path) ?: RecentProjectMetaInfo()
      state.additionalInfo.put(path, info)
      modCounter.increment()

      val appInfo = ApplicationInfoEx.getInstanceEx()
      info.displayName = getProjectDisplayName(project)
      info.projectWorkspaceId = project.stateStore.projectWorkspaceId
      ProjectFrameBounds.getInstance(project).frameInfoHelper.info?.let {
        info.frame = it
      }
      info.build = appInfo!!.build.asString()
      info.productionCode = appInfo.build.productCode
      info.eap = appInfo.isEAP
      info.binFolder = FileUtilRt.toSystemIndependentName(PathManager.getBinPath())
      info.projectOpenTimestamp = System.currentTimeMillis()
      info.buildTimestamp = appInfo.buildDate.timeInMillis
      info.metadata = getRecentProjectMetadata(path, project)
      return info
    }
  }

  /**
   * No-op if [path] is already present in recent projects
   */
  fun addRecentPath(path: String, info: RecentProjectMetaInfo) {
    synchronized(stateLock) {
      val presentInfo = state.additionalInfo.putIfAbsent(path, info)
      if (presentInfo == null) modCounter.increment()
    }
  }

  fun updateRecentMetadata(project: Project, metaInfoUpdater: RecentProjectMetaInfo.() -> Unit) {
    val projectPath = getProjectPath(project) ?: return
    synchronized(stateLock) {
      val currentProjectMetaInfo = state.additionalInfo.get(projectPath) ?: return
      metaInfoUpdater(currentProjectMetaInfo)
    }
  }

  protected open fun getRecentProjectMetadata(path: String, project: Project): String? = null

  open fun getProjectPath(projectStoreBaseDir: Path): String? {
    return projectStoreBaseDir.invariantSeparatorsPathString
  }

  open fun getProjectPath(project: Project): String? {
    return FileUtilRt.toSystemIndependentName(project.presentableUrl ?: return null)
  }

  @TestOnly
  fun openProjectSync(projectFile: Path, openProjectOptions: OpenProjectTask): Project? {
    return runBlockingMaybeCancellable { openProject(projectFile, openProjectOptions) }
  }

  open suspend fun openProject(projectFile: Path, options: OpenProjectTask): Project? {
    var effectiveOptions = options
    if (options.implOptions == null) {
      getProjectMetaInfo(projectFile)?.let { info ->
        effectiveOptions = effectiveOptions.copy(
          projectWorkspaceId = info.projectWorkspaceId,
          implOptions = OpenProjectImplOptions(recentProjectMetaInfo = info, frameInfo = info.frame)
        )
      }
    }

    return FUSProjectHotStartUpMeasurer.withProjectContextElement(projectFile) {
      openProjectWithEffectiveOptions(projectFile, effectiveOptions)
    }
  }

  private suspend fun openProjectWithEffectiveOptions(
    projectFile: Path,
    effectiveOptions: OpenProjectTask,
  ): Project? {
    if (ProjectUtil.isValidProjectPath(projectFile)) {
      val projectManager = ProjectManagerEx.getInstanceEx()
      projectManager.openProjects.firstOrNull { isSameProject(projectFile = projectFile, project = it) }?.let { project ->
        FUSProjectHotStartUpMeasurer.reportAlreadyOpenedProject()
        withContext(Dispatchers.EDT) {
          ProjectUtilService.getInstance(project).focusProjectWindow()
        }
        return project
      }
      return projectManager.openProjectAsync(projectFile, effectiveOptions)
    }
    else {
      // If .idea is missing in the recent project's dir, this might mean, for instance, that 'git clean' was called.
      // Reopening such a project should be similar to opening the dir first time (and trying to import known project formats)
      // IDEA-144453 IDEA rejects opening a recent project if there are no .idea subfolder
      // CPP-12106 Auto-load CMakeLists.txt on opening from Recent projects when .idea and cmake-build-debug was deleted
      LOG.info("Opening project from the recent projects, but .idea is missing. Open project as this is first time.")
      return ProjectUtil.openOrImportAsync(projectFile, effectiveOptions)
    }
  }

  override fun setActivationTimestamp(project: Project, timestamp: Long) {
    if (disableUpdatingRecentInfo.get()) {
      return
    }

    val projectPath = getProjectPath(project) ?: return
    synchronized(stateLock) {
      findAndRemoveNewlyClonedProject(projectPath)
      val info = state.additionalInfo.computeIfAbsent(projectPath) { RecentProjectMetaInfo() }
      info.activationTimestamp = timestamp
      info.opened = true
      info.displayName = getProjectDisplayName(project)
      state.lastOpenedProject = projectPath
    }
    modCounter.increment()
  }

  fun getLastOpenedProject(): String? = state.lastOpenedProject

  @Internal
  class MyFrameStateListener : FrameStateListener {
    override fun onFrameActivated(frame: IdeFrame): Unit = frame.notifyProjectActivation()
  }

  @Internal
  fun projectOpened(project: Project) {
    projectOpened(project, System.currentTimeMillis())
  }

  internal fun projectOpened(project: Project, openTimestamp: Long) {
    if (disableUpdatingRecentInfo.get() || LightEdit.owns(project)) {
      return
    }

    val projectPath = getProjectPath(project) ?: return
    synchronized(stateLock) {
      findAndRemoveNewlyClonedProject(projectPath)
      val info = markPathRecent(path = projectPath, project = project)
      info.opened = true
      info.displayName = getProjectDisplayName(project)
      info.projectOpenTimestamp = openTimestamp

      state.lastOpenedProject = projectPath
      validateRecentProjects(modCounter, state.additionalInfo)
    }

    updateSystemDockMenu()
  }

  @Internal
  @VisibleForTesting
  class MyProjectListener : ProjectCloseListener {
    override fun projectClosingBeforeSave(project: Project) {
      val app = ApplicationManagerEx.getApplicationEx()
      if (app.isExitInProgress) {
        // `appClosing` handler updates project info (even more - on `projectClosed` the full-screen state maybe not correct)
        return
      }

      val manager = getInstanceEx()
      val path = manager.getProjectPath(project) ?: return
      if (!app.isHeadlessEnvironment) {
        manager.updateProjectInfo(project = project,
                                  windowManager = WindowManager.getInstance() as WindowManagerImpl,
                                  writeLastProjectInfo = false,
                                  appClosing = false)
      }
      manager.nameCache.put(path, project.name)
    }

    override fun projectClosed(project: Project) {
      if (ApplicationManagerEx.getApplicationEx().isExitInProgress) {
        // `appClosing` handler updates project info (even more - on `projectClosed` the full-screen state maybe not correct)
        return
      }

      val manager = getInstanceEx()
      synchronized(manager.stateLock) {
        manager.state.additionalInfo.get(manager.getProjectPath(project))?.opened = false
      }
      manager.updateSystemDockMenu()
    }
  }

  fun getRecentPaths(): List<String> {
    synchronized(stateLock) {
      validateRecentProjects(modCounter, state.additionalInfo)
      return state.additionalInfo.filter { !it.value.hidden }.keys.reversed()
    }
  }

  fun getDisplayName(path: String): String? {
    synchronized(stateLock) {
      return state.additionalInfo.get(path)?.displayName
    }
  }

  fun getActivationTimestamp(path: String): Long? {
    synchronized(stateLock) {
      return state.additionalInfo.get(path)?.activationTimestamp
    }
  }

  fun getCurrentBranch(path: String, nameIsDistinct: Boolean): String? {
    return RecentProjectsBranchesProvider.getCurrentBranch(path, nameIsDistinct)
  }

  fun getProjectName(path: String): String {
    nameCache.get(path)?.let {
      return it
    }

    synchronized(namesToResolve) {
      namesToResolve.add(path)
    }
    check(nameResolveRequests.tryEmit(Unit))

    return getProjectNameOnlyByPath(path)
  }

  fun forceReopenProjects() {
    synchronized(stateLock) {
      state.forceReopenProjects = true
    }
  }

  override suspend fun willReopenProjectOnStart(): Boolean {
    if (!synchronized(stateLock) { state.forceReopenProjects }
        && (!serviceAsync<GeneralSettings>().isReopenLastProject || AppMode.isDontReopenProjects())) {
      return false
    }

    synchronized(stateLock) {
      // FIXME do we really want to make this method non-idempotent?
      state.forceReopenProjects = false
      return state.additionalInfo.values.any { canReopenProject(it) }
    }
  }

  override suspend fun reopenLastProjectsOnStart(): Boolean {
    // Do not reopen, because previously opened projects will open in new instances
    // TODO alternative behaviour?
    if (ProjectManagerEx.IS_PER_PROJECT_INSTANCE_ENABLED) {
      return false
    }

    val openPaths = synchronized(stateLock) {
      state.additionalInfo.entries.filter { canReopenProject(it.value) }
    }
    if (openPaths.isEmpty()) {
      return false
    }

    disableUpdatingRecentInfo.set(true)
    try {
      if (openPaths.size == 1 || isOpenProjectsOneByOneRequired()) {
        FUSProjectHotStartUpMeasurer.reportReopeningProjects(openPaths)
        return openOneByOne(openPaths, index = 0, someProjectWasOpened = false)
      }

      val toOpen = openPaths.mapNotNull { entry ->
        runCatching {
          val path = Path.of(entry.key)
          if (ProjectUtil.isValidProjectPath(path)) Pair(path, entry.value) else null
        }.getOrLogException(LOG)
      }

      FUSProjectHotStartUpMeasurer.reportReopeningProjects(toOpen)

      if (toOpen.size == 1) {
        val pair = toOpen.get(0)
        val pathsToOpen = listOf(AbstractMap.SimpleEntry(pair.first.toString(), pair.second))
        return openOneByOne(pathsToOpen, index = 0, someProjectWasOpened = false)
      }
      else {
        return openMultiple(toOpen)
      }
    }
    finally {
      disableUpdatingRecentInfo.set(false)
    }
  }

  protected open fun isOpenProjectsOneByOneRequired(): Boolean {
    return ApplicationManager.getApplication().isHeadlessEnvironment || WindowManagerEx.getInstanceEx().getFrameHelper(null) != null
  }

  private suspend fun openOneByOne(
    openPaths: List<Entry<String, RecentProjectMetaInfo>>,
    index: Int,
    someProjectWasOpened: Boolean,
  ): Boolean {
    val (key, value) = openPaths.get(index)
    EelInitialization.runEelInitialization(key)
    val project = openProject(projectFile = Path.of(key), options = OpenProjectTask {
      forceOpenInNewFrame = true
      showWelcomeScreen = false
      projectWorkspaceId = value.projectWorkspaceId
      implOptions = OpenProjectImplOptions(recentProjectMetaInfo = value, frameInfo = value.frame)
    })
    val nextIndex = index + 1
    if (nextIndex == openPaths.size) {
      return someProjectWasOpened || project != null
    }
    else {
      return openOneByOne(openPaths, index = index + 1, someProjectWasOpened = someProjectWasOpened || project != null)
    }
  }

  override fun suggestNewProjectLocation(): String = ProjectUtil.getBaseDir()

  // toOpen - no non-existent project paths and every info has a frame
  private suspend fun openMultiple(toOpen: List<Pair<Path, RecentProjectMetaInfo>>): Boolean {
    val activeInfo = (toOpen.maxByOrNull { it.second.activationTimestamp } ?: return false).second

    data class Setup(val path: Path, val elementToPass: CoroutineContext?, val task: OpenProjectTask)

    val taskList = ArrayList<Setup>(toOpen.size)
    span("project frame initialization", Dispatchers.EDT) {
      var activeTask: Setup? = null
      for ((path, info) in toOpen) {
        val isActive = info == activeInfo
        val ideFrame = createIdeFrame(info.frame ?: FrameInfo())
        info.frameTitle?.let {
          ideFrame.title = it
        }

        CustomWindowHeaderUtil.customizeRawFrame(ideFrame)
        FUSProjectHotStartUpMeasurer.withProjectContextElement(path) {
          ideFrame.isVisible = true

          val startUpContextElementToPass = FUSProjectHotStartUpMeasurer.getStartUpContextElementToPass()
          val task = Setup(path,
                           startUpContextElementToPass,
                           OpenProjectTask {
                             forceOpenInNewFrame = true
                             showWelcomeScreen = false
                             projectWorkspaceId = info.projectWorkspaceId
                             implOptions = OpenProjectImplOptions(recentProjectMetaInfo = info, frame = ideFrame)
                           })

          if (isActive) {
            activeTask = task
          }
          else {
            taskList.add(task)
          }
        }
      }

      // we open project windows in the order projects were opened historically (to preserve taskbar order),
      // but once the windows are created, we start project loading from the latest active project (and put its window at front)
      taskList.add(activeTask!!)
      taskList.reverse()
      activeTask.task.frame?.toFront()
    }

    val projectManager = ProjectManagerEx.getInstanceEx()
    try {
      val iterator = taskList.iterator()
      while (iterator.hasNext()) {
        val (path, coroutineContext, options) = iterator.next()
        withContext(coroutineContext ?: EmptyCoroutineContext) {
          projectManager.openProjectAsync(path, options)
        }
        iterator.remove()
      }
    }
    finally {
      // cleanup unused pre-allocated frames if the operation failed or was canceled
      for (task in taskList) {
        task.task.frame?.dispose()
      }
    }
    return true
  }

  private fun canReopenProject(info: RecentProjectMetaInfo): Boolean {
    return info.opened && !info.hidden
  }

  /**
   * Do not reopen a project on restart and do not show it in the recent projects list
   */
  fun setProjectHidden(project: Project, hidden: Boolean) {
    val path = getProjectPath(project) ?: return
    synchronized(stateLock) {
      val info = state.additionalInfo.computeIfAbsent(path) {
        // A new unopened project isn't in `state.additionalInfo` yet, because it will be
        // added there on frame activation in `setActivationTimestamp` a bit later.
        // Add it to `state.additionalInfo` now to support new projects which are not opened yet.
        RecentProjectMetaInfo()
      }
      if (info.hidden == hidden) return

      info.hidden = hidden
      modCounter.increment()
    }
  }

  override val groups: List<ProjectGroup>
    get() {
      synchronized(stateLock) {
        return Collections.unmodifiableList(state.groups)
      }
    }

  override fun addGroup(group: ProjectGroup) {
    synchronized(stateLock) {
      if (!state.groups.contains(group)) {
        state.groups.add(group)
        fireChangeEvent()
      }
    }
  }

  override fun removeGroup(group: ProjectGroup) {
    synchronized(stateLock) {
      for (path in group.projects) {
        state.additionalInfo.remove(path)
      }

      state.groups.remove(group)
      modCounter.increment()
      fireChangeEvent()
    }
  }

  override fun moveProjectToGroup(projectPath: String, to: ProjectGroup) {
    for (group in groups) {
      group.removeProject(projectPath)
    }
    to.addProject(projectPath)
    to.isExpanded = true // Save state for UI
    fireChangeEvent()
  }

  override fun removeProjectFromGroup(projectPath: String, from: ProjectGroup) {
    from.removeProject(projectPath)
    fireChangeEvent()
  }

  fun findGroup(projectPath: String): ProjectGroup? = groups.find { it.projects.contains(projectPath) }

  override fun getModificationCount(): Long {
    synchronized(stateLock) {
      return modCounter.sum() + state.modificationCount
    }
  }

  private fun updateProjectInfo(project: Project, windowManager: WindowManagerImpl, writeLastProjectInfo: Boolean, appClosing: Boolean) {
    val frameHelper = windowManager.getFrameHelper(project)
    if (frameHelper == null) {
      LOG.warn("Cannot update frame info (project=${project.name}, reason=frame helper is not found)")
      return
    }

    val frame = frameHelper.frame
    if (appClosing) {
      frameHelper.appClosing()
    }

    val workspaceId = project.stateStore.projectWorkspaceId

    val frameInfo = ProjectFrameBounds.getInstance(project).getActualFrameInfoInDeviceSpace(frameHelper, frame, windowManager)
    val path = getProjectPath(project)
    synchronized(stateLock) {
      val info = state.additionalInfo.get(path)
      if (info == null) {
        LOG.warn("Cannot find info for ${project.name} to update frame info")
      }
      else {
        if (info.frame !== frameInfo) {
          info.frame = frameInfo
          if (IDE_FRAME_EVENT_LOG.isDebugEnabled) { // avoid unnecessary concatenation
            IDE_FRAME_EVENT_LOG.debug("Saved updated frame info ${info.frame} for project '${project.name}'")
          }
        }
        info.displayName = getProjectDisplayName(project)
        info.projectWorkspaceId = workspaceId
        info.frameTitle = frame.title
        info.colorInfo = ProjectColorInfoManager.getInstance(project).recentProjectColorInfo
      }
    }

    runCatching {
      if (writeLastProjectInfo) {
        writeInfoFile(frameInfo, frame)
      }
    }.getOrLogException(LOG)
  }

  /**
   * Finding a project that has just been cloned.
   * Skip a project with a similar path for [markPathRecent] to work correctly
   *
   * @param projectPath path to file that opens project (may differ with directory specified during cloning)
   */
  private fun findAndRemoveNewlyClonedProject(projectPath: String) {
    if (state.additionalInfo.containsKey(projectPath)) {
      return
    }

    var file: Path? = Path.of(projectPath)
    while (file != null) {
      val projectMetaInfo = state.additionalInfo.remove(file.invariantSeparatorsPathString)
      if (projectMetaInfo != null) {
        modCounter.increment()
        fireChangeEvent()
        break
      }

      file = file.parent
    }
  }

  private fun writeInfoFile(frameInfo: FrameInfo?, frame: JFrame) {
    if (!isUseProjectFrameAsSplash()) {
      return
    }

    val infoFile = getLastProjectFrameInfoFile()
    val bounds = frameInfo?.bounds
    if (bounds == null) {
      Files.deleteIfExists(infoFile)
      return
    }

    /* frame-info.tcl:
big_endian
int16 "Version"
int32 "x"
int32 "y"
int32 "width"
int32 "height"
uint32 -hex "backgroundColor"
uint8 "isFullscreen"
int32 "extendedState"
     */

    val buffer = ByteBuffer.allocate(2 + 6 * 4 + 1)
    // version
    buffer.putShort(0)
    buffer.putInt(bounds.x)
    buffer.putInt(bounds.y)
    buffer.putInt(bounds.width)
    buffer.putInt(bounds.height)

    buffer.putInt(frame.contentPane.background.rgb)

    buffer.put((if (frameInfo.fullScreen) 1 else 0).toByte())
    buffer.putInt(frameInfo.extendedState)

    buffer.flip()
    Files.newByteChannel(infoFile.createParentDirectories(), StandardOpenOption.WRITE, StandardOpenOption.CREATE).use {
      it.write(buffer)
    }
  }

  fun patchRecentPaths(patcher: (String) -> String?) {
    synchronized(stateLock) {
      for (path in state.additionalInfo.keys.toList()) {
        patcher(path)?.let { newPath ->
          state.additionalInfo.remove(path)?.let { info ->
            state.additionalInfo.put(newPath, info)
          }
        }
      }
      modCounter.increment()
    }
  }

  @Internal
  fun updateProjectColor(project: Project) {
    val info = ProjectColorInfoManager.getInstance(project).recentProjectColorInfo
    val projectPath = ProjectWindowCustomizerService.projectPath(project) ?: return
    updateProjectColor(projectPath, info)
  }

  @Internal
  fun updateProjectColor(projectBasePath: String, info: RecentProjectColorInfo) {
    synchronized(stateLock) {
      getProjectMetaInfo(projectBasePath)?.colorInfo = info
      modCounter.increment()
    }
  }

  @Internal
  class MyAppLifecycleListener : AppLifecycleListener {
    override fun projectOpenFailed() {
      getInstanceEx().updateLastProjectPath()
    }

    override fun appClosing() {
      if (ApplicationManager.getApplication().isHeadlessEnvironment) {
        return
      }

      val projectManager = ProjectManager.getInstanceIfCreated() ?: return
      val openProjects = projectManager.openProjects
      // do not delete an info file if ProjectManager not created - it means that it was simply not loaded, so, unlikely something is changed
      if (openProjects.isEmpty()) {
        if (!isUseProjectFrameAsSplash()) {
          Files.deleteIfExists(getLastProjectFrameInfoFile())
        }
      }
      else {
        val manager = getInstanceEx()
        val windowManager = WindowManager.getInstance() as WindowManagerImpl
        for ((index, project) in openProjects.withIndex()) {
          manager.updateProjectInfo(project = project,
                                    windowManager = windowManager,
                                    writeLastProjectInfo = index == 0,
                                    appClosing = true)
        }
      }
    }

    override fun projectFrameClosed() {
      // ProjectManagerListener.projectClosed cannot be used to call updateLastProjectPath,
      // because called even if a project closed on app exit
      getInstanceEx().updateLastProjectPath()
    }
  }

  @Internal
  fun hasCustomIcon(project: Project): Boolean = projectIconHelper.hasCustomIcon(project)
}

private fun fireChangeEvent() {
  ApplicationManager.getApplication().invokeLater {
    ApplicationManager.getApplication().messageBus.syncPublisher(RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC).change()
  }
}

private fun isUseProjectFrameAsSplash() = Registry.`is`("ide.project.frame.as.splash")

private fun readProjectName(path: String): String {
  if (!RecentProjectsManagerBase.isFileSystemPath(path)) {
    return path
  }

  // IJPL-194035
  // Avoid greedy I/O under non-local projects. For example, in the case of WSL:
  //	1.	it may trigger Ijent initialization for each recent project
  //	2.	with Ijent disabled, performance may degrade further — 9P is very slow and could lead to UI freezes
  if (Path(path).getEelDescriptor() != LocalEelDescriptor) {
    return path
  }

  if (path.endsWith(".ipr")) {
    return FileUtilRt.getNameWithoutExtension(path)
  }

  val file = try {
    Path.of(path)
  }
  catch (_: InvalidPathException) {
    return path
  }

  val storePath = ProjectStorePathManager.getInstance().getStoreDirectoryPath(file)
  return JpsPathUtil.readProjectName(storePath) ?: PathUtilRt.getFileName(path)
}

private fun getLastProjectFrameInfoFile() = appSystemDir.resolve("lastProjectFrameInfo")

private fun convertToSystemIndependentPaths(list: MutableList<String>) {
  list.replaceAll {
    FileUtilRt.toSystemIndependentName(it)
  }
}

private fun validateRecentProjects(modCounter: LongAdder, map: MutableMap<String, RecentProjectMetaInfo>) {
  val limit = AdvancedSettings.getInt("ide.max.recent.projects")
  var toRemove = map.size - limit
  if (limit < 1 || toRemove <= 0) {
    return
  }

  val oldMapSize = map.size
  val iterator = map.values.iterator()
  while (true) {
    if (!iterator.hasNext()) {
      break
    }

    val info = iterator.next()
    if (info.opened) {
      continue
    }

    iterator.remove()
    toRemove--

    if (toRemove <= 0) {
      break
    }
  }

  if (oldMapSize != map.size) {
    modCounter.increment()
  }
}

internal fun getProjectNameOnlyByPath(path: String): String {
  val name = PathUtilRt.getFileName(path)
  return if (path.endsWith(".ipr")) FileUtilRt.getNameWithoutExtension(name) else name
}

@JvmInline
@Internal
value class ProjectNameOrPathIfNotYetComputed(val nameOnlyByProjectPath: String)

@Internal
data class OpenProjectImplOptions(
  @JvmField val recentProjectMetaInfo: RecentProjectMetaInfo,
  @JvmField val frameInfo: FrameInfo? = null,
  @JvmField val frame: IdeFrameImpl? = null,
)

val OpenProjectTask.frame: IdeFrameImpl?
  @Internal get() = (implOptions as OpenProjectImplOptions?)?.frame

val OpenProjectTask.frameInfo: FrameInfo?
  @Internal get() = (implOptions as OpenProjectImplOptions?)?.frameInfo

@Internal
interface SystemDock {
  suspend fun updateRecentProjectsMenu()
}
