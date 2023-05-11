// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.ProjectTopics
import com.intellij.configurationStore.BatchUpdateListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileTypeEvent
import com.intellij.openapi.fileTypes.FileTypeListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.ModuleEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.*
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.LocalFileSystem.WatchRequest
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.impl.VirtualFilePointerContainerImpl
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.project.stateStore
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.indexing.EntityIndexingService
import com.intellij.util.indexing.roots.WorkspaceIndexingRootsBuilder
import com.intellij.util.io.systemIndependentPath
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.impl.PlatformInternalWorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.WorkspaceEntity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOG = logger<ProjectRootManagerComponent>()
private val LOG_CACHES_UPDATE by lazy(LazyThreadSafetyMode.NONE) {
  ApplicationManager.getApplication().isInternal && !ApplicationManager.getApplication().isUnitTestMode
}
private val WATCH_ROOTS_LOG = Logger.getInstance("#com.intellij.openapi.vfs.WatchRoots")
private val WATCHED_ROOTS_PROVIDER_EP_NAME = ExtensionPointName<WatchedRootsProvider>("com.intellij.roots.watchedRootsProvider")

/**
 * ProjectRootManager extended with ability to watch events.
 */
open class ProjectRootManagerComponent(project: Project) : ProjectRootManagerImpl(project), Disposable {
  private var isStartupActivityPerformed = false

  // accessed in EDT only
  private var collectWatchRootsJob = AtomicReference<Job>()
  private var pointerChangesDetected = false
  private var insideWriteAction = 0

  var rootsToWatch: MutableSet<WatchRequest> = CollectionFactory.createSmallMemoryFootprintSet()
    private set

  private var rootPointersDisposable = Disposer.newDisposable()
  private var lastInProgressRootPointersDisposable: Disposable? = null
  private val rootWatchLock = ReentrantLock()

  private val rootsChangedListener: VirtualFilePointerListener = object : VirtualFilePointerListener {
    private fun getPointersChanges(pointers: Array<VirtualFilePointer>): RootsChangeRescanningInfo {
      var result: RootsChangeRescanningInfo? = null
      for (pointer in pointers) {
        if (pointer.isValid) {
          return RootsChangeRescanningInfo.TOTAL_RESCAN
        }
        else if (result == null) {
          result = RootsChangeRescanningInfo.NO_RESCAN_NEEDED
        }
      }
      return result ?: RootsChangeRescanningInfo.TOTAL_RESCAN
    }

    override fun beforeValidityChanged(pointers: Array<VirtualFilePointer>) {
      if (myProject.isDisposed) {
        return
      }
      if (!isInsideWriteAction && !pointerChangesDetected) {
        pointerChangesDetected = true
        //this is the first pointer changing validity
        myRootsChanged.levelUp()
      }
      myRootsChanged.beforeRootsChanged()
      if (LOG_CACHES_UPDATE || LOG.isTraceEnabled) {
        LOG.trace(Throwable(if (pointers.isNotEmpty()) pointers[0].presentableUrl else ""))
      }
    }

    override fun validityChanged(pointers: Array<VirtualFilePointer>) {
      val changeInfo = getPointersChanges(pointers)
      if (myProject.isDisposed) {
        return
      }
      if (isInsideWriteAction) {
        myRootsChanged.rootsChanged(changeInfo)
      }
      else {
        clearScopesCaches()
      }
    }

    private val isInsideWriteAction: Boolean
      get() = insideWriteAction == 0
  }

  init {
    if (!myProject.isDefault) {
      registerListeners()
    }
  }

