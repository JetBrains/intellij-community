// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "OVERRIDE_DEPRECATION", "LiftReturnOrAssignment")

package com.intellij.ide

import com.intellij.diagnostic.runActivity
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.ProjectUtil.isSameProject
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.ide.impl.ProjectUtilService
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.vcs.RecentProjectsBranchesProvider
import com.intellij.idea.AppMode
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.OpenProjectImplOptions
import com.intellij.openapi.project.impl.createNewProjectFrameProducer
import com.intellij.openapi.project.impl.frame
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.project.stateStore
import com.intellij.util.PathUtilRt
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
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.io.path.invariantSeparatorsPathString
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
    private val TEMPORARY_PROJECT_KEY = Key.create<Boolean>("RecentProjectManager.TemporaryProject")

    @JvmStatic
    fun getInstanceEx(): RecentProjectsManagerBase = RecentProjectsManager.getInstance() as RecentProjectsManagerBase

    internal fun isFileSystemPath(path: String): Boolean = path.indexOf('/') != -1 || path.indexOf('\\') != -1

    /**
     * Indicates that the project shouldn't be added to the recent projects list. 
     * This function must be called before the project is opened, e.g., in [com.intellij.ide.impl.OpenProjectTaskBuilder.beforeInit].
     */
    @Internal
    fun markProjectAsTemporary(project: Project) {
      project.putUserData(TEMPORARY_PROJECT_KEY, true)
    }

    private fun isTemporaryProject(project: Project) = project.getUserData(TEMPORARY_PROJECT_KEY) == true
  }

  private val modCounter = LongAdder()
  private val projectIconHelper by lazy(::RecentProjectIconHelper)
  private val namesToResolve = HashSet<String>(MAX_PROJECTS_IN_MAIN_MENU)

  private val nameCache: MutableMap<String, String> = Collections.synchronizedMap(HashMap())

  private val disableUpdatingRecentInfo = AtomicBoolean()

  private val nameResolveRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

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
  }

  final override fun getState(): RecentProjectManagerState = state

  internal fun getProjectMetaInfo(projectStoreBaseDir: Path): RecentProjectMetaInfo? {
    val path = getProjectPath(projectStoreBaseDir) ?: return null
    synchronized(stateLock) {
      return state.additionalInfo.get(path)
    }
  }

  final override fun loadState(state: RecentProjectManagerState) {
    synchronized(stateLock) {
      this.state = state
      state.pid = null

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

  fun getProjectIcon(path: String, isProjectValid: Boolean, iconSize: Int, name: String? = null): Icon {
    return projectIconHelper.getProjectIcon(path, isProjectValid, iconSize, name)
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun getRecentProjectsActions(addClearListItem: Boolean): Array<AnAction> {
    return RecentProjectListActionProvider.getInstance().getActions(addClearListItem = addClearListItem).toTypedArray()
  }

  fun markPathRecent(path: String, project: Project): RecentProjectMetaInfo {
    if (isTemporaryProject(project)) {
      return RecentProjectMetaInfo()
    }
    
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

    FUSProjectHotStartUpMeasurer.reportProjectPath(projectFile)
    if (isValidProjectPath(projectFile)) {
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
    if (disableUpdatingRecentInfo.get() || isTemporaryProject(project)) {
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
  suspend fun projectOpened(project: Project) {
    projectOpened(project, System.currentTimeMillis())
  }

  internal suspend fun projectOpened(project: Project, openTimestamp: Long) {
    if (disableUpdatingRecentInfo.get() || LightEdit.owns(project) || isTemporaryProject(project)) {
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

    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      updateSystemDockMenu()
    }
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
      updateSystemDockMenu()
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

  fun getCurrentBranchName(path: String): String? {
    return RecentProjectsBranchesProvider.getCurrentBranch(path)
  }

  fun getProjectName(path: String): String {
    nameCache.get(path)?.let {
      return it
    }

    synchronized(namesToResolve) {
      namesToResolve.add(path)
    }
    check(nameResolveRequests.tryEmit(Unit))

    return getProjectNameOnlyByPath(path).nameOnlyByProjectPath
  }

  fun forceReopenProjects() {
    synchronized(stateLock) {
      state.forceReopenProjects = true
    }
  }

  override fun willReopenProjectOnStart(): Boolean {
    if (!synchronized(stateLock) { state.forceReopenProjects }
        && (!GeneralSettings.getInstance().isReopenLastProject || AppMode.isDontReopenProjects())) {
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
          if (isValidProjectPath(path)) Pair(path, entry.value) else null
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

  private suspend fun openOneByOne(openPaths: List<Entry<String, RecentProjectMetaInfo>>,
                                   index: Int,
                                   someProjectWasOpened: Boolean): Boolean {
    val (key, value) = openPaths.get(index)
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

  // open for Rider
  @Suppress("MemberVisibilityCanBePrivate")
  protected suspend fun isValidProjectPath(file: Path): Boolean {
    return withContext(Dispatchers.IO) { ProjectUtilCore.isValidProjectPath(file) }
  }

  // toOpen -  no non-existent project paths and every info has a frame
  private suspend fun openMultiple(toOpen: List<Pair<Path, RecentProjectMetaInfo>>): Boolean {
    val activeInfo = (toOpen.maxByOrNull { it.second.activationTimestamp } ?: return false).second
    val taskList = ArrayList<Pair<Path, OpenProjectTask>>(toOpen.size)
    span("project frame initialization", Dispatchers.EDT) {
      var activeTask: Pair<Path, OpenProjectTask>? = null
      for ((path, info) in toOpen) {
        val isActive = info == activeInfo
        val ideFrame = createNewProjectFrameProducer(info.frame).create()
        info.frameTitle?.let {
          ideFrame.title = it
        }

        CustomWindowHeaderUtil.customizeRawFrame(ideFrame)
        ideFrame.isVisible = true
        val task = Pair(path, OpenProjectTask {
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

      // we open project windows in the order projects were opened historically (to preserve taskbar order),
      // but once the windows are created, we start project loading from the latest active project (and put its window at front)
      taskList.add(activeTask!!)
      taskList.reverse()
      activeTask.second.frame?.toFront()
    }

    val projectManager = ProjectManagerEx.getInstanceEx()
    for ((path, options) in taskList) {
      projectManager.openProjectAsync(path, options)
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
    val info = state.additionalInfo.get(path) ?: return
    val oldHidden = info.hidden
    info.hidden = hidden
    if (oldHidden != hidden) {
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
    updateProjectColor(Path.of(projectPath), info)
  }

  @Internal
  fun updateProjectColor(projectBasePath: Path, info: RecentProjectColorInfo) {
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
      // do not delete info file if ProjectManager not created - it means that it was simply not loaded, so, unlikely something is changed
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
  fun hasCustomIcon(project: Project) = projectIconHelper.hasCustomIcon(project)
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

  if (path.endsWith(".ipr")) {
    return FileUtilRt.getNameWithoutExtension(path)
  }

  val file = try {
    Path.of(path)
  }
  catch (e: InvalidPathException) {
    return path
  }

  return JpsPathUtil.readProjectName(file.resolve(Project.DIRECTORY_STORE_FOLDER)) ?: PathUtilRt.getFileName(path)
}

private fun getLastProjectFrameInfoFile() = appSystemDir.resolve("lastProjectFrameInfo")

private fun convertToSystemIndependentPaths(list: MutableList<String>) {
  list.replaceAll {
    FileUtilRt.toSystemIndependentName(it)
  }
}

private fun updateSystemDockMenu() {
  if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
    runActivity("system dock menu") {
      SystemDock.updateMenu()
    }
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

internal fun getProjectNameOnlyByPath(path: String): ProjectNameOnlyByPath {
  val name = PathUtilRt.getFileName(path)
  return ProjectNameOnlyByPath(if (path.endsWith(".ipr")) FileUtilRt.getNameWithoutExtension(name) else name)
}

@JvmInline
@Internal
value class ProjectNameOnlyByPath(val nameOnlyByProjectPath: String)
