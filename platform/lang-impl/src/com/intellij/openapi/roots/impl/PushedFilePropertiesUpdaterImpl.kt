// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.*
import com.intellij.openapi.project.DumbServiceImpl.Companion.isSynchronousTaskExecution
import com.intellij.openapi.roots.ContentIteratorEx
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.FilesScanExecutor.runOnAllThreads
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.file.impl.FileManagerEx
import com.intellij.util.ModalityUiUtil
import com.intellij.util.containers.TreeNodeProcessingResult
import com.intellij.util.gist.GistManager
import com.intellij.util.gist.GistManagerImpl
import com.intellij.util.indexing.*
import com.intellij.util.indexing.diagnostic.ChangedFilesPushedDiagnostic.addEvent
import com.intellij.util.indexing.diagnostic.ChangedFilesPushingStatistics
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper.Companion.shouldDumpInUnitTestMode
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods.createIterators
import com.intellij.util.indexing.roots.IndexableFileScanner
import com.intellij.util.indexing.roots.IndexableFileScanner.IndexableFileVisitor
import com.intellij.util.indexing.roots.IndexableFilesDeduplicateFilter
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.ProjectIndexableFilesIteratorImpl
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Function
import kotlin.coroutines.coroutineContext

@Internal
class PushedFilePropertiesUpdaterImpl(private val myProject: Project) : PushedFilePropertiesUpdater() {
  private fun interface TaskFactory {
    fun getTasks(): List<Runnable>
  }

  private val myTasks: Queue<TaskFactory> = ConcurrentLinkedQueue()

