// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.ProjectTopics
import com.intellij.configurationStore.BatchUpdateListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.logger
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
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.LocalFileSystem.WatchRequest
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.impl.VirtualFilePointerContainerImpl
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerListener
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.project.stateStore
import com.intellij.util.ObjectUtils
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.indexing.EntityIndexingService
import com.intellij.util.indexing.IndexableFilesIndex
import com.intellij.util.indexing.roots.IndexableFilesIndexImpl
import com.intellij.util.io.systemIndependentPath
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import java.lang.Runnable
import java.util.concurrent.atomic.AtomicReference

private val LOG = logger<ProjectRootManagerComponent>()
private val LOG_CACHES_UPDATE by lazy(LazyThreadSafetyMode.NONE) {
  ApplicationManager.getApplication().isInternal && !ApplicationManager.getApplication().isUnitTestMode
}
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

  @OptIn(ExperimentalCoroutinesApi::class)
  private val sequentialDispatcher = Dispatchers.Default.limitedParallelism(1)

  var rootsToWatch: MutableSet<WatchRequest> = CollectionFactory.createSmallMemoryFootprintSet()
    private set

  // accessed in EDT
  private var rootPointersDisposable = Disposer.newDisposable()

  private val rootsChangedListener: VirtualFilePointerListener = object : VirtualFilePointerListener {
    private fun getPointersChanges(pointers: Array<VirtualFilePointer>): RootsChangeRescanningInfo {
      var result: RootsChangeRescanningInfo? = null
      for (pointer in pointers) {
        if (pointer.isValid) {
          return RootsChangeRescanningInfo.TOTAL_RESCAN
        }
        else {
          if (result == null) {
            result = RootsChangeRescanningInfo.NO_RESCAN_NEEDED
          }
        }
      }
      return ObjectUtils.notNull(result, RootsChangeRescanningInfo.TOTAL_RESCAN)
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
        LOG.trace(Throwable(if (pointers.size > 0) pointers[0].presentableUrl else ""))
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

  @RequiresEdt
  private fun addRootsToWatch() {
    if (myProject.isDefault) {
      return
    }

    ApplicationManager.getApplication().assertIsWriteThread()
    val oldDisposable = rootPointersDisposable
    val newDisposable = Disposer.newDisposable()
    if (ApplicationManager.getApplication().isUnitTestMode) {
      val watchRoots = collectWatchRoots(newDisposable)
      postCollect(newDisposable, oldDisposable, watchRoots)
    }
    else {
      myProject.coroutineScope.launch {
        val job = launch(start = CoroutineStart.LAZY) {
          val watchRoots = readAction { collectWatchRoots(newDisposable) }
          withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            postCollect(newDisposable, oldDisposable, watchRoots)
          }
        }
        collectWatchRootsJob.getAndSet(job)?.cancelAndJoin()
        job.start()
      }
    }
  }

  private fun postCollect(newDisposable: Disposable, oldDisposable: Disposable, watchRoots: Pair<Set<String>, Set<String>>) {
    rootPointersDisposable = newDisposable
    // dispose after the re-creating container to keep VFPs from disposing and re-creating back;
    // instead, just increment/decrement their usage count
    Disposer.dispose(oldDisposable)
    rootsToWatch = LocalFileSystem.getInstance().replaceWatchedRoots(rootsToWatch, watchRoots.first, watchRoots.second)
  }

  override fun fireBeforeRootsChangeEvent(fileTypes: Boolean) {
    isFiringEvent = true
    try {
      (DirectoryIndex.getInstance(myProject) as? DirectoryIndexImpl)?.reset()
      if (IndexableFilesIndex.shouldBeUsed()) {
        IndexableFilesIndexImpl.getInstanceImpl(myProject).beforeRootsChanged()
      }
      (WorkspaceFileIndex.getInstance(myProject) as WorkspaceFileIndexEx).resetCustomContributors()
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
      (WorkspaceFileIndex.getInstance(myProject) as WorkspaceFileIndexEx).resetCustomContributors()

      val isFromWorkspaceOnly = EntityIndexingService.getInstance().isFromWorkspaceOnly(indexingInfos)
      if (IndexableFilesIndex.shouldBeUsed()) {
        IndexableFilesIndexImpl.getInstanceImpl(myProject).afterRootsChanged(fileTypes, indexingInfos, isFromWorkspaceOnly)
      }
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
    val recursivePathsToWatch = CollectionFactory.createFilePathSet()
    val flatPaths = CollectionFactory.createFilePathSet()
    val store = myProject.stateStore
    val projectFilePath = store.projectFilePath
    if (Project.DIRECTORY_STORE_FOLDER != projectFilePath.parent.fileName?.toString()) {
      flatPaths.add(projectFilePath.systemIndependentPath)
      flatPaths.add(store.workspacePath.systemIndependentPath)
    }
    for (extension in AdditionalLibraryRootsProvider.EP_NAME.extensionList) {
      val toWatch = extension.getRootsToWatch(myProject)
      if (!toWatch.isEmpty()) {
        for (file in toWatch) {
          recursivePathsToWatch.add(file.path)
        }
      }
    }
    for (extension in WATCHED_ROOTS_PROVIDER_EP_NAME.extensionList) {
      val toWatch = extension.getRootsToWatch(myProject)
      if (!toWatch.isEmpty()) {
        for (path in toWatch) {
          recursivePathsToWatch.add(FileUtilRt.toSystemIndependentName(path))
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
    collectModuleWatchRoots(recursivePaths = recursivePathsToWatch, flatPaths = flatPaths)
    return Pair(recursivePathsToWatch, flatPaths)
  }

  private fun collectModuleWatchRoots(recursivePaths: MutableSet<in String>, flatPaths: MutableSet<in String>) {
    val urls = CollectionFactory.createFilePathSet()
    for (module in ModuleManager.getInstance(myProject).modules) {
      val rootManager = ModuleRootManager.getInstance(module)
      urls.addAll(rootManager.contentRootUrls)
      rootManager.orderEntries().withoutModuleSourceEntries().withoutDepModules().forEach { entry ->
        if (entry is LibraryOrSdkOrderEntry) {
          for (type in OrderRootType.getAllTypes()) {
            urls.addAll(entry.getRootUrls(type))
          }
        }
        true
      }
    }
    for (url in urls) {
      val protocol = VirtualFileManager.extractProtocol(url)
      when {
        protocol == null || StandardFileSystems.FILE_PROTOCOL == protocol -> recursivePaths.add(extractLocalPath(url))
        StandardFileSystems.JAR_PROTOCOL == protocol -> flatPaths.add(extractLocalPath(url))
        StandardFileSystems.JRT_PROTOCOL == protocol -> recursivePaths.add(extractLocalPath(url))
      }
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
    collectModuleWatchRoots(paths, paths)
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
  fun disposeVirtualFilePointersAfterTest() {
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