  private fun registerListeners() {
    val connection = myProject.messageBus.connect(this)
    connection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      @Deprecated("Deprecated in Java")
      @Suppress("removal")
      override fun projectOpened(project: Project) {
        if (project === myProject) {
          addRootsToWatch()
          ApplicationManager.getApplication().addApplicationListener(AppListener(), myProject)
        }
      }

      override fun projectClosed(project: Project) {
        if (project === myProject) {
          this@ProjectRootManagerComponent.projectClosed()
        }
      }
    })
    connection.subscribe(FileTypeManager.TOPIC, object : FileTypeListener {
      override fun beforeFileTypesChanged(event: FileTypeEvent) {
        myFileTypesChanged.beforeRootsChanged()
      }

      override fun fileTypesChanged(event: FileTypeEvent) {
        myFileTypesChanged.rootsChanged()
      }
    })
    StartupManager.getInstance(myProject).registerStartupActivity { isStartupActivityPerformed = true }
    connection.subscribe(BatchUpdateListener.TOPIC, object : BatchUpdateListener {
      override fun onBatchUpdateStarted() {
        myRootsChanged.levelUp()
        myFileTypesChanged.levelUp()
      }

      override fun onBatchUpdateFinished() {
        myRootsChanged.levelDown()
        myFileTypesChanged.levelDown()
      }
    })

    val rootsExtensionPointListener = Runnable {
      ApplicationManager.getApplication().invokeLater {
        ApplicationManager.getApplication().runWriteAction {
          makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.TOTAL_RESCAN)
        }
      }
    }
    AdditionalLibraryRootsProvider.EP_NAME.addChangeListener(rootsExtensionPointListener, this)
    OrderEnumerationHandler.EP_NAME.addChangeListener(rootsExtensionPointListener, this)
  }

  protected open fun projectClosed() {
    LocalFileSystem.getInstance().removeWatchedRoots(rootsToWatch)
  }

  private fun addRootsToWatch() {
    if (myProject.isDefault) {
      return
    }

    val oldDisposable: Disposable
    val newDisposable: Disposable
    rootWatchLock.withLock {
      oldDisposable = rootPointersDisposable
      newDisposable = Disposer.newDisposable()
      lastInProgressRootPointersDisposable = newDisposable
    }

    if (ApplicationManager.getApplication().isUnitTestMode) {
      val watchRoots = collectWatchRoots(newDisposable)
      postCollect(newDisposable, oldDisposable, watchRoots)
    }
    else {
      @Suppress("DEPRECATION")
      myProject.coroutineScope.launch {
        val job = launch(start = CoroutineStart.LAZY) {
          val watchRoots = readAction { collectWatchRoots(newDisposable) }
          postCollect(newDisposable, oldDisposable, watchRoots)
        }
        collectWatchRootsJob.getAndSet(job)?.cancelAndJoin()
        job.start()
      }
    }
  }

  private fun postCollect(newDisposable: Disposable,
                          oldDisposable: Disposable,
                          watchRoots: Pair<Set<String>, Set<String>>) = rootWatchLock.withLock {
    if (rootPointersDisposable == oldDisposable && lastInProgressRootPointersDisposable == newDisposable) {
      rootPointersDisposable = newDisposable
      // dispose after the re-creating container to keep VFPs from disposing and re-creating back;
      // instead, just increment/decrement their usage count
      Disposer.dispose(oldDisposable)
      rootsToWatch = LocalFileSystem.getInstance().replaceWatchedRoots(rootsToWatch, watchRoots.first, watchRoots.second)
    }
    else {
      Disposer.dispose(newDisposable)
    }
  }

  override fun fireBeforeRootsChangeEvent(fileTypes: Boolean) {
    isFiringEvent = true
    try {
      (DirectoryIndex.getInstance(myProject) as? DirectoryIndexImpl)?.reset()
      (WorkspaceFileIndex.getInstance(myProject) as WorkspaceFileIndexEx).indexData.resetCustomContributors()
      myProject.messageBus.syncPublisher(ProjectTopics.PROJECT_ROOTS).beforeRootsChange(ModuleRootEventImpl(myProject, fileTypes))
    }
    finally {
      isFiringEvent = false
    }
  }

  override fun fireRootsChangedEvent(fileTypes: Boolean, indexingInfos: List<RootsChangeRescanningInfo>) {
    isFiringEvent = true
    try {
      (DirectoryIndex.getInstance(myProject) as? DirectoryIndexImpl)?.reset()
      (WorkspaceFileIndex.getInstance(myProject) as WorkspaceFileIndexEx).indexData.resetCustomContributors()

      val isFromWorkspaceOnly = EntityIndexingService.getInstance().isFromWorkspaceOnly(indexingInfos)
      myProject.messageBus.syncPublisher(ProjectTopics.PROJECT_ROOTS)
        .rootsChanged(ModuleRootEventImpl(myProject, fileTypes, indexingInfos, isFromWorkspaceOnly))
    }
    finally {
      isFiringEvent = false
    }
    if (isStartupActivityPerformed) {
      EntityIndexingService.getInstance().indexChanges(myProject, indexingInfos)
    }
    addRootsToWatch()
  }

  private fun collectWatchRoots(disposable: Disposable): Pair<Set<String>, Set<String>> {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val recursivePaths = CollectionFactory.createFilePathSet()
    val flatPaths = CollectionFactory.createFilePathSet()
    WATCH_ROOTS_LOG.trace { "watch roots for ${myProject}}" }

    val store = myProject.stateStore
    val projectFilePath = store.projectFilePath
    if (Project.DIRECTORY_STORE_FOLDER != projectFilePath.parent.fileName?.toString()) {
      flatPaths += projectFilePath.systemIndependentPath
      flatPaths += store.workspacePath.systemIndependentPath
      WATCH_ROOTS_LOG.trace { "  project store: ${flatPaths}" }
    }

    for (extension in AdditionalLibraryRootsProvider.EP_NAME.extensionList) {
      val toWatch = extension.getRootsToWatch(myProject)
      if (!toWatch.isEmpty()) {
        WATCH_ROOTS_LOG.trace { "  ${extension::class.java}}: ${toWatch}" }
        for (file in toWatch) {
          recursivePaths += file.path
        }
      }
    }

    for (extension in WATCHED_ROOTS_PROVIDER_EP_NAME.extensionList) {
      val toWatch = extension.getRootsToWatch(myProject)
      if (!toWatch.isEmpty()) {
        WATCH_ROOTS_LOG.trace { "  ${extension::class.java}}: ${toWatch}" }
        for (path in toWatch) {
          recursivePaths += FileUtilRt.toSystemIndependentName(path)
        }
      }
    }

    val excludedUrls = HashSet<String>()
    // changes in files provided by this method should be watched manually because no-one's bothered to set up correct pointers for them
    for (excludePolicy in DirectoryIndexExcludePolicy.EP_NAME.getExtensions(myProject)) {
      excludedUrls.addAll(excludePolicy.excludeUrlsForProject)
    }
    // avoid creating empty unnecessary container
    if (!excludedUrls.isEmpty()) {
      Disposer.register(this, disposable)
      // creating a container with these URLs with the sole purpose to get events to getRootsValidityChangedListener() when these roots change
      val container = VirtualFilePointerManager.getInstance().createContainer(disposable, rootsValidityChangedListener)
      (container as VirtualFilePointerContainerImpl).addAll(excludedUrls)
    }

    // module roots already fire validity change events, see usages of ProjectRootManagerComponent.getRootsValidityChangedListener
    collectModuleWatchRoots(recursivePaths, flatPaths, true)

    collectCustomWorkspaceWatchRoots(recursivePaths)

    return recursivePaths to flatPaths
  }

  private fun collectModuleWatchRoots(recursivePaths: MutableSet<String>, flatPaths: MutableSet<String>, logRoots: Boolean) {
    fun collectUrls(urls: Array<String>, logDescriptor: String) {
      val recursive = if (logRoots) CollectionFactory.createFilePathSet() else recursivePaths
      val flat = if (logRoots) CollectionFactory.createFilePathSet() else flatPaths

      for (url in urls) {
        when (VirtualFileManager.extractProtocol(url)) {
          null, StandardFileSystems.FILE_PROTOCOL, StandardFileSystems.JRT_PROTOCOL -> recursive += extractLocalPath(url)
          StandardFileSystems.JAR_PROTOCOL -> flat += extractLocalPath(url)
        }
      }

      if (logRoots) {
        LOG.trace { "    ${logDescriptor}: ${recursive}, ${flat}" }
        recursivePaths += recursive
        flatPaths += flat
      }
    }

    for (module in ModuleManager.getInstance(myProject).modules) {
      if (logRoots) LOG.trace { "  module ${module}" }
      val rootManager = ModuleRootManager.getInstance(module)
      collectUrls(rootManager.contentRootUrls, "content")
      rootManager.orderEntries().withoutModuleSourceEntries().withoutDepModules().forEach { entry ->
        if (entry is LibraryOrSdkOrderEntry) {
          for (type in OrderRootType.getAllTypes()) {
            collectUrls(entry.getRootUrls(type), "${entry} [${type}]")
          }
        }
        true
      }
    }
  }

  private fun collectCustomWorkspaceWatchRoots(recursivePaths: MutableSet<String>) {
    val settings = WorkspaceIndexingRootsBuilder.Companion.Settings()
    settings.retainCondition = Condition<WorkspaceFileIndexContributor<out WorkspaceEntity>> {
      it.storageKind == EntityStorageKind.MAIN && it !is PlatformInternalWorkspaceFileIndexContributor && it !is SkipAddingToWatchedRoots
    }
    val builder = WorkspaceIndexingRootsBuilder.registerEntitiesFromContributors(project,
                                                                                 WorkspaceModel.getInstance(project).currentSnapshot,
                                                                                 settings)

    fun register(rootFiles: Collection<VirtualFile>, name: String) {
      WATCH_ROOTS_LOG.trace { "  $name from workspace entities: ${rootFiles}" }
      rootFiles.forEach { recursivePaths.add(it.path) }
    }

    builder.forEachModuleContentEntitiesRoots { roots -> register(roots, "module content roots") }
    builder.forEachContentEntitiesRoots { roots -> register(roots, "content roots") }
    builder.forEachExternalEntitiesRoots({ roots -> register(roots, "external roots") }) { sourceRoots ->
      register(sourceRoots, "external source roots")
    }
  }

  override fun clearScopesCaches() {
    super.clearScopesCaches()

    myProject.getServiceIfCreated(LibraryScopeCache::class.java)?.clear()
  }

  override fun clearScopesCachesForModules() {
    super.clearScopesCachesForModules()

    for (module in ModuleManager.getInstance(myProject).modules) {
      (module as ModuleEx).clearScopesCache()
    }
  }

  override fun markRootsForRefresh() {
    val paths = CollectionFactory.createFilePathSet()
    collectModuleWatchRoots(paths, paths, false)
    val fs = LocalFileSystem.getInstance()
    for (path in paths) {
      val root = fs.findFileByPath(path)
      if (root is NewVirtualFile) {
        root.markDirtyRecursively()
      }
    }
  }

  override fun dispose() {}

  @TestOnly
  fun disposeVirtualFilePointersAfterTest() = rootWatchLock.withLock {
    Disposer.dispose(rootPointersDisposable)
  }

  private inner class AppListener : ApplicationListener {
    override fun beforeWriteActionStart(action: Any) {
      insideWriteAction++
    }

    override fun writeActionFinished(action: Any) {
      if (--insideWriteAction == 0 && pointerChangesDetected) {
        pointerChangesDetected = false
        myRootsChanged.levelDown()
      }
    }
  }

  override fun getRootsValidityChangedListener(): VirtualFilePointerListener = rootsChangedListener
}
