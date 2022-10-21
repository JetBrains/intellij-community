// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "OVERRIDE_DEPRECATION")

package com.intellij.ide

import com.intellij.diagnostic.runActivity
import com.intellij.ide.RecentProjectsManager.Companion.fireChangeEvent
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.ProjectUtil.isSameProject
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.ui.UISettings
import com.intellij.idea.AppMode
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.*
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.platform.ProjectSelfieUtil
import com.intellij.project.stateStore
import com.intellij.util.PathUtilRt
import com.intellij.util.SingleAlarm
import com.intellij.util.io.isDirectory
import com.intellij.util.io.outputStream
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.io.write
import com.intellij.util.ui.ImageUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asDeferred
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.jps.util.JpsPathUtil
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.stream.MemoryCacheImageOutputStream
import javax.swing.Icon
import javax.swing.JFrame
import kotlin.collections.Map.Entry
import kotlin.collections.component1
import kotlin.collections.component2

private val LOG = logger<RecentProjectsManager>()

/**
 * Used directly by IntelliJ IDEA.
 */
@State(name = "RecentProjectsManager", storages = [Storage(value = "recentProjects.xml", roamingType = RoamingType.DISABLED)])
open class RecentProjectsManagerBase : RecentProjectsManager, PersistentStateComponent<RecentProjectManagerState>, ModificationTracker {
  companion object {
    const val MAX_PROJECTS_IN_MAIN_MENU: Int = 6

    @JvmStatic
    fun getInstanceEx(): RecentProjectsManagerBase = RecentProjectsManager.getInstance() as RecentProjectsManagerBase

    @JvmStatic
    fun isFileSystemPath(path: String): Boolean {
      return path.indexOf('/') != -1 || path.indexOf('\\') != -1
    }
  }

  private val modCounter = AtomicLong()
  private val projectIconHelper by lazy(::RecentProjectIconHelper)
  private val namesToResolve = HashSet<String>(MAX_PROJECTS_IN_MAIN_MENU)

  private val nameCache: MutableMap<String, String> = Collections.synchronizedMap(HashMap())

  private val disableUpdatingRecentInfo = AtomicBoolean()

  private val nameResolver = SingleAlarm.pooledThreadSingleAlarm(50, ApplicationManager.getApplication()) {
    var paths: Set<String>
    synchronized(namesToResolve) {
      paths = HashSet(namesToResolve)
      namesToResolve.clear()
    }
    for (p in paths) {
      nameCache.put(p, readProjectName(p))
    }
  }

  private val stateLock = Any()
  private var state = RecentProjectManagerState()

  final override fun getState() = state

  fun getProjectMetaInfo(file: Path): RecentProjectMetaInfo? {
    synchronized(stateLock) {
      return state.additionalInfo.get(file.systemIndependentPath)
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
        modCounter.incrementAndGet()
      }
      for (group in state.groups) {
        if (group.removeProject(path)) {
          modCounter.incrementAndGet()
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
        updateProjectOpenedState(project, updateTime = false)
      }
      state.validateRecentProjects(modCounter)
    }
  }

  private fun updateProjectOpenedState(project: Project, updateTime: Boolean) {
    val path = getProjectPath(project) ?: return
    val info = state.additionalInfo.get(path) ?: return
    info.opened = true
    if (updateTime) {
      info.projectOpenTimestamp = System.currentTimeMillis()
    }
    info.displayName = getProjectDisplayName(project)
  }

  protected open fun getProjectDisplayName(project: Project): String? = null

  fun getProjectIcon(path: String, isProjectValid: Boolean): Icon {
    return projectIconHelper.getProjectIcon(path, isProjectValid)
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun getRecentProjectsActions(addClearListItem: Boolean): Array<AnAction> {
    return RecentProjectListActionProvider.getInstance().getActions(addClearListItem = addClearListItem).toTypedArray()
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun getRecentProjectsActions(addClearListItem: Boolean, useGroups: Boolean): Array<AnAction> {
    return RecentProjectListActionProvider.getInstance().getActions(addClearListItem = addClearListItem,
                                                                    useGroups = useGroups).toTypedArray()
  }

  fun markPathRecent(path: String, project: Project) {
    synchronized(stateLock) {
      for (group in state.groups) {
        if (group.markProjectFirst(path)) {
          modCounter.incrementAndGet()
          break
        }
      }

      // remove instead of get to re-order
      val info = state.additionalInfo.remove(path) ?: RecentProjectMetaInfo()
      state.additionalInfo.put(path, info)
      modCounter.incrementAndGet()
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
    }
  }

  fun addRecentPath(path: String, info: RecentProjectMetaInfo) {
    synchronized(stateLock) {
      state.additionalInfo.put(path, info)
      modCounter.incrementAndGet()
    }
  }

  // for Rider
  protected open fun getRecentProjectMetadata(path: String, project: Project): String? = null

  open fun getProjectPath(project: Project): String? {
    return FileUtilRt.toSystemIndependentName(project.presentableUrl ?: return null)
  }

  @TestOnly
  fun openProjectSync(projectFile: Path, openProjectOptions: OpenProjectTask): Project? {
    return runBlocking { openProject(projectFile, openProjectOptions) }
  }

  // open for Rider
  open suspend fun openProject(projectFile: Path, options: OpenProjectTask): Project? {
    var effectiveOptions = options
    if (options.implOptions == null) {
      getProjectMetaInfo(projectFile)?.frame?.let { frameInfo ->
        effectiveOptions = effectiveOptions.copy(implOptions = OpenProjectImplOptions(frameInfo = frameInfo))
      }
    }

    if (isValidProjectPath(projectFile)) {
      val projectManager = ProjectManagerEx.getInstanceEx()
      projectManager.openProjects.firstOrNull { isSameProject(projectFile, it) }?.let { project ->
        withContext(Dispatchers.EDT) {
          ProjectUtil.focusProjectWindow(project = project)
        }
        return project
      }
      return projectManager.openProjectAsync(projectFile, effectiveOptions)
    }
    else {
      // If .idea is missing in the recent project's dir; this might mean, for instance, that 'git clean' was called.
      // Reopening such a project should be similar to opening the dir first time (and trying to import known project formats)
      // IDEA-144453 IDEA rejects opening recent project if there are no .idea subfolder
      // CPP-12106 Auto-load CMakeLists.txt on opening from Recent projects when .idea and cmake-build-debug were deleted
      return ProjectUtil.openOrImportAsync(projectFile, effectiveOptions)
    }
  }

  open fun setActivationTimestamp(project: Project, timestamp: Long) {
    getProjectPath(project)?.let {
      synchronized(stateLock) {
        state.additionalInfo.get(it)?.activationTimestamp = timestamp
      }
    }
  }

  fun getLastOpenedProject() = state.lastOpenedProject

  @Internal
  class MyFrameStateListener : FrameStateListener {
    override fun onFrameActivated(frame: IdeFrame) = frame.notifyProjectActivation()
  }

  @VisibleForTesting
  suspend fun runProjectPostStartupActivity(project: Project) {
    if (disableUpdatingRecentInfo.get() || LightEdit.owns(project)) {
      return
    }

    val projectPath = getProjectPath(project) ?: return
    synchronized(stateLock) {
      findAndRemoveNewlyClonedProject(projectPath)
      markPathRecent(projectPath, project)
      state.lastOpenedProject = projectPath

      updateProjectOpenedState(project, updateTime = true)
      state.validateRecentProjects(modCounter)
    }

    withContext(Dispatchers.EDT) {
      updateSystemDockMenu()
    }
  }

  internal class MyProjectPostStartupActivity : ProjectPostStartupActivity {
    init {
      if (ApplicationManager.getApplication().isUnitTestMode ||
          ApplicationManager.getApplication().isHeadlessEnvironment /* disabling for Fleet */) {
        throw ExtensionNotApplicableException.create()
      }
    }

    override suspend fun execute(project: Project) {
      getInstanceEx().runProjectPostStartupActivity(project)
    }
  }

  @Internal
  @VisibleForTesting
  class MyProjectListener : ProjectCloseListener {
    override fun projectClosing(project: Project) {
      val app = ApplicationManagerEx.getApplicationEx()
      if (app.isExitInProgress) {
        // appClosing updates project info (even more - on project closed full screen state maybe not correct)
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
        // appClosing updates project info (even more - on project closed full screen state maybe not correct)
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
      state.validateRecentProjects(modCounter)
      return state.additionalInfo.keys.reversed()
    }
  }

  fun getDisplayName(path: String): String? {
    synchronized(stateLock) {
      return state.additionalInfo.get(path)?.displayName
    }
  }

  fun getProjectName(path: String): String {
    nameCache.get(path)?.let {
      return it
    }

    nameResolver.cancel()
    synchronized(namesToResolve) {
      namesToResolve.add(path)
    }
    nameResolver.request()
    val name = PathUtilRt.getFileName(path)
    return if (path.endsWith(".ipr")) FileUtilRt.getNameWithoutExtension(name) else name
  }

  override fun willReopenProjectOnStart(): Boolean {
    if (!GeneralSettings.getInstance().isReopenLastProject || AppMode.isDontReopenProjects()) {
      return false
    }

    synchronized(stateLock) {
      return state.additionalInfo.values.any { it.opened }
    }
  }

  override suspend fun reopenLastProjectsOnStart(): Boolean {
    val openPaths = lastOpenedProjects
    if (openPaths.isEmpty()) {
      return false
    }

    disableUpdatingRecentInfo.set(true)
    try {
      val isOpened = if (openPaths.size == 1 ||
                         ApplicationManager.getApplication().isHeadlessEnvironment ||
                         WindowManagerEx.getInstanceEx().getFrameHelper(null) != null) {
        openOneByOne(java.util.List.copyOf(openPaths), index = 0, someProjectWasOpened = false)
      }
      else {
        openMultiple(openPaths)
      }
      return isOpened
    }
    finally {
      WelcomeFrame.showIfNoProjectOpened(null)
      disableUpdatingRecentInfo.set(false)
    }
  }

  private suspend fun openOneByOne(openPaths: List<Entry<String, RecentProjectMetaInfo>>,
                                   index: Int,
                                   someProjectWasOpened: Boolean): Boolean {
    val (key, value) = openPaths.get(index)
    val options = OpenProjectTask {
      forceOpenInNewFrame = true
      showWelcomeScreen = false
      projectWorkspaceId = value.projectWorkspaceId
      implOptions = OpenProjectImplOptions(frameInfo = value.frame)
    }
    val project = openProject(Path.of(key), options)
    val nextIndex = index + 1
    if (nextIndex == openPaths.size) {
      return someProjectWasOpened || project != null
    }
    else {
      return openOneByOne(openPaths, index = index + 1, someProjectWasOpened = someProjectWasOpened || project != null)
    }
  }

  override fun suggestNewProjectLocation() = ProjectUtil.getBaseDir()

  // open for Rider
  protected open fun isValidProjectPath(file: Path) = ProjectUtilCore.isValidProjectPath(file)

  // open for Rider
  @Suppress("MemberVisibilityCanBePrivate")
  protected suspend fun openMultiple(openPaths: List<Entry<String, RecentProjectMetaInfo>>): Boolean {
    val toOpen = ArrayList<Pair<Path, RecentProjectMetaInfo>>(openPaths.size)
    for (entry in openPaths) {
      val path = Path.of(entry.key)
      if (entry.value.frame == null || !isValidProjectPath(path)) {
        return false
      }

      toOpen.add(Pair(path, entry.value))
    }

    // ok, no non-existent project paths and every info has a frame
    val activeInfo = toOpen.maxByOrNull { it.second.activationTimestamp }!!.second
    val taskList = ArrayList<Pair<Path, OpenProjectTask>>(toOpen.size)
    withContext(Dispatchers.EDT) {
      runActivity("project frame initialization") {
        var activeTask: Pair<Path, OpenProjectTask>? = null
        var fullScreenPromise: CompletableFuture<*>? = null
        for ((path, info) in toOpen) {
          val frameInfo = info.frame!!
          val isActive = info == activeInfo
          val ideFrame = createNewProjectFrame(frameInfo)
          info.frameTitle?.let {
            ideFrame.title = it
          }
          val frameHelper = ProjectFrameHelper(frame = ideFrame, selfie = null)
          val frameManager = MyProjectUiFrameManager(frame = ideFrame, frameHelper = frameHelper)

          frameHelper.init()

          ideFrame.isVisible = true
          if (frameInfo.fullScreen && FrameInfoHelper.isFullScreenSupportedInCurrentOs()) {
            fullScreenPromise = frameHelper.toggleFullScreen(true)
          }

          val task = Pair(path, OpenProjectTask {
            forceOpenInNewFrame = true
            showWelcomeScreen = false
            projectWorkspaceId = info.projectWorkspaceId
            implOptions = OpenProjectImplOptions(frameManager = frameManager, isVisibleManaged = true)
          })
          if (isActive) {
            activeTask = task
          }
          else {
            taskList.add(task)
          }
        }
        // we open project windows in the order projects were opened historically (to preserve taskbar order)
        // but once the windows are created, we start project loading from the latest active project (and put its window at front)
        taskList.add(activeTask!!)
        taskList.reverse()
        val frameToActivate = (activeTask.second.frameManager as MyProjectUiFrameManager).frame
        fullScreenPromise?.asDeferred()?.join()
        frameToActivate.toFront()
      }
    }

    val projectManager = ProjectManagerEx.getInstanceEx()
    val iterator = taskList.iterator()
    while (iterator.hasNext()) {
      val entry = iterator.next()
      try {
        projectManager.openProjectAsync(entry.first, entry.second)
      }
      catch (e: Exception) {
        withContext(NonCancellable + Dispatchers.EDT + ModalityState.any().asContextElement()) {
          @Suppress("SSBasedInspection")
          (entry.second.frameManager as MyProjectUiFrameManager?)?.dispose()
          while (iterator.hasNext()) {
            @Suppress("SSBasedInspection")
            (iterator.next().second.frameManager as MyProjectUiFrameManager?)?.dispose()
          }
        }

        throw e
      }
    }
    return true
  }

  protected val lastOpenedProjects: List<Entry<String, RecentProjectMetaInfo>>
    get() = synchronized(stateLock) {
      return state.additionalInfo.entries.filter { it.value.opened }
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
      modCounter.incrementAndGet()
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
      return modCounter.get() + state.modificationCount
    }
  }

  private fun updateProjectInfo(project: Project, windowManager: WindowManagerImpl, writeLastProjectInfo: Boolean, appClosing: Boolean) {
    val frameHelper = windowManager.getFrameHelper(project)
    if (frameHelper == null) {
      LOG.warn("Cannot update frame info (project=${project.name}, reason=frame helper is not found)")
      return
    }

    val frame = frameHelper.frameOrNull
    if (frame == null) {
      LOG.warn("Cannot update frame info (project=${project.name}, reason=frame is null)")
      return
    }

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
        }
        info.displayName = getProjectDisplayName(project)
        info.projectWorkspaceId = workspaceId
        info.frameTitle = frame.title
      }
    }

