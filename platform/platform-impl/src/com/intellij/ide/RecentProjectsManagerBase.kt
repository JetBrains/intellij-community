// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.*
import com.intellij.openapi.components.State
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.*
import com.intellij.platform.ProjectSelfieUtil
import com.intellij.project.stateStore
import com.intellij.util.PathUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.io.isDirectory
import com.intellij.util.io.outputStream
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.io.write
import com.intellij.util.pooledThreadSingleAlarm
import com.intellij.util.text.nullize
import com.intellij.util.ui.UIUtil
import gnu.trove.THashMap
import gnu.trove.THashSet
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.jps.util.JpsPathUtil
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
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
  private val namesToResolve: MutableSet<String> = THashSet(MAX_PROJECTS_IN_MAIN_MENU)

  private val nameCache: MutableMap<String, String> = Collections.synchronizedMap(THashMap())

  private val nameResolver = pooledThreadSingleAlarm(50) {
    var paths: Set<String>
    synchronized(namesToResolve) {
      paths = THashSet(namesToResolve)
      namesToResolve.clear()
    }
    for (p in paths) {
      nameCache.put(p, readProjectName(p))
    }
  }

  private val stateLock = Any()
  private var state = RecentProjectManagerState()

  final override fun getState(): RecentProjectManagerState {
    synchronized(stateLock) {
      // https://youtrack.jetbrains.com/issue/TBX-3756
      @Suppress("DEPRECATION")
      state.recentPaths.clear()
      @Suppress("DEPRECATION")
      state.recentPaths.addAll(state.additionalInfo.keys.reversed())
      if (state.pid == null) {
        //todo[kb] uncomment when we will fix JRE-251 The pid is needed for 3rd parties like Toolbox App to show the project is open now
        state.pid = null
      }
      return state
    }
  }

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
          newAdditionalInfo.put(recentPath, value)
        }

        if (newAdditionalInfo != state.additionalInfo) {
          state.additionalInfo.clear()
          state.additionalInfo.putAll(newAdditionalInfo)
        }
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
        oldInfoMap.put(path, info)
      }
    }

    for (path in openPaths.asReversed()) {
      val info = oldInfoMap.get(path) ?: RecentProjectMetaInfo()
      info.opened = true
      state.additionalInfo.put(path, info)
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
    val newValue = PathUtil.toSystemIndependentName(value.nullize(nullizeSpaces = true))
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

  fun getProjectIcon(path: String, isDark: Boolean): Icon? {
    return projectIconHelper.getProjectIcon(path, isDark)
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
      state.additionalInfo.put(path, info)
      modCounter.incrementAndGet()
      val appInfo = ApplicationInfoEx.getInstanceEx()
      info.displayName = getProjectDisplayName(project)
      info.projectWorkspaceId = project.stateStore.projectWorkspaceId
      info.frame = ProjectFrameBounds.getInstance(project).state
      info.build = appInfo!!.build.asString()
      info.productionCode = appInfo.build.productCode
      info.eap = appInfo.isEAP
      info.binFolder = FileUtilRt.toSystemIndependentName(PathManager.getBinPath())
      info.projectOpenTimestamp = System.currentTimeMillis()
      info.buildTimestamp = appInfo.buildDate.timeInMillis
      info.metadata = getRecentProjectMetadata(path, project)
    }
  }

  @Suppress("MemberVisibilityCanBePrivate", "UNUSED_PARAMETER")
  // for Rider
  protected open fun getRecentProjectMetadata(path: String, project: Project): String? = null

  open fun getProjectPath(project: Project): String? {
    return FileUtilRt.toSystemIndependentName(project.presentableUrl ?: return null)
  }

  // open for Rider
  open fun openProject(projectFile: Path, openProjectOptions: OpenProjectTask): Project? {
    if (ProjectUtil.isValidProjectPath(projectFile)) {
      return ProjectUtil.findAndFocusExistingProjectForPath(projectFile)
             ?: ProjectManagerEx.getInstanceEx().openProject(projectFile, openProjectOptions)
    }
    else {
      // If .idea is missing in the recent project's dir; this might mean, for instance, that 'git clean' was called.
      // Reopening such a project should be similar to opening the dir first time (and trying to import known project formats)
      // IDEA-144453 IDEA rejects opening recent project if there are no .idea subfolder
      // CPP-12106 Auto-load CMakeLists.txt on opening from Recent projects when .idea and cmake-build-debug were deleted
      return ProjectUtil.openOrImport(projectFile, openProjectOptions)
    }
  }

  @Internal
  class MyProjectListener : ProjectManagerListener {
    private val manager = instanceEx

    companion object {
      private fun updateSystemDockMenu() {
        if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
          SystemDock.updateMenu()
        }
      }
    }

    override fun projectOpened(project: Project) {
      val path = manager.getProjectPath(project)
      if (path != null) {
        manager.markPathRecent(path, project)
      }
      manager.updateLastProjectPath()
      updateSystemDockMenu()
    }

    override fun projectClosing(project: Project) {
      val path = manager.getProjectPath(project) ?: return
      if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
        manager.updateProjectInfo(project, WindowManager.getInstance() as WindowManagerImpl, writLastProjectInfo = false)
      }
      manager.nameCache.put(path, project.name)
    }

    override fun projectClosed(project: Project) {
      val openProjects = ProjectManager.getInstance().openProjects
      if (openProjects.isNotEmpty()) {
        val openProject = openProjects[openProjects.size - 1]
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

  override fun reopenLastProjectsOnStart(): Boolean {
    val openPaths = lastOpenedProjects
    var someProjectWasOpened = false
    for ((key, value) in openPaths) {
      val options = OpenProjectTask(forceOpenInNewFrame = true, sendFrameBack = someProjectWasOpened, showWelcomeScreen = false, frame = value.frame, projectWorkspaceId = value.projectWorkspaceId)
      val project = openProject(Paths.get(key), options)
      if (!someProjectWasOpened) {
        someProjectWasOpened = project != null
      }
    }

    return someProjectWasOpened
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
    val frame = windowManager.getFrameHelper(project) ?: return
    val workspaceId = project.stateStore.projectWorkspaceId

    // ensure that last closed project frame bounds will be used as newly created project frame bounds (if will be no another focused opened project)
    val frameInfo = ProjectFrameBounds.getInstance(project).getActualFrameInfoInDeviceSpace(frame, windowManager)
    val path = getProjectPath(project)
    synchronized(stateLock) {
      val info = state.additionalInfo.get(path)
      if (info != null) {
        if (info.frame !== frameInfo) {
          info.frame = frameInfo
        }
        info.projectWorkspaceId = workspaceId
      }
    }

    logger<RecentProjectsManager>().runAndLogException {
      if (writLastProjectInfo) {
        writeInfoFile(frameInfo, frame.frame)
      }

      if (workspaceId != null && Registry.`is`("ide.project.loading.show.last.state")) {
        takeASelfie(frame, workspaceId)
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
    val frame = frameHelper.frame
    val width = frame.width
    val height = frame.height
    val image = UIUtil.createImage(frame, width, height, BufferedImage.TYPE_INT_ARGB)
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

  @Internal
  class MyAppLifecycleListener : AppLifecycleListener {
    override fun projectOpenFailed() {
      instanceEx.updateLastProjectPath()
    }

    override fun appClosing() {
      if (ApplicationManager.getApplication().isHeadlessEnvironment) {
        return
      }

      val windowManager = WindowManager.getInstance() as WindowManagerImpl
      val manager = instanceEx
      val openProjects = ProjectUtil.getOpenProjects()
      // do not delete info file if ProjectManager not created - it means that it was simply not loaded, so, unlikely something is changed
      if (openProjects.isEmpty() || !isUseProjectFrameAsSplash()) {
        Files.deleteIfExists(getLastProjectFrameInfoFile())
      }

      for ((index, project) in openProjects.withIndex()) {
        manager.updateProjectInfo(project, windowManager, writLastProjectInfo = index == 0)
      }
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

  val file = Paths.get(path)
  if (!file.isDirectory()) {
    return FileUtilRt.getNameWithoutExtension(file.fileName.toString())
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
@State(name = "RecentDirectoryProjectsManager", storages = [Storage(value = "recentProjectDirectories.xml", roamingType = RoamingType.DISABLED, deprecated = true)])
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