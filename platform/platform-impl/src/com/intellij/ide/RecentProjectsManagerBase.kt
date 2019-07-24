// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.configurationStore.readProjectNameFile
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.ProjectFrameBounds.Companion.getInstance
import com.intellij.openapi.wm.impl.SystemDock
import com.intellij.openapi.wm.impl.WindowManagerImpl
import com.intellij.openapi.wm.impl.welcomeScreen.RecentProjectPanel
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.project.stateStore
import com.intellij.util.PathUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.toArray
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.pooledThreadSingleAlarm
import gnu.trove.THashMap
import gnu.trove.THashSet
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import javax.swing.Icon
import kotlin.collections.Map.Entry
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

private const val MAX_PROJECTS_IN_MAIN_MENU = 6

/**
 * Used directly by IntelliJ IDEA.
 *
 * @see RecentDirectoryProjectsManager base class primary for minor IDEs on IntelliJ Platform
 */
@State(name = "RecentProjectsManager", storages = [Storage(value = "recentProjects.xml", roamingType = RoamingType.DISABLED)])
open class RecentProjectsManagerBase : RecentProjectsManager(), PersistentStateComponent<RecentProjectManagerState?>, ModificationTracker {
  companion object {
    @JvmStatic
    val instanceEx: RecentProjectsManagerBase
      get() = getInstance() as RecentProjectsManagerBase
  }

  private val modCounter = AtomicLong()
  private val myProjectIconHelper = RecentProjectIconHelper()
  private val namesToResolve: MutableSet<String> = THashSet(MAX_PROJECTS_IN_MAIN_MENU)

  private val nameCache: MutableMap<String, String>? = Collections.synchronizedMap(THashMap())

  private val nameResolver = pooledThreadSingleAlarm(50) {
    var paths: Set<String>
    synchronized(namesToResolve) {
      paths = THashSet(namesToResolve)
      namesToResolve.clear()
    }
    for (p in paths) {
      nameCache!!.put(p, readProjectName(p))
    }
  }

  private val stateLock = Any()
  private var myState = RecentProjectManagerState()

  override fun getState(): RecentProjectManagerState? {
    synchronized(stateLock) {
      @Suppress("DEPRECATION")
      myState.recentPaths.clear()
      @Suppress("DEPRECATION")
      myState.recentPaths.addAll(ContainerUtil.reverse(myState.additionalInfo.keys.toList()))
      if (myState.pid == null) {
        //todo[kb] uncomment when we will fix JRE-251 The pid is needed for 3rd parties like Toolbox App to show the project is open now
        myState.pid = null
      }
      return myState
    }
  }

  fun getProjectMetaInfo(file: Path): RecentProjectMetaInfo? {
    synchronized(stateLock) {
      return myState.additionalInfo.get(file.systemIndependentPath)
    }
  }

  override fun loadState(state: RecentProjectManagerState) {
    synchronized(stateLock) {
      myState = state
      myState.pid = null
      @Suppress("DEPRECATION")
      val openPaths = myState.openPaths
      if (!openPaths.isEmpty()) {
        migrateOpenPaths(openPaths)
      }
    }
  }

  // reorder according to openPaths order and mark as opened
  private fun migrateOpenPaths(openPaths: MutableList<String>) {
    val oldInfoMap: MutableMap<String, RecentProjectMetaInfo> = THashMap()
    for (path in openPaths) {
      val info = myState.additionalInfo.remove(path)
      if (info != null) {
        oldInfoMap[path] = info
      }
    }

    for (path in ContainerUtil.reverse(openPaths)) {
      val info = oldInfoMap[path] ?: RecentProjectMetaInfo()
      info.opened = true
      myState.additionalInfo[path] = info
    }
    openPaths.clear()
    modCounter.incrementAndGet()
  }

  override fun removePath(path: String?) {
    if (path == null) {
      return
    }

    synchronized(stateLock) {
      myState.additionalInfo.remove(path)
      for (group in myState.groups) {
        if (group.removeProject(path)) {
          modCounter.incrementAndGet()
        }
      }
    }
  }

  override fun hasPath(path: String?): Boolean {
    synchronized(stateLock) {
      return myState.additionalInfo.containsKey(path)
    }
  }

  /**
   * @return a path pointing to a directory where the last project was created or null if not available
   */
  override fun getLastProjectCreationLocation(): String? {
    synchronized(stateLock) {
      return myState.lastProjectLocation
    }
  }

