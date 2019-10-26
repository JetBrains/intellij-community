// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.configurationStore.readProjectNameFile
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.platform.ProjectSelfieUtil
import com.intellij.project.stateStore
import com.intellij.util.PathUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.toArray
import com.intellij.util.io.isDirectory
import com.intellij.util.io.outputStream
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.io.write
import com.intellij.util.pooledThreadSingleAlarm
import com.intellij.util.ui.UIUtil
import gnu.trove.THashMap
import gnu.trove.THashSet
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.*
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

private const val MAX_PROJECTS_IN_MAIN_MENU = 6

/**
 * Used directly by IntelliJ IDEA.
 *
 * @see RecentDirectoryProjectsManager base class primary for minor IDEs on IntelliJ Platform
 */
@State(name = "RecentProjectsManager", storages = [Storage(value = "recentProjects.xml", roamingType = RoamingType.DISABLED)])
open class RecentProjectsManagerBase : RecentProjectsManager(), PersistentStateComponent<RecentProjectManagerState>, ModificationTracker {
  companion object {
    @JvmStatic
    val instanceEx: RecentProjectsManagerBase
      get() = getInstance() as RecentProjectsManagerBase

    @JvmStatic
    fun isFileSystemPath(path: String): Boolean {
      return path.indexOf('/') != -1 || path.indexOf('\\') != -1
    }
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

  override fun getState(): RecentProjectManagerState {
    synchronized(stateLock) {
      @Suppress("DEPRECATION")
      state.recentPaths.clear()
      @Suppress("DEPRECATION")
      state.recentPaths.addAll(ContainerUtil.reverse(state.additionalInfo.keys.toList()))
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

  override fun loadState(state: RecentProjectManagerState) {
    synchronized(stateLock) {
      this.state = state
      state.pid = null
      @Suppress("DEPRECATION")
      val openPaths = state.openPaths
      if (openPaths.isNotEmpty()) {
        migrateOpenPaths(openPaths)
      }
    }
  }

  // reorder according to openPaths order and mark as opened
  private fun migrateOpenPaths(openPaths: MutableList<String>) {
    val oldInfoMap: MutableMap<String, RecentProjectMetaInfo> = THashMap()
    for (path in openPaths) {
      val info = state.additionalInfo.remove(path)
      if (info != null) {
        oldInfoMap.put(path, info)
      }
    }

    for (path in ContainerUtil.reverse(openPaths)) {
      val info = oldInfoMap.get(path) ?: RecentProjectMetaInfo()
      info.opened = true
      state.additionalInfo.put(path, info)
    }
    openPaths.clear()
    modCounter.incrementAndGet()
  }

  override fun removePath(path: String?) {
    if (path == null) {
      return
    }

    synchronized(stateLock) {
      state.additionalInfo.remove(path)
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
    val newValue = PathUtil.toSystemIndependentName(StringUtil.nullize(value, true))
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
        val path = getProjectPath(project!!)
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

  private fun getDuplicateProjectNames(openedPaths: Set<String>, recentPaths: Set<String>): Set<String> {
    val names: MutableSet<String> = THashSet()
    val duplicates: MutableSet<String> = THashSet()
    for (path in ContainerUtil.union(openedPaths, recentPaths)) {
      val name = getProjectName(path)
      if (!names.add(name)) {
        duplicates.add(name)
      }
    }
    return duplicates
  }

  override fun getRecentProjectsActions(forMainMenu: Boolean): Array<AnAction?> {
    return getRecentProjectsActions(forMainMenu, false)
  }

  override fun getRecentProjectsActions(forMainMenu: Boolean, useGroups: Boolean): Array<AnAction?> {
    var paths: MutableSet<String>
    synchronized(stateLock) {
      state.validateRecentProjects(modCounter)
      paths = LinkedHashSet(ContainerUtil.reverse(state.additionalInfo.keys.toList()))
    }

    val openedPaths = THashSet<String>()
    for (openProject in ProjectManager.getInstance().openProjects) {
      ContainerUtil.addIfNotNull(openedPaths, getProjectPath(openProject))
    }

    val actions: MutableList<AnAction?> = SmartList()
    val duplicates = getDuplicateProjectNames(openedPaths, paths)
    if (useGroups) {
      val groups = synchronized(stateLock) {
        state.groups.toMutableList()
      }

      val projectPaths: List<String?> = ArrayList(paths)
      groups.sortWith(object : Comparator<ProjectGroup> {
        override fun compare(o1: ProjectGroup, o2: ProjectGroup): Int {
          val ind1 = getGroupIndex(o1)
          val ind2 = getGroupIndex(o2)
          return if (ind1 == ind2) StringUtil.naturalCompare(o1.name, o2.name) else ind1 - ind2
        }

        private fun getGroupIndex(group: ProjectGroup): Int {
          var index = Integer.MAX_VALUE
          for (path in group.projects) {
            val i = projectPaths.indexOf(path)
            if (i in 0 until index) {
              index = i
            }
          }
          return index
        }
      })

      for (group in groups) {
        paths.removeAll(group.projects)
      }

      for (group in groups) {
        val children: MutableList<AnAction?> = ArrayList()
        for (path in group.projects) {
          children.add(createOpenAction(path!!, duplicates))
          if (forMainMenu && children.size >= MAX_PROJECTS_IN_MAIN_MENU) {
            break
          }
        }
        actions.add(ProjectGroupActionGroup(group, children))
        if (group.isExpanded) {
          actions.addAll(children)
        }
      }
    }

    for (path in paths) {
      actions.add(createOpenAction(path, duplicates))
    }

    return when {
      actions.isEmpty() -> AnAction.EMPTY_ARRAY
      else -> actions.toArray(AnAction.EMPTY_ARRAY)
    }
  }

  // for Rider
  @Suppress("MemberVisibilityCanBePrivate")
  protected open fun createOpenAction(path: String, duplicates: Set<String>): AnAction {
    var displayName = synchronized(stateLock) {
      state.additionalInfo.get(path)?.displayName
    }

    val projectName = getProjectName(path)

    if (displayName.isNullOrBlank()) {
      displayName = if (duplicates.contains(projectName)) FileUtil.toSystemDependentName(path) else projectName
    }

    // It's better don't to remove non-existent projects. Sometimes projects stored
    // on USB-sticks or flash-cards, and it will be nice to have them in the list
    // when USB device or SD-card is mounted
    return ReopenProjectAction(path, projectName, displayName)
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

  protected open fun getProjectPath(project: Project): String? {
    return PathUtil.toSystemIndependentName(project.presentableUrl)
  }

  // open for Rider
  open fun openProject(projectFile: Path, openProjectOptions: OpenProjectTask): Project? {
    val existing = ProjectUtil.findAndFocusExistingProjectForPath(projectFile)
    return when {
      existing != null -> existing
      ProjectUtil.isValidProjectPath(projectFile) -> {
        PlatformProjectOpenProcessor.openExistingProject(projectFile, projectFile, openProjectOptions)
      }
      else -> {
        // If .idea is missing in the recent project's dir; this might mean, for instance, that 'git clean' was called.
        // Reopening such a project should be similar to opening the dir first time (and trying to import known project formats)
        // IDEA-144453 IDEA rejects opening recent project if there are no .idea subfolder
        // CPP-12106 Auto-load CMakeLists.txt on opening from Recent projects when .idea and cmake-build-debug were deleted
        ProjectUtil.openOrImport(projectFile, openProjectOptions)
      }
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
        val path = manager.getProjectPath(openProject!!)
        if (path != null) {
          manager.markPathRecent(path, openProject)
        }
      }
      updateSystemDockMenu()
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
    if (!GeneralSettings.getInstance().isReopenLastProject) {
      return false
    }

    synchronized(stateLock) {
      return state.additionalInfo.values.any { it.opened }
    }
  }

  override fun reopenLastProjectsOnStart() {
    val openPaths = lastOpenedProjects
    var someProjectWasOpened = false
    for ((key, value) in openPaths) {
      val options = OpenProjectTask(forceOpenInNewFrame = true, projectToClose = null)
      options.frame = value.frame
      options.projectWorkspaceId = value.projectWorkspaceId
      options.showWelcomeScreen = false
      options.sendFrameBack = someProjectWasOpened
      val project = openProject(Paths.get(key), options)
      if (!someProjectWasOpened) {
        someProjectWasOpened = project != null
      }
    }

    if (!someProjectWasOpened) {
      WelcomeFrame.showIfNoProjectOpened()
    }
  }

  protected val lastOpenedProjects: List<Entry<String, RecentProjectMetaInfo>>
    get() = synchronized(stateLock) {
      return ContainerUtil.reverse(ContainerUtil.findAll(state.additionalInfo.entries) { it.value.opened })
    }

  override fun getGroups(): List<ProjectGroup?> {
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
      state.groups.remove(group)
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

  val nameFile = file.resolve(Project.DIRECTORY_STORE_FOLDER).resolve(ProjectImpl.NAME_FILE)
  try {
    val result = readProjectNameFile(nameFile)
    if (result != null) {
      return result
    }
  }
  catch (ignore: NoSuchFileException) { }
  catch (ignored: IOException) { }

  return file.fileName?.toString() ?: "<unknown>"
}

private fun getLastProjectFrameInfoFile() = appSystemDir.resolve("lastProjectFrameInfo")