  init {
    val connection = myProject.messageBus.simpleConnect()
    connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        if (LOG.isTraceEnabled) {
          LOG
            .trace(
              Throwable("Processing roots changed event (caused by file type change: " + event.isCausedByFileTypesChange + ")"))
        }
        for (pusher in FilePropertyPusher.EP_NAME.extensionList) {
          pusher.afterRootsChanged(myProject)
        }
      }
    })

    connection.subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun beforePluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        myTasks.clear()
      }
    })
  }

  fun processAfterVfsChanges(events: List<VFileEvent>) {
    val syncTasks = ArrayList<TaskFactory>()
    val delayedTasks: MutableList<TaskFactory> = ArrayList()
    val filePushers = filePushers

    // this is useful for debugging. Especially in integration tests: it is often clear why large file sets have changed
    // (e.g. imported modules or jdk), but it is often unclear why small file sets change and what these files are.
    if (LOG.isDebugEnabled && events.size < 20) {
      for (event in events) LOG.debug("""
  File changed: ${event.path}.
  event:$event
  """.trimIndent())
    }

    for (event in events) {
      if (event is VFileCreateEvent) {
        val isDirectory = event.isDirectory
        val pushers = if (isDirectory) FilePropertyPusher.EP_NAME.extensionList else filePushers

        if (!event.isFromRefresh()) {
          createRecursivePushTask(event, pushers)?.let { recursiveTask ->
            syncTasks.add(TaskFactory { listOf(recursiveTask) })
          }
        }
        else {
          val isProjectOrWorkspaceFile = ProjectCoreUtil.isProjectOrWorkspaceFile(event.parent, event.childName)
          if (!isProjectOrWorkspaceFile) {
            createRecursivePushTask(event, pushers)?.let { recursiveTask ->
              delayedTasks.add(TaskFactory { listOf(recursiveTask) })
            }
          }
        }
      }
      else if (event is VFileMoveEvent || event is VFileCopyEvent) {
        val file = getFile(event)
        if (file == null) continue
        val isDirectory = file.isDirectory
        val pushers = if (isDirectory) FilePropertyPusher.EP_NAME.extensionList else filePushers
        createRecursivePushTask(event, pushers)?.let { recursiveTask ->
          syncTasks.add(TaskFactory { listOf(recursiveTask) })
        }
      }
    }
    val pushingSomethingSynchronously =
      !syncTasks.isEmpty() && syncTasks.size < FileBasedIndexProjectHandler.ourMinFilesToStartDumbMode
    if (pushingSomethingSynchronously) {
      // push synchronously to avoid entering dumb mode in the middle of a meaningful write action
      // when only a few files are created/moved
      syncTasks.forEach { task ->
        runConcurrentlyIfPossible(task.getTasks())
      }
    }
    else {
      delayedTasks.addAll(syncTasks)
    }
    if (!delayedTasks.isEmpty()) {
      queueTasks(delayedTasks, "Push on VFS changes")
    }
    if (pushingSomethingSynchronously) {
      val app = ApplicationManager.getApplication()
      if (app.isDispatchThread) {
        scheduleDumbModeReindexingIfNeeded()
      }
      else {
        app.invokeLater({ this.scheduleDumbModeReindexingIfNeeded() }, myProject.disposed)
      }
    }
  }

  override fun runConcurrentlyIfPossible(tasks: List<Runnable>) {
    invokeConcurrentlyIfPossible(tasks)
  }

  fun initializeProperties() {
    FilePropertyPusher.EP_NAME.forEachExtensionSafe { pusher: FilePropertyPusher<*> ->
      pusher.initExtra(myProject)
    }
    IndexableFileScanner.EP_NAME.extensionList //ensure the list of extensions is initialized in a background thread
  }

  private fun createRecursivePushTask(event: VFileEvent, pushers: List<FilePropertyPusher<*>>): Runnable? {
    val scanners = IndexableFileScanner.EP_NAME.extensionList
    if (pushers.isEmpty() && scanners.isEmpty()) {
      return null
    }

    return Runnable {
      // delay calling event.getFile() until background to avoid expensive VFileCreateEvent.getFile() in EDT
      val dir = getFile(event)
      val fileIndex = ReadAction.compute<ProjectFileIndex, RuntimeException> { ProjectFileIndex.getInstance(myProject) }
      if (dir != null && ReadAction.compute<Boolean, RuntimeException> { fileIndex.isInContent(dir) } && !isProjectOrWorkspaceFile(dir)) {
        doPushRecursively(pushers, scanners, ProjectIndexableFilesIteratorImpl(dir))
      }
    }
  }

  private fun doPushRecursively(pushers: List<FilePropertyPusher<*>>,
                                scanners: List<IndexableFileScanner>,
                                indexableFilesIterator: IndexableFilesIterator) {
    val sessions = scanners.mapNotNull({ visitor: IndexableFileScanner ->
                                         visitor.startSession(myProject).createVisitor(indexableFilesIterator.origin)
                                       })
    indexableFilesIterator.iterateFiles(myProject, { fileOrDir: VirtualFile ->
      applyPushersToFile(fileOrDir, pushers, null)
      applyScannersToFile(fileOrDir, sessions)
      true
    }, IndexableFilesDeduplicateFilter.create())
    finishVisitors(sessions)
  }

  private fun queueTasks(actions: List<TaskFactory>, reason: @NonNls String) {
    actions.forEach { myTasks.offer(it) }
    val task: DumbModeTask = MyDumbModeTask(reason, this)
    myProject.messageBus.connect(task).subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        for (info in (event as ModuleRootEventImpl).infos) {
          if (info === RootsChangeRescanningInfo.TOTAL_RESCAN) {
            DumbService.getInstance(myProject).cancelTask(task)
            return
          }
        }
      }
    })
    task.queue(myProject)
  }

  suspend fun performDelayedPushTasks() {
    performDelayedPushTasks(null)
  }

  private suspend fun performDelayedPushTasks(statistics: ChangedFilesPushingStatistics?) {
    var hadTasks = false
    while (true) {
      checkCanceled()
      val task = myTasks.poll()
      if (task == null) {
        break
      }
      try {
        coroutineScope {
          task.getTasks().forEachConcurrent(SCANNING_PARALLELISM) { subtask ->
            blockingContext {
              subtask.run()
            }
          }
        }
        hadTasks = true
      }
      catch (e: Throwable) {
        if (!coroutineContext.isActive) {
          if (statistics != null) {
            statistics.finished(true)
            addEvent(myProject, statistics)
          }
          queueTasks(listOf(task),
                     "Rerun pushing tasks after process cancelled") // reschedule dumb mode and ensure the canceled task is enqueued again
        }

        throw e
      }
    }

    if (hadTasks) {
      scheduleDumbModeReindexingIfNeeded()
    }
    if (statistics != null) {
      statistics.finished(false)
      addEvent(myProject, statistics)
    }
  }

  private fun scheduleDumbModeReindexingIfNeeded() {
    FileBasedIndexProjectHandler.scheduleReindexingInDumbMode(myProject)
  }

  override fun filePropertiesChanged(fileOrDir: VirtualFile, acceptFileCondition: Condition<in VirtualFile>) {
    if (fileOrDir.isDirectory) {
      for (child in fileOrDir.children) {
        if (!child.isDirectory && acceptFileCondition.value(child)) {
          filePropertiesChanged(child)
        }
      }
    }
    else if (acceptFileCondition.value(fileOrDir)) {
      filePropertiesChanged(fileOrDir)
    }
  }

  override fun pushAll(vararg pushers: FilePropertyPusher<*>) {
    if (!isFirstProjectScanningRequested(myProject)) {
      LOG.info("Ignoring push request, as project is not yet initialized")
      return
    }
    queueTasks(listOf(TaskFactory { doPushAll(listOf(*pushers)) }), "Push all on " + pushers.contentToString())
  }

  private fun doPushAll(pushers: List<FilePropertyPusher<*>>): List<Runnable> {
    return generateScanTasks(myProject) { module: Module ->
      val moduleValues = getModuleImmediateValues(pushers, module)
      ContentIteratorEx { fileOrDir: VirtualFile ->
        applyPushersToFile(fileOrDir, pushers, moduleValues)
        TreeNodeProcessingResult.CONTINUE
      }
    }
  }

  fun applyPushersToFile(fileOrDir: VirtualFile,
                         pushers: List<FilePropertyPusher<*>>,
                         moduleValues: Array<Any?>?) {
    if (pushers.isEmpty()) return
    if (fileOrDir.isDirectory) {
      fileOrDir.children // outside read action to avoid freezes
    }

    ReadAction.run<RuntimeException> {
      if (!fileOrDir.isValid || fileOrDir !is VirtualFileWithId) return@run
      doApplyPushersToFile(fileOrDir, pushers, moduleValues)
    }
  }

  private fun doApplyPushersToFile(fileOrDir: VirtualFile,
                                   pushers: List<FilePropertyPusher<*>>,
                                   moduleValues: Array<Any?>?) {
    val isDir = fileOrDir.isDirectory
    for (i in pushers.indices) {
      val pusher = pushers[i] as FilePropertyPusher<Any>
      val notApplicable =
        if (isDir) !pusher.acceptsDirectory(fileOrDir, myProject)
        else pusher.pushDirectoriesOnly() || !pusher.acceptsFile(fileOrDir, myProject)
      if (notApplicable) {
        continue
      }
      val value = moduleValues?.get(i)
      findAndUpdateValue(fileOrDir, pusher, value)
    }
  }

  override fun <T : Any> findAndUpdateValue(fileOrDir: VirtualFile, pusher: FilePropertyPusher<T>, moduleValue: T?) {
    val newValue: T = findNewPusherValue(myProject, fileOrDir, pusher, moduleValue)
    try {
      pusher.persistAttribute(myProject, fileOrDir, newValue)
    }
    catch (e: IOException) {
      LOG.error(e)
    }
  }

  @Deprecated("Deprecated in Java")
  override fun filePropertiesChanged(file: VirtualFile) {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val fileBasedIndex = FileBasedIndex.getInstance()
    if (fileBasedIndex is FileBasedIndexImpl) {
      fileBasedIndex.requestReindex(file, false)
    }
    for (project in ProjectManager.getInstance().openProjects) {
      reloadPsi(file, project)
    }
  }

  private class MyDumbModeTask(private val myReason: @NonNls String,
                               private val myUpdater: PushedFilePropertiesUpdaterImpl) : DumbModeTask() {
    override fun performInDumbMode(indicator: ProgressIndicator) {
      indicator.isIndeterminate = true
      indicator.text = IndexingBundle.message("progress.indexing.scanning")
      val statistics = if (!ApplicationManager.getApplication().isUnitTestMode || shouldDumpInUnitTestMode) {
        ChangedFilesPushingStatistics(myReason)
      }
      else {
        null
      }
      (GistManager.getInstance() as GistManagerImpl).runWithMergingDependentCacheInvalidations {
        runBlockingCancellable {
          withContext(Dispatchers.IO) {
            myUpdater.performDelayedPushTasks(statistics)
          }
        }
      }
    }

    override fun tryMergeWith(taskFromQueue: DumbModeTask): DumbModeTask? {
      if (taskFromQueue is MyDumbModeTask && taskFromQueue.myUpdater == myUpdater) return this
      return null
    }

    override fun toString(): String {
      return super.toString() + " (reason: " + myReason + ")"
    }
  }

  companion object {
    private val LOG = Logger.getInstance(
      PushedFilePropertiesUpdater::class.java)

    private val SCANNING_PARALLELISM: Int = UnindexedFilesUpdater.getNumberOfScanningThreads()

    private fun getFile(event: VFileEvent): VirtualFile? {
      var file = event.file
      if (event is VFileCopyEvent) {
        file = event.newParent.findChild(event.newChildName)
      }
      return file
    }

    fun applyScannersToFile(fileOrDir: VirtualFile, sessions: List<IndexableFileVisitor>) {
      for (session in sessions) {
        try {
          session.visitFile(fileOrDir)
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (e: Exception) {
          LOG.error("Failed to visit file", e, Attachment("filePath.txt", fileOrDir.path))
        }
      }
    }

    fun finishVisitors(sessions: List<IndexableFileVisitor>) {
      for (session in sessions) {
        session.visitingFinished()
      }
    }

    private fun <T> findNewPusherValue(project: Project, fileOrDir: VirtualFile, pusher: FilePropertyPusher<out T>, moduleValue: T?): T {
      //Do not check fileOrDir.getUserData() as it may be outdated.
      val immediateValue = pusher.getImmediateValue(project, fileOrDir)
      if (immediateValue != null) return immediateValue
      if (moduleValue != null) return moduleValue
      return findNewPusherValueFromParent(project, fileOrDir, pusher)
    }

    private fun <T> findNewPusherValueFromParent(project: Project, fileOrDir: VirtualFile, pusher: FilePropertyPusher<out T>): T {
      val parent = fileOrDir.parent
      if (parent != null && ProjectFileIndex.getInstance(project).isInContent(parent)) {
        val userValue = pusher.filePropertyKey.getPersistentValue(parent)
        if (userValue != null) return userValue
        return findNewPusherValue(project, parent, pusher, null)
      }
      val projectValue = pusher.getImmediateValue(project, null)
      return projectValue ?: pusher.defaultValue
    }

    @JvmStatic
    fun getModuleImmediateValues(pushers: List<FilePropertyPusher<*>>,
                                 module: Module): Array<Any?> {
      val moduleValues = arrayOfNulls<Any>(pushers.size)
      for (i in moduleValues.indices) {
        moduleValues[i] = pushers[i].getImmediateValue(module)
      }
      return moduleValues
    }

    @JvmStatic
    fun getImmediateValuesEx(pushers: List<FilePropertyPusherEx<*>>,
                             origin: IndexableSetOrigin): Array<Any?> {
      val moduleValues = arrayOfNulls<Any>(pushers.size)
      for (i in moduleValues.indices) {
        moduleValues[i] = pushers[i].getImmediateValueEx(origin)
      }
      return moduleValues
    }

    private fun generateScanTasks(project: Project,
                                  iteratorProducer: Function<in Module, out ContentIteratorEx>): List<Runnable> {
      val modulesSequence = ReadAction.compute<Sequence<ModuleEntity>, RuntimeException> {
        WorkspaceModel.getInstance(project).currentSnapshot.entities(
          ModuleEntity::class.java)
      }
      val moduleEntities = modulesSequence.toList()
      val indexableFilesDeduplicateFilter = IndexableFilesDeduplicateFilter.create()

      return moduleEntities.flatMap { moduleEntity: ModuleEntity ->
        ReadAction.compute<List<Runnable>, RuntimeException> {
          val storage: EntityStorage = WorkspaceModel.getInstance(project).currentSnapshot
          val module: Module? = moduleEntity.findModule(storage)
          if (module == null) {
            return@compute emptyList()
          }
          ProgressManager.checkCanceled()
          createIterators(moduleEntity, storage, project).map { it: IndexableFilesIterator ->
            val iterator: ContentIteratorEx = iteratorProducer.apply(module)
            Runnable {
              it.iterateFiles(project, iterator, indexableFilesDeduplicateFilter)
            }
          }
        }
      }
    }

    @JvmStatic
    fun scanProject(project: Project,
                    iteratorProducer: Function<in Module, out ContentIteratorEx>) {
      val tasks = generateScanTasks(project, iteratorProducer)
      invokeConcurrentlyIfPossible(tasks)
    }

    fun invokeConcurrentlyIfPossible(tasks: List<Runnable>) {
      if (tasks.isEmpty()) return
      val synchronous = (isSynchronousTaskExecution || tasks.size == 1)

      if (synchronous) {
        for (r in tasks) r.run()
        return
      } else {
        // at the moment, we allow `invokeConcurrentlyIfPossible` invocation under WA if there is only one task to execute.
        LOG.assertTrue(!ApplicationManager.getApplication().isWriteAccessAllowed, "Write access is not allowed here")
      }

      val tasksQueue = ConcurrentLinkedQueue(tasks)
      runOnAllThreads {
        var runnable: Runnable? = tasksQueue.poll()
        while (runnable != null) {
          runnable.run()
          runnable = tasksQueue.poll()
        }
      }
    }

    private fun reloadPsi(file: VirtualFile, project: Project) {
      val fileManager = PsiManagerEx.getInstanceEx(project).fileManager as FileManagerEx
      if (fileManager.findCachedViewProvider(file) != null) {
        ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), project.disposed
        ) { WriteAction.run<RuntimeException> { fileManager.forceReload(file) } }
      }
    }

    private val filePushers: List<FilePropertyPusher<*>>
      get() = FilePropertyPusher.EP_NAME.extensionList.filter({ pusher: FilePropertyPusher<*> -> !pusher.pushDirectoriesOnly() })
  }
}