  override fun setLastProjectCreationLocation(value: String?) {
    val newValue = PathUtil.toSystemIndependentName(StringUtil.nullize(value, true))
    synchronized(stateLock) {
      myState.lastProjectLocation = newValue
    }
  }

  override fun updateLastProjectPath() {
    val openProjects: Array<Project?> = ProjectManager.getInstance().openProjects
    synchronized(stateLock) {
      for (info in myState.additionalInfo.values) {
        info.opened = false
      }

      for (project in openProjects) {
        val path = getProjectPath(project!!)
        val info = if (path == null) null else myState.additionalInfo.get(path)
        if (info != null) {
          info.opened = true
          info.projectOpenTimestamp = System.currentTimeMillis()
          info.displayName = getProjectDisplayName(project)
        }
      }
      myState.validateRecentProjects(modCounter)
    }
  }

  protected open fun getProjectDisplayName(project: Project): String? = null

  fun getProjectIcon(path: String, isDark: Boolean): Icon? {
    return myProjectIconHelper.getProjectIcon(path, isDark)
  }

  fun getProjectOrAppIcon(path: String): Icon {
    return myProjectIconHelper.getProjectOrAppIcon(path)
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
      myState.validateRecentProjects(modCounter)
      paths = LinkedHashSet(ContainerUtil.reverse(myState.additionalInfo.keys.toList()))
    }

    val openedPaths = THashSet<String>()
    for (openProject in ProjectManager.getInstance().openProjects) {
      ContainerUtil.addIfNotNull(openedPaths, getProjectPath(openProject!!))
    }

    val actions: MutableList<AnAction?> = SmartList()
    val duplicates = getDuplicateProjectNames(openedPaths, paths)
    if (useGroups) {
      val groups = synchronized(stateLock) {
        myState.groups.toMutableList()
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
    val projectName = getProjectName(path)
    var displayName: String?
    synchronized(stateLock) {
      displayName = myState.additionalInfo.get(path)?.displayName
    }

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
      for (group in myState.groups) {
        if (group.markProjectFirst(path)) {
          modCounter.incrementAndGet()
          break
        }
      }

      // remove instead of get to re-order
      val info = myState.additionalInfo.remove(path) ?: RecentProjectMetaInfo()
      myState.additionalInfo[path] = info
      modCounter.incrementAndGet()
      val appInfo = ApplicationInfoEx.getInstanceEx()
      info.displayName = getProjectDisplayName(project)
      info.projectWorkspaceId = project.stateStore.projectWorkspaceId
      info.frame = getInstance(project).state
      info.build = appInfo!!.build.asString()
      info.productionCode = appInfo.build.productCode
      info.eap = appInfo.isEAP
      info.binFolder = FileUtilRt.toSystemIndependentName(
        PathManager.getBinPath())
      info.projectOpenTimestamp = System.currentTimeMillis()
      info.buildTimestamp = appInfo.buildDate.timeInMillis
      info.metadata = getRecentProjectMetadata(path, project)
    }
  }

  @Suppress("MemberVisibilityCanBePrivate", "UNUSED_PARAMETER")
  // for Rider
  protected open fun getRecentProjectMetadata(path: String, project: Project): String? {
    return null
  }

  protected open fun getProjectPath(project: Project): String? {
    return PathUtil.toSystemIndependentName(project.presentableUrl)
  }

  fun doOpenProject(projectPath: String, openProjectOptions: OpenProjectTask): Project? {
    return doOpenProject(Paths.get(projectPath), openProjectOptions)
  }

  fun doOpenProject(projectFile: Path, openProjectOptions: OpenProjectTask): Project? {
    val existing = ProjectUtil.findAndFocusExistingProjectForPath(projectFile)
    if (existing != null) {
      return existing
    }

    if (ProjectUtil.isValidProjectPath(projectFile)) {
      return PlatformProjectOpenProcessor.openExistingProject(projectFile, projectFile, openProjectOptions, null)
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
        manager.updateProjectInfo(project,
                                  (WindowManager.getInstance() as WindowManagerImpl))
      }
      manager.nameCache!![path] = project.name
    }

