// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

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
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.project.stateStore
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.indexing.EntityIndexingService
import com.intellij.util.indexing.roots.WorkspaceIndexingRootsBuilder
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.impl.PlatformInternalWorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.invariantSeparatorsPathString

private val LOG = logger<ProjectRootManagerComponent>()
private val LOG_CACHES_UPDATE by lazy(LazyThreadSafetyMode.NONE) {
  ApplicationManager.getApplication().isInternal && !ApplicationManager.getApplication().isUnitTestMode
}
private val WATCH_ROOTS_LOG = Logger.getInstance("#com.intellij.openapi.vfs.WatchRoots")
private val WATCHED_ROOTS_PROVIDER_EP_NAME = ExtensionPointName<WatchedRootsProvider>("com.intellij.roots.watchedRootsProvider")

/**
 * ProjectRootManager extended with the ability to watch events.
 */
@ApiStatus.Internal
open class ProjectRootManagerComponent(
  project: Project,
  coroutineScope: CoroutineScope
) : ProjectRootManagerImpl(project, coroutineScope), Disposable {
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
    private fun getPointerChanges(pointers: Array<VirtualFilePointer>): RootsChangeRescanningInfo {
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
      if (project.isDisposed) {
        return
      }
      if (!isInsideWriteAction && !pointerChangesDetected) {
        pointerChangesDetected = true
        //this is the first pointer changing validity
        rootsChanged.levelUp()
      }
      rootsChanged.beforeRootsChanged()
      if (LOG_CACHES_UPDATE || LOG.isTraceEnabled) {
        LOG.trace(Throwable(if (pointers.isNotEmpty()) pointers[0].presentableUrl else ""))
      }
    }

    override fun validityChanged(pointers: Array<VirtualFilePointer>) {
      val changeInfo = getPointerChanges(pointers)
      if (project.isDisposed) {
        return
      }
      if (isInsideWriteAction) {
        rootsChanged.rootsChanged(changeInfo)
      }
      else {
        clearScopesCaches()
      }
    }

    private val isInsideWriteAction: Boolean
      get() = insideWriteAction == 0
  }

  init {
    if (!project.isDefault) {
      registerListeners()
    }
  }

  private fun registerListeners() {
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      @Deprecated("Deprecated in Java")
      @Suppress("removal")
      override fun projectOpened(project: Project) {
        if (project === this@ProjectRootManagerComponent.project) {
          addRootsToWatch()
          ApplicationManager.getApplication().addApplicationListener(AppListener(), this@ProjectRootManagerComponent.project)
        }
      }

      override fun projectClosed(project: Project) {
        if (project === this@ProjectRootManagerComponent.project) {
          this@ProjectRootManagerComponent.projectClosed()
        }
      }
    })

    val connection = project.messageBus.connect(this)

    connection.subscribe(FileTypeManager.TOPIC, object : FileTypeListener {
      override fun beforeFileTypesChanged(event: FileTypeEvent) {
        fileTypesChanged.beforeRootsChanged()
      }

      override fun fileTypesChanged(event: FileTypeEvent) {
        fileTypesChanged.rootsChanged()
      }
    })

    connection.subscribe(BatchUpdateListener.TOPIC, object : BatchUpdateListener {
      override fun onBatchUpdateStarted() {
        rootsChanged.levelUp()
        fileTypesChanged.levelUp()
      }

      override fun onBatchUpdateFinished() {
        rootsChanged.levelDown()
        fileTypesChanged.levelDown()
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
    if (project.isDefault) {
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
      coroutineScope.launch {
        val job = launch(start = CoroutineStart.LAZY) {
          val watchRoots = readAction { collectWatchRoots(newDisposable) }
          postCollect(newDisposable = newDisposable, oldDisposable = oldDisposable, watchRoots = watchRoots)
        }
        collectWatchRootsJob.getAndSet(job)?.cancelAndJoin()
        job.start()
      }
    }
  }

  private fun postCollect(newDisposable: Disposable, oldDisposable: Disposable, watchRoots: Pair<Set<String>, Set<String>>) {
    rootWatchLock.withLock {
      if (rootPointersDisposable == oldDisposable && lastInProgressRootPointersDisposable == newDisposable) {
        rootPointersDisposable = newDisposable
        // dispose after the re-creating container to keep VFPs from disposing and re-creating back;
        // instead, update their usage count
        Disposer.dispose(oldDisposable)
        rootsToWatch = LocalFileSystem.getInstance().replaceWatchedRoots(rootsToWatch, watchRoots.first, watchRoots.second)
      }
      else {
        Disposer.dispose(newDisposable)
      }
    }
  }

  override fun fireBeforeRootsChangeEvent(fileTypes: Boolean) {
    isFiringEvent = true
    try {
      @Suppress("UsagesOfObsoleteApi")
      (DirectoryIndex.getInstance(project) as? DirectoryIndexImpl)?.reset()
      (WorkspaceFileIndex.getInstance(project) as WorkspaceFileIndexEx).indexData.resetCustomContributors()
      project.messageBus.syncPublisher(ModuleRootListener.TOPIC).beforeRootsChange(ModuleRootEventImpl(project, fileTypes))
    }
    finally {
      isFiringEvent = false
    }
  }

  override fun fireRootsChangedEvent(fileTypes: Boolean, indexingInfos: List<RootsChangeRescanningInfo>) {
    isFiringEvent = true
    try {
      @Suppress("UsagesOfObsoleteApi")
      (DirectoryIndex.getInstance(project) as? DirectoryIndexImpl)?.reset()
      (WorkspaceFileIndex.getInstance(project) as WorkspaceFileIndexEx).indexData.resetCustomContributors()

      val isFromWorkspaceOnly = EntityIndexingService.getInstance().isFromWorkspaceOnly(indexingInfos)
      project.messageBus.syncPublisher(ModuleRootListener.TOPIC)
        .rootsChanged(ModuleRootEventImpl(project, fileTypes, indexingInfos, isFromWorkspaceOnly))
    }
    finally {
      isFiringEvent = false
    }
    EntityIndexingService.getInstance().indexChanges(project, indexingInfos)
    addRootsToWatch()
  }

  @RequiresReadLock
  private fun collectWatchRoots(disposable: Disposable): Pair<Set<String>, Set<String>> {
    val recursivePaths = CollectionFactory.createFilePathSet()
    val flatPaths = CollectionFactory.createFilePathSet()
    WATCH_ROOTS_LOG.trace { "watch roots for ${project}}" }

    val store = project.stateStore
    val projectFilePath = store.projectFilePath
    if (Project.DIRECTORY_STORE_FOLDER != projectFilePath.parent.fileName?.toString()) {
      flatPaths += projectFilePath.invariantSeparatorsPathString
      flatPaths += store.workspacePath.invariantSeparatorsPathString
      WATCH_ROOTS_LOG.trace { "  project store: ${flatPaths}" }
    }

    for (extension in AdditionalLibraryRootsProvider.EP_NAME.extensionList) {
      val toWatch = extension.getRootsToWatch(project)
      if (toWatch.isNotEmpty()) {
        WATCH_ROOTS_LOG.trace { "  ${extension::class.java}}: ${toWatch}" }
        for (file in toWatch) {
          recursivePaths += file.path
        }
      }
    }

    for (extension in WATCHED_ROOTS_PROVIDER_EP_NAME.extensionList) {
      val toWatch = extension.getRootsToWatch(project)
      if (toWatch.isNotEmpty()) {
        WATCH_ROOTS_LOG.trace { "  ${extension::class.java}}: ${toWatch}" }
        for (path in toWatch) {
          recursivePaths += FileUtilRt.toSystemIndependentName(path)
        }
      }
    }

    val excludedUrls = HashSet<String>()
    // changes in files provided by this method should be watched manually because no-one's bothered to set up correct pointers for them
    for (excludePolicy in DirectoryIndexExcludePolicy.EP_NAME.getExtensions(project)) {
      excludedUrls.addAll(excludePolicy.excludeUrlsForProject)
    }
    // avoid creating empty unnecessary container
    if (excludedUrls.isNotEmpty()) {
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

  private fun collectModuleWatchRoots(recursivePaths: MutableSet<String>, flatPaths: MutableSet<String>, logAllowed: Boolean) {
    val logRoots = logAllowed && WATCH_ROOTS_LOG.isTraceEnabled
    fun collectUrls(urls: Array<String>, logDescriptor: () -> String) {
      val recursive = if (logRoots) CollectionFactory.createFilePathSet() else recursivePaths
      val flat = if (logRoots) CollectionFactory.createFilePathSet() else flatPaths

      for (url in urls) {
        when (VirtualFileManager.extractProtocol(url)) {
          null, StandardFileSystems.FILE_PROTOCOL, StandardFileSystems.JRT_PROTOCOL -> recursive += extractLocalPath(url)
          StandardFileSystems.JAR_PROTOCOL -> flat += extractLocalPath(url)
        }
      }

      if (logRoots) {
        WATCH_ROOTS_LOG.trace { "    ${logDescriptor()}: ${recursive}, ${flat}" }
        recursivePaths += recursive
        flatPaths += flat
      }
    }

    for (module in ModuleManager.getInstance(project).modules) {
      if (logRoots) WATCH_ROOTS_LOG.trace { "  module ${module}" }
      val rootManager = ModuleRootManager.getInstance(module)
      collectUrls(rootManager.contentRootUrls) { "content" }
      rootManager.orderEntries().withoutModuleSourceEntries().withoutDepModules().forEach { entry ->
        if (entry is LibraryOrSdkOrderEntry) {
          for (type in OrderRootType.getAllTypes()) {
            collectUrls(entry.getRootUrls(type)) { "${entry} [${type}]" }
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
    val builder = WorkspaceIndexingRootsBuilder.registerEntitiesFromContributors(WorkspaceModel.getInstance(project).currentSnapshot, settings)
    fun register(rootFiles: Collection<VirtualFile>, name: String) {
      WATCH_ROOTS_LOG.trace { "  ${name} from workspace entities: ${rootFiles}" }
      rootFiles.forEach { recursivePaths.add(it.path) }
    }

    builder.forEachModuleContentEntitiesRoots { urlRoots ->
      val roots = urlRoots.toRootHolder()
      register(roots.roots, "module content roots")
      register(roots.nonRecursiveRoots, "module non-recursive content roots")
    }
    builder.forEachContentEntitiesRoots { urlRoots ->
      val roots = urlRoots.toRootHolder()
      register(roots.roots, "content roots")
      register(roots.nonRecursiveRoots, "non-recursive content roots")
    }
    builder.forEachExternalEntitiesRoots { urlRoots ->
      val roots = urlRoots.toSourceRootHolder()
      register(roots.roots, "external roots")
      register(roots.sourceRoots, "external source roots")
      register(roots.nonRecursiveRoots, "non-recursive external roots")
      register(roots.nonRecursiveSourceRoots, "non-recursive external source roots")
    }
  }

  override fun clearScopesCaches() {
    super.clearScopesCaches()

    LibraryScopeCache.getInstance(project)?.clear()
  }

  override fun clearScopesCachesForModules() {
    super.clearScopesCachesForModules()

    for (module in ModuleManager.getInstance(project).modules) {
      (module as ModuleEx).clearScopesCache()
    }
  }

  override fun markRootsForRefresh(): List<VirtualFile> {
    val paths = CollectionFactory.createFilePathSet()
    collectModuleWatchRoots(paths, paths, false)
    val roots = paths.mapNotNull(LocalFileSystem.getInstance()::findFileByPath)
    roots.asSequence()
      .filterIsInstance(NewVirtualFile::class.java)
      .forEach(NewVirtualFile::markDirtyRecursively)
    return roots
  }

  override fun dispose() {}

  @TestOnly
  fun disposeVirtualFilePointersAfterTest(): Unit = rootWatchLock.withLock {
    Disposer.dispose(rootPointersDisposable)
  }

  private inner class AppListener : ApplicationListener {
    override fun writeActionStarted(action: Any) {
      insideWriteAction++
    }

    override fun writeActionFinished(action: Any) {
      if (--insideWriteAction == 0 && pointerChangesDetected) {
        pointerChangesDetected = false
        rootsChanged.levelDown()
      }
    }
  }

  override val rootsValidityChangedListener: VirtualFilePointerListener
    get() = rootsChangedListener
}
