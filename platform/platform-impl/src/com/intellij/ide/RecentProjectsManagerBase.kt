// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide

import com.intellij.diagnostic.runActivity
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectUiFrameAllocator
import com.intellij.openapi.project.impl.ProjectUiFrameManager
import com.intellij.openapi.project.impl.createNewProjectFrame
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
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
import com.intellij.util.text.nullize
import com.intellij.util.ui.ImageUtil
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.jps.util.JpsPathUtil
import java.awt.image.BufferedImage
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.stream.MemoryCacheImageOutputStream
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JFrame
import kotlin.collections.Map.Entry
import kotlin.collections.component1
import kotlin.collections.component2

private val LOG = logger<RecentProjectsManager>()

/**
 * Used directly by IntelliJ IDEA.
 */
@State(name = "RecentProjectsManager", storages = [Storage(value = "recentProjects.xml", roamingType = RoamingType.DISABLED)])
open class RecentProjectsManagerBase : RecentProjectsManager(), PersistentStateComponent<RecentProjectManagerState>, ModificationTracker {
  companion object {
    const val MAX_PROJECTS_IN_MAIN_MENU = 6

    @JvmStatic
    val instanceEx: RecentProjectsManagerBase
      get() = getInstance() as RecentProjectsManagerBase

    @JvmStatic
    fun isFileSystemPath(path: String): Boolean {
      return path.indexOf('/') != -1 || path.indexOf('\\') != -1
    }

    @JvmField
    var dontReopenProjects = false
  }

  private val modCounter = AtomicLong()
  private val projectIconHelper by lazy { RecentProjectIconHelper() }
  private val namesToResolve: MutableSet<String> = HashSet(MAX_PROJECTS_IN_MAIN_MENU)

  private val nameCache: MutableMap<String, String> = Collections.synchronizedMap(HashMap())

  private val disableUpdatingRecentInfo = AtomicBoolean()

  private val nameResolver = SingleAlarm.pooledThreadSingleAlarm(50, ApplicationManager.getApplication()) {
    var paths: Set<String>
    synchronized(namesToResolve) {
      paths = HashSet(namesToResolve)
      namesToResolve.clear()
    }
    for (p in paths) {
      nameCache[p] = readProjectName(p)
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

  final override fun noStateLoaded() {
    val old = service<OldRecentDirectoryProjectsManager>().loadedState ?: return
    val newState = RecentProjectManagerState()
    newState.copyFrom(old)
    newState.intIncrementModificationCount()
    loadState(newState)
  }

  final override fun loadState(state: RecentProjectManagerState) {
    synchronized(stateLock) {
      this.state = state
      state.pid = null

      @Suppress("DEPRECATION")
      migrateOpenPaths(state.openPaths)

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
          newAdditionalInfo[recentPath] = value
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

  // reorder according to openPaths order and mark as opened
  private fun migrateOpenPaths(openPaths: MutableList<String>) {
    if (openPaths.isEmpty()) {
      return
    }

    convertToSystemIndependentPaths(openPaths)

    val oldInfoMap = mutableMapOf<String, RecentProjectMetaInfo>()
    for (path in openPaths) {
      val info = state.additionalInfo.remove(path)
      if (info != null) {
        oldInfoMap[path] = info
      }
    }

    for (path in openPaths.asReversed()) {
      val info = oldInfoMap.get(path) ?: RecentProjectMetaInfo()
      info.opened = true
      state.additionalInfo[path] = info
    }
    openPaths.clear()
    modCounter.incrementAndGet()
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
    }
  }

  override fun hasPath(path: String?): Boolean {
    synchronized(stateLock) {
      return state.additionalInfo.containsKey(path)
    }
  }

  /**
   * @return a path pointing to a directory where the last project was created or null if not available
   */
  override fun getLastProjectCreationLocation(): String? {
    synchronized(stateLock) {
      return state.lastProjectLocation
    }
  }

  override fun setLastProjectCreationLocation(value: String?) {
    val newValue = value.nullize(nullizeSpaces = true)?.let { FileUtilRt.toSystemIndependentName(it) }
    synchronized(stateLock) {
      state.lastProjectLocation = newValue
    }
  }

  override fun updateLastProjectPath() {
    val openProjects = ProjectUtil.getOpenProjects()
    synchronized(stateLock) {
      for (info in state.additionalInfo.values) {
        info.opened = false
      }

      for (project in openProjects) {
        val path = getProjectPath(project)
        val info = if (path == null) null else state.additionalInfo.get(path)
        if (info != null) {
          info.opened = true
          info.projectOpenTimestamp = System.currentTimeMillis()
          info.displayName = getProjectDisplayName(project)
        }
      }
      state.validateRecentProjects(modCounter)
    }
  }

  protected open fun getProjectDisplayName(project: Project): String? = null

  fun getProjectIcon(path: String): Icon {
    return projectIconHelper.getProjectIcon(path, false)
  }

  @Deprecated("Use getProjectIcon(String, Boolean)", ReplaceWith("getProjectIcon(path, generateFromName)"))
  fun getProjectIcon(path: String, @Suppress("UNUSED_PARAMETER") isDark: Boolean, generateFromName: Boolean) = getProjectIcon(path, generateFromName)

  fun getProjectIcon(path: String, generateFromName: Boolean): Icon {
    return projectIconHelper.getProjectIcon(path, generateFromName)
  }

  fun getProjectOrAppIcon(path: String): Icon {
    return projectIconHelper.getProjectOrAppIcon(path)
  }

  override fun getRecentProjectsActions(addClearListItem: Boolean): Array<AnAction> {
    return RecentProjectListActionProvider.getInstance().getActions(addClearListItem = addClearListItem).toTypedArray()
  }

  override fun getRecentProjectsActions(addClearListItem: Boolean, useGroups: Boolean): Array<AnAction?> {
    return RecentProjectListActionProvider.getInstance().getActions(addClearListItem = addClearListItem, useGroups = useGroups).toTypedArray()
  }

  private fun markPathRecent(path: String, project: Project) {
    synchronized(stateLock) {
      for (group in state.groups) {
        if (group.markProjectFirst(path)) {
          modCounter.incrementAndGet()
          break
        }
      }

      // remove instead of get to re-order
      val info = state.additionalInfo.remove(path) ?: RecentProjectMetaInfo()
      state.additionalInfo[path] = info
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
    }
  }

  @Suppress("MemberVisibilityCanBePrivate", "UNUSED_PARAMETER")
  // for Rider
  protected open fun getRecentProjectMetadata(path: String, project: Project): String? = null

  open fun getProjectPath(project: Project): String? {
    return FileUtilRt.toSystemIndependentName(project.presentableUrl ?: return null)
  }

  // open for Rider
  open fun openProject(projectFile: Path, openProjectOptions: OpenProjectTask): CompletableFuture<Project?> {
    if (isValidProjectPath(projectFile)) {
      ProjectUtil.findAndFocusExistingProjectForPath(projectFile)?.let {
        return CompletableFuture.completedFuture(it)
      }
      return ProjectManagerEx.getInstanceEx().openProjectAsync(projectFile, openProjectOptions)
    }
    else {
      // If .idea is missing in the recent project's dir; this might mean, for instance, that 'git clean' was called.
      // Reopening such a project should be similar to opening the dir first time (and trying to import known project formats)
      // IDEA-144453 IDEA rejects opening recent project if there are no .idea subfolder
      // CPP-12106 Auto-load CMakeLists.txt on opening from Recent projects when .idea and cmake-build-debug were deleted
      return ProjectUtil.openOrImportAsync(projectFile, openProjectOptions)
    }
  }

  @Internal
  class MyProjectListener : ProjectManagerListener {
    private val manager = instanceEx

    override fun projectOpened(project: Project) {
      if (manager.disableUpdatingRecentInfo.get() || LightEdit.owns(project)) {
        return
      }

      val path = manager.getProjectPath(project)
      if (path != null) {
        manager.markPathRecent(path, project)
      }
      manager.updateLastProjectPath()
      updateSystemDockMenu()
    }

    override fun projectClosing(project: Project) {
      val app = ApplicationManagerEx.getApplicationEx()
      if (app.isExitInProgress) {
        // appClosing updates project info (even more - on project closed full screen state maybe not correct)
        return
      }

      val path = manager.getProjectPath(project) ?: return
      if (!app.isHeadlessEnvironment) {
        manager.updateProjectInfo(project, WindowManager.getInstance() as WindowManagerImpl, writLastProjectInfo = false)
      }
      manager.nameCache[path] = project.name
    }

    override fun projectClosed(project: Project) {
      if (ApplicationManagerEx.getApplicationEx().isExitInProgress) {
        // appClosing updates project info (even more - on project closed full screen state maybe not correct)
        return
      }

      val openProject = ProjectManager.getInstance().openProjects.lastOrNull()
      if (openProject != null) {
        val path = manager.getProjectPath(openProject)
        if (path != null) {
          manager.markPathRecent(path, openProject)
        }
      }
      updateSystemDockMenu()
    }
  }

  fun getRecentPaths(): List<String> {
    synchronized(stateLock) {
      state.validateRecentProjects(modCounter)
      return state.additionalInfo.keys.toList().asReversed()
    }
  }

  fun getDisplayName(path: String): String? {
    synchronized(stateLock) {
      return state.additionalInfo.get(path)?.displayName
    }
  }

  fun getProjectName(path: String): String {
    val cached = nameCache.get(path)
    if (cached != null) {
      return cached
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
    if (!GeneralSettings.getInstance().isReopenLastProject || dontReopenProjects) {
      return false
    }

    synchronized(stateLock) {
      return state.additionalInfo.values.any { it.opened }
    }
  }

  override fun reopenLastProjectsOnStart(): CompletableFuture<Boolean> {
    val openPaths = lastOpenedProjects
    if (lastOpenedProjects.isEmpty()) {
      return CompletableFuture.completedFuture(false)
    }

    disableUpdatingRecentInfo.set(true)
    // https://youtrack.jetbrains.com/issue/IDEA-121163
    // pre-allocate frames in reversed order
    val future: CompletableFuture<Boolean>
    if (openPaths.size == 1 ||
        ApplicationManager.getApplication().isHeadlessEnvironment ||
        !System.getProperty("idea.open.multi.projects.correctly", "true").toBoolean() ||
        WindowManagerEx.getInstanceEx().getFrameHelper(null) != null) {
      future = openOneByOne(java.util.List.copyOf(lastOpenedProjects), index = 0, someProjectWasOpened = false)
    }
    else {
      future = openMultiple(openPaths)
    }
    return future
      .whenComplete { _, _ ->
        WelcomeFrame.showIfNoProjectOpened(null)
        disableUpdatingRecentInfo.set(false)
      }
  }

  private fun openOneByOne(openPaths: List<Entry<String, RecentProjectMetaInfo>>,
                           index: Int,
                           someProjectWasOpened: Boolean): CompletableFuture<Boolean> {
    val (key, value) = openPaths.get(index)
    val options = OpenProjectTask(
      forceOpenInNewFrame = true,
      showWelcomeScreen = false,
      frameManager = value.frame,
      projectWorkspaceId = value.projectWorkspaceId
    )
    return openProject(Path.of(key), options)
      .thenCompose { project ->
        val nextIndex = index + 1
        if (nextIndex == openPaths.size) {
          CompletableFuture.completedFuture(someProjectWasOpened || project != null)
        }
        else {
          openOneByOne(openPaths, index = index + 1, someProjectWasOpened = someProjectWasOpened || project != null)
        }
      }
  }

  override fun suggestNewProjectLocation(): String {
    return ProjectUtil.getBaseDir()
  }

  // open for Rider
  protected open fun isValidProjectPath(file: Path) = ProjectUtil.isValidProjectPath(file)

  protected fun openMultiple(openPaths: List<Entry<String, RecentProjectMetaInfo>>): CompletableFuture<Boolean> {
    val reversedList = ArrayList<Pair<Path, RecentProjectMetaInfo>>(openPaths.size)
    for (entry in openPaths.reversed()) {
      val path = Path.of(entry.key)
      if (entry.value.frame == null || !isValidProjectPath(path)) {
        return CompletableFuture.completedFuture(false)
      }

      reversedList.add(Pair(path, entry.value))
    }

    // ok, no non-existent project paths and every info has a frame
    val first = openPaths.first().value
    val taskList = ArrayList<Pair<Path, OpenProjectTask>>(openPaths.size)
    return CompletableFuture.runAsync({
      for ((path, info) in reversedList) {
        val frameInfo = info.frame!!
        val isActive = info == first
        val ideFrame = createNewProjectFrame(forceDisableAutoRequestFocus = !isActive, frameInfo)
        info.frameTitle?.let {
          ideFrame.title = it
        }
        val frameManager = if (isActive) {
          MyActiveProjectUiFrameManager(ideFrame, taskList, frameInfo.fullScreen)
        }
        else {
          MyProjectUiFrameManager(ideFrame)
        }
        taskList.add(Pair(path, OpenProjectTask(
          forceOpenInNewFrame = true,
          showWelcomeScreen = false,
          frameManager = frameManager,
          projectWorkspaceId = info.projectWorkspaceId,
        )))
      }
    }, ApplicationManager.getApplication()::invokeLater)
      .thenApplyAsync({
        taskList.reverse()

        val projectManager = ProjectManagerEx.getInstanceEx()
        val iterator = taskList.iterator()
        while (iterator.hasNext()) {
          val entry = iterator.next()
          try {
            projectManager.openProject(entry.first, entry.second)
          }
          catch (e: Exception) {
            @Suppress("SSBasedInspection")
            (entry.second.frameManager as MyProjectUiFrameManager?)?.dispose()
            while (iterator.hasNext()) {
              @Suppress("SSBasedInspection")
              (iterator.next().second.frameManager as MyProjectUiFrameManager?)?.dispose()
            }

            throw e
          }
        }
        true
      }, ForkJoinPool.commonPool())
  }

  protected val lastOpenedProjects: List<Entry<String, RecentProjectMetaInfo>>
    get() = synchronized(stateLock) {
      return state.additionalInfo.entries.filter { it.value.opened }.asReversed()
    }

  override fun getGroups(): List<ProjectGroup> {
    synchronized(stateLock) {
      return Collections.unmodifiableList(state.groups)
    }
  }

  override fun addGroup(group: ProjectGroup) {
    synchronized(stateLock) {
      if (!state.groups.contains(group)) {
        state.groups.add(group)
      }
    }
  }

  override fun removeGroup(group: ProjectGroup) {
    synchronized(stateLock) {
      for (path in group.projects) {
        state.additionalInfo.remove(path)
        for (anotherGroup in state.groups) {
          if (anotherGroup !== group) {
            group.removeProject(path)
          }
        }
      }

      state.groups.remove(group)
      modCounter.incrementAndGet()
    }
  }

  override fun getModificationCount(): Long {
    synchronized(stateLock) {
      return modCounter.get() + state.modificationCount
    }
  }

  private fun updateProjectInfo(project: Project, windowManager: WindowManagerImpl, writLastProjectInfo: Boolean) {
    val frameHelper = windowManager.getFrameHelper(project)
    if (frameHelper == null) {
      LOG.warn("Cannot update frame info (project=${project.name}, reason=frame helper is not found)")
      return
    }

    val frame = frameHelper.frame
    if (frame == null) {
      LOG.warn("Cannot update frame info (project=${project.name}, reason=frame is null)")
      return
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
      if (writLastProjectInfo) {
        writeInfoFile(frameInfo, frame)
      }

      if (workspaceId != null && Registry.`is`("ide.project.loading.show.last.state")) {
        takeASelfie(frameHelper, workspaceId)
      }
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
    val frame = frameHelper.frame!!
    val width = frame.width
    val height = frame.height
    val image = ImageUtil.createImage(frame.graphicsConfiguration, width, height, BufferedImage.TYPE_INT_ARGB)
    UISettings.setupAntialiasing(image.graphics)
    frame.paint(image.graphics)
    val selfieFile = ProjectSelfieUtil.getSelfieLocation(workspaceId)
    // must be file, because for Path no optimized impl (output stream must be not used, otherwise cache file will be created by JDK)
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
      instanceEx.updateLastProjectPath()
    }

    override fun appClosing() {
      if (ApplicationManager.getApplication().isHeadlessEnvironment) {
        return
      }

      val openProjects = ProjectUtil.getOpenProjects()
      // do not delete info file if ProjectManager not created - it means that it was simply not loaded, so, unlikely something is changed
      if (openProjects.isEmpty()) {
        if (!isUseProjectFrameAsSplash()) {
          Files.deleteIfExists(getLastProjectFrameInfoFile())
        }
      }
      else {
        val manager = instanceEx
        val windowManager = WindowManager.getInstance() as WindowManagerImpl
        for ((index, project) in openProjects.withIndex()) {
          manager.updateProjectInfo(project, windowManager, writLastProjectInfo = index == 0)
        }
      }
    }

    override fun appWillBeClosed(isRestart: Boolean) {

    }

    override fun projectFrameClosed() {
      // ProjectManagerListener.projectClosed cannot be used to call updateLastProjectPath,
      // because called even if project closed on app exit
      instanceEx.updateLastProjectPath()
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
  return JpsPathUtil.readProjectName(projectDir) ?:
         JpsPathUtil.getDefaultProjectName(projectDir)
}

private fun getLastProjectFrameInfoFile() = appSystemDir.resolve("lastProjectFrameInfo")

private fun convertToSystemIndependentPaths(list: MutableList<String>) {
  list.replaceAll {
    FileUtilRt.toSystemIndependentName(it)
  }
}

@Service
@State(name = "RecentDirectoryProjectsManager",
       storages = [Storage(value = "recentProjectDirectories.xml", roamingType = RoamingType.DISABLED, deprecated = true)],
       reportStatistic = false)
private class OldRecentDirectoryProjectsManager : PersistentStateComponent<RecentProjectManagerState> {
  var loadedState: RecentProjectManagerState? = null

  companion object {
    private val emptyState = RecentProjectManagerState()
  }

  override fun loadState(state: RecentProjectManagerState) {
    loadedState = state
  }

  override fun getState() = emptyState
}

private open class MyProjectUiFrameManager(val frame: IdeFrameImpl) : ProjectUiFrameManager {
  override var frameHelper: ProjectFrameHelper? = null

  override fun getComponent(): JComponent = frame.rootPane

  override fun init(allocator: ProjectUiFrameAllocator) {
    // done by active frame manager for all frames
  }

  fun dispose() {
    frame.dispose()
  }

  override fun projectOpened(project: Project) {
    // allow to grab focus only after fully opened
    val ref = WeakReference(frame)
    StartupManager.getInstance(project).runAfterOpened(Runnable {
      if (ref.get() != null) {
        ApplicationManager.getApplication().invokeLater(Runnable {
          ref.get()?.isAutoRequestFocus = true
        }, ModalityState.NON_MODAL, project.disposed)
      }
    })
  }
}

private class MyActiveProjectUiFrameManager(frame: IdeFrameImpl,
                                            tasks: List<Pair<Path, OpenProjectTask>>,
                                            private val isFullScreen: Boolean) : MyProjectUiFrameManager(frame) {
  companion object {
    private fun doInit(isFullScreen: Boolean, tasks: List<Pair<Path, OpenProjectTask>>) {
      for (task in tasks.reversed()) {
        val manager = task.second.frameManager as MyProjectUiFrameManager
        val frame = manager.frame
        val frameHelper = ProjectFrameHelper(frame, null)
        frame.isVisible = true

        if (isFullScreen && manager is MyActiveProjectUiFrameManager && FrameInfoHelper.isFullScreenSupportedInCurrentOs()) {
          frameHelper.toggleFullScreen(true)
        }

        manager.frameHelper = frameHelper
      }

      for (task in tasks) {
        (task.second.frameManager as MyProjectUiFrameManager).frameHelper!!.init()
      }
    }
  }

  private var tasks: List<Pair<Path, OpenProjectTask>>? = tasks

  override fun getComponent(): JComponent = frame.rootPane

  override fun init(allocator: ProjectUiFrameAllocator) {
    if (frameHelper != null) {
      return
    }

    ApplicationManager.getApplication().invokeLater {
      if (!allocator.cancelled) {
        runActivity("project frame initialization") {
          val tasks = this.tasks!!
          this.tasks = null
          doInit(isFullScreen, tasks)
        }
      }
    }
  }

  override fun projectOpened(project: Project) {
    // override default impl of MyProjectUiFrameManager - for active window we don't force setting
    // isAutoRequestFocus to false, so, no need to set it to true on project open
  }
}

private fun updateSystemDockMenu() {
  if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
    SystemDock.updateMenu()
  }
}