    override fun projectClosed(project: Project) {
      val openProjects: Array<Project?> = ProjectManager.getInstance().openProjects
      if (openProjects.isNotEmpty()) {
        val openProject = openProjects[openProjects.size - 1]
        val path = manager.getProjectPath(openProject!!)
        if (path != null) {
          manager.markPathRecent(path, openProject)
        }
      }
      updateSystemDockMenu()
    }

    companion object {
      private fun updateSystemDockMenu() {
        if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
          SystemDock.updateMenu()
        }
      }
    }
  }

  fun getProjectName(path: String): String {
    val cached = nameCache!!.get(path)
    if (cached != null) {
      return cached
    }

    nameResolver.cancel()
    synchronized(namesToResolve) { namesToResolve.add(path) }
    nameResolver.request()
    val name = PathUtilRt.getFileName(path)
    return if (path.endsWith(".ipr")) FileUtilRt.getNameWithoutExtension(name) else name
  }

  override fun willReopenProjectOnStart(): Boolean {
    if (!GeneralSettings.getInstance().isReopenLastProject) {
      return false
    }

    synchronized(stateLock) {
      for (info in myState.additionalInfo.values) {
        if (info.opened) {
          return true
        }
      }
    }
    return false
  }

  override fun reopenLastProjectsOnStart() {
    if (!GeneralSettings.getInstance().isReopenLastProject) {
      return
    }

    val openPaths = lastOpenedProjects
    var someProjectWasOpened = false
    for ((key, value) in openPaths) {
      // https://youtrack.jetbrains.com/issue/IDEA-166321
      val options = OpenProjectTask(forceOpenInNewFrame = true, projectToClose = null, frame = value.frame,  projectWorkspaceId = value.projectWorkspaceId)
      options.showWelcomeScreenIfNoProjectOpened = false
      options.sendFrameBack = someProjectWasOpened
      val project = doOpenProject(key, options)
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
      return ContainerUtil.reverse(ContainerUtil.findAll(myState.additionalInfo.entries) { it.value.opened })
    }

  override fun getGroups(): List<ProjectGroup?> {
    synchronized(stateLock) { return Collections.unmodifiableList(myState.groups) }
  }

  override fun addGroup(group: ProjectGroup) {
    synchronized(stateLock) {
      if (!myState.groups.contains(group)) {
        myState.groups.add(group)
      }
    }
  }

  override fun removeGroup(group: ProjectGroup) {
    synchronized(stateLock) { myState.groups.remove(group) }
  }

  override fun getModificationCount(): Long {
    synchronized(stateLock) { return modCounter.get() + myState.modificationCount }
  }

  private fun updateProjectInfo(project: Project, windowManager: WindowManagerImpl) {
    val frame = windowManager.getFrame(project) ?: return
    val workspaceId = project.stateStore.projectWorkspaceId

    // ensure that last closed project frame bounds will be used as newly created project frame bounds (if will be no another focused opened project)
    val frameInfo = getInstance(project).getActualFrameInfoInDeviceSpace(frame, windowManager)
    val path = getProjectPath(project)
    synchronized(stateLock) {
      val info = myState.additionalInfo[path]
      if (info != null) {
        if (info.frame !== frameInfo) {
          info.frame = frameInfo
        }
        info.projectWorkspaceId = workspaceId
      }
    }
    if (workspaceId != null && Registry.`is`("ide.project.loading.show.last.state")) {
      frame.takeASelfie(workspaceId)
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
      for (project in ProjectManager.getInstance().openProjects) {
        manager.updateProjectInfo(project!!, windowManager)
      }
    }

    override fun projectFrameClosed() {
      // ProjectManagerListener.projectClosed cannot be used to call updateLastProjectPath,
      // because called even if project closed on app exit

      instanceEx.updateLastProjectPath()
    }
  }
}

private fun readProjectName(path: String): String {
  if (!RecentProjectPanel.isFileSystemPath(path)) return path
  val file = Paths.get(path)


  if (!Files.isDirectory(file)) {
    return FileUtilRt.getNameWithoutExtension(file.fileName.toString())
  }
  val nameFile = file.resolve(Project.DIRECTORY_STORE_FOLDER).resolve(
    ProjectImpl.NAME_FILE)
  try {
    val result = readProjectNameFile(nameFile)
    if (result != null) {
      return result
    }
  }
  catch (ignore: NoSuchFileException) {
    // ignore not found

  }
  catch (ignored: IOException) {
  }
  return file.fileName.toString()
}