    LOG.runAndLogException {
      if (writeLastProjectInfo) {
        writeInfoFile(frameInfo, frame)
      }

      if (workspaceId != null && Registry.`is`("ide.project.loading.show.last.state", false)) {
        takeASelfie(frameHelper, workspaceId)
      }
    }
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

    var file: File? = File(projectPath)
    while (file != null) {
      val projectMetaInfo = state.additionalInfo.remove(FileUtil.toSystemIndependentName(file.path))
      if (projectMetaInfo != null) break

      file = FileUtil.getParentFile(file)
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
    infoFile.write(buffer)
  }

  private fun takeASelfie(frameHelper: ProjectFrameHelper, workspaceId: String) {
    val frame = frameHelper.frameOrNull!!
    val width = frame.width
    val height = frame.height
    val image = ImageUtil.createImage(frame.graphicsConfiguration, width, height, BufferedImage.TYPE_INT_ARGB)
    UISettings.setupAntialiasing(image.graphics)
    frame.paint(image.graphics)
    val selfieFile = ProjectSelfieUtil.getSelfieLocation(workspaceId)
    // must be a file, because for Path no optimized impl (output stream must be not used, otherwise cache file will be created by JDK)
    //long start = System.currentTimeMillis();
    selfieFile.outputStream().use { stream ->
      MemoryCacheImageOutputStream(stream).use { out ->
        val writer = ImageIO.getImageWriters(ImageTypeSpecifier.createFromRenderedImage(image), "png").next()
        try {
          writer.output = out
          writer.write(null, IIOImage(image, null, null), null)
        }
        finally {
          writer.dispose()
        }
      }
    }

    //System.out.println("Write image: " + (System.currentTimeMillis() - start) + "ms");
    val lastLink = selfieFile.parent.resolve("last.png")
    if (SystemInfo.isUnix) {
      Files.deleteIfExists(lastLink)
      Files.createSymbolicLink(lastLink, selfieFile)
    }
    else {
      Files.copy(selfieFile, lastLink, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  fun patchRecentPaths(patcher: (String) -> String?) {
    synchronized(stateLock) {
      for (path in state.additionalInfo.keys.toList()) {
        patcher(path)?.let { newPath ->
          state.additionalInfo.remove(path)?.let { info ->
            state.additionalInfo[newPath] = info
          }
        }
      }
      modCounter.incrementAndGet()
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

      val openProjects = ProjectManagerEx.getOpenProjects()
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

    override fun appWillBeClosed(isRestart: Boolean) {

    }

    override fun projectFrameClosed() {
      // ProjectManagerListener.projectClosed cannot be used to call updateLastProjectPath,
      // because called even if project closed on app exit
      getInstanceEx().updateLastProjectPath()
    }
  }
}

private fun isUseProjectFrameAsSplash() = Registry.`is`("ide.project.frame.as.splash")

private fun readProjectName(path: String): String {
  if (!RecentProjectsManagerBase.isFileSystemPath(path)) {
    return path
  }

  val file = try {
    Path.of(path)
  }
  catch (e: InvalidPathException) {
    return path
  }

  if (!file.isDirectory()) {
    val fileName = file.fileName
    if (fileName != null) {
      return FileUtilRt.getNameWithoutExtension(fileName.toString())
    }
  }

  val projectDir = file.resolve(Project.DIRECTORY_STORE_FOLDER)
  return JpsPathUtil.readProjectName(projectDir) ?: JpsPathUtil.getDefaultProjectName(projectDir)
}

private fun getLastProjectFrameInfoFile() = appSystemDir.resolve("lastProjectFrameInfo")

private fun convertToSystemIndependentPaths(list: MutableList<String>) {
  list.replaceAll {
    FileUtilRt.toSystemIndependentName(it)
  }
}

private open class MyProjectUiFrameManager(val frame: IdeFrameImpl, private val frameHelper: ProjectFrameHelper) : ProjectUiFrameManager {
  override fun getWindow() = frame

  override suspend fun createFrameHelper(allocator: ProjectUiFrameAllocator) = frameHelper

  fun dispose() {
    frame.dispose()
  }
}

private fun updateSystemDockMenu() {
  if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
    runActivity("system dock menu") {
      SystemDock.updateMenu()
    }
  }
}
