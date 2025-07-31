// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.conversion.ConversionService
import com.intellij.ide.*
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.newvfs.*
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector
import com.intellij.openapi.wm.IdeFrame
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.project.stateStore
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.*
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val EP_NAME = ExtensionPointName<SaveAndSyncHandlerListener>("com.intellij.saveAndSyncHandlerListener")
private val LISTEN_DELAY = 15.seconds

@OptIn(FlowPreview::class)
private class SaveAndSyncHandlerImpl(private val coroutineScope: CoroutineScope) : SaveAndSyncHandler() {
  private val refreshKnownLocalRootsRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val refreshOpenedFilesRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val saveRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val blockSaveOnFrameDeactivationCount = AtomicInteger()
  private val blockSyncCount = AtomicInteger()

  private val saveAppAndProjectsSettingsTask = SaveTask()
  private val saveQueue = ArrayDeque<SaveTask>()
  private val currentJob = AtomicReference<Job?>()

  private val forceExecuteImmediatelyState = AtomicBoolean()

  init {
    coroutineScope.launch {
      // add listeners after some delay - doesn't make sense to listen earlier
      delay(LISTEN_DELAY)

      val settings = serviceAsync<GeneralSettings>()
      launch {
        listenIdleAndActivate(settings)
      }

      val refreshQueue = serviceAsync<RefreshQueue>()

      launch(CoroutineName("refresh known local roots requests flow processing")) {
        val refreshSession = AtomicReference<RefreshSession>()
        coroutineContext.job.invokeOnCompletion {
          refreshSession.getAndSet(null)?.cancel()
        }

        // not collectLatest - wait for previous execution
        refreshKnownLocalRootsRequests
          .debounce(300.milliseconds)
          .collect {
            if (!isSyncBlocked(settings)) {
              for (listener in EP_NAME.extensionList) {
                runCatching {
                  listener.beforeRefresh()
                }.getOrLogException(LOG)
              }

              doRefreshAllKnownLocalRoots(refreshQueue, refreshSession)
            }
          }
      }

      launch(CoroutineName("refresh opened files requests flow processing")) {
        // not collectLatest - wait for previous execution
        refreshOpenedFilesRequests
          .debounce(300.milliseconds)
          .collect {
            if (!isSyncBlocked(settings)) {
              doRefreshOpenedFiles(refreshQueue)
            }
          }
      }

      launch(CoroutineName("save requests flow processing")) {
        // not collectLatest - wait for previous execution
        saveRequests.collect {
          val forceExecuteImmediately = forceExecuteImmediatelyState.compareAndSet(true, false)
          if (!forceExecuteImmediately) {
            delay(300.milliseconds)
          }

          if (blockSaveOnFrameDeactivationCount.get() != 0) {
            return@collect
          }

          val job = currentJob.updateAndGet { oldJob ->
            oldJob?.cancel()
            launch(start = CoroutineStart.LAZY) {
              processSaveTasks(forceExecuteImmediately)
            }
          }!!
          try {
            if (job.start()) {
              job.join()
            }
          }
          catch (@Suppress("IncorrectCancellationExceptionHandling") _: CancellationException) {
          }
          finally {
            currentJob.compareAndSet(job, null)
          }
        }
      }
    }
  }

  private fun doRefreshAllKnownLocalRoots(refreshQueue: RefreshQueue, refreshSession: AtomicReference<RefreshSession>) {
    // We cannot use async=false because `semaphore.waitFor` is used, which can lead to thread starvation.
    // (While there's compensation for this, it's still not ideal.)
    // The `debounce` logic never worked as intended — we want to fix that.
    // A suspend function for refresh will be available soon, so it's better to wait for that.
    val session = refreshQueue.createSession(
      /* async = */ true,
      /* recursive = */ true,
      /* finishRunnable = */ null,
      /* state = */ ModalityState.nonModal(),
    )
    session.addAllFiles(*ManagingFS.getInstance().localRoots)

    refreshSession.getAndSet(session)?.cancel()
    LOG.debug("VFS refresh started (refreshRequests)")
    session.launch()
  }

  private fun isSyncBlocked(settings: GeneralSettings): Boolean {
    if (!settings.isSyncOnFrameActivation) {
      LOG.debug("VFS refresh rejected: isSyncOnFrameActivation=false")
      return true
    }
    return isSyncBlockedTemporarily()
  }

  private fun isSyncBlockedTemporarily(): Boolean {
    val blockSyncOnFrameActivationCount = blockSyncCount.get()
    if (blockSyncOnFrameActivationCount == 0) {
      return false
    }
    LOG.debug { "VFS refresh rejected: blocked=$blockSyncOnFrameActivationCount" }
    return true
  }

  /**
   * If there is already running a job, it doesn't mean that queue is processed - maybe paused on delay.
   * But even if `forceExecuteImmediately = true` specified, the job is not re-added.
   * That's ok - client doesn't expect that `forceExecuteImmediately` means "executes immediately", it means "do save without regular delay".
   */
  private fun requestSave(forceExecuteImmediately: Boolean = false) {
    if (blockSaveOnFrameDeactivationCount.get() != 0) {
      return
    }

    if (forceExecuteImmediately) {
      forceExecuteImmediatelyState.set(true)
    }
    check(saveRequests.tryEmit(Unit))
  }

  private suspend fun processSaveTasks(forceExecuteImmediately: Boolean) {
    while (true) {
      if (blockSaveOnFrameDeactivationCount.get() != 0) {
        return
      }

      val task = synchronized(saveQueue) {
        saveQueue.pollFirst() ?: return
      }

      if (task.project?.isDisposed == true) {
        continue
      }

      if (blockSaveOnFrameDeactivationCount.get() > 0 || ProgressManager.getInstance().hasModalProgressIndicator()) {
        return
      }

      for (listener in EP_NAME.extensionList) {
        runCatching {
          listener.beforeSave(task, forceExecuteImmediately)
        }.getOrLogException(LOG)
      }

      runCatching {
        saveProjectsAndApp(forceSavingAllSettings = task.forceSavingAllSettings, onlyProject = task.project)
      }.getOrLogException(LOG)
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun listenIdleAndActivate(settings: GeneralSettings) {
    if (settings.inactiveTimeout.seconds <= LISTEN_DELAY) {
      executeOnIdle()
    }

    ApplicationManager.getApplication().messageBus.connect(coroutineScope)
      .subscribe(ApplicationActivationListener.TOPIC, object : ApplicationActivationListener {
        private var backgroundRefreshJob: Job? = null

        override fun applicationDeactivated(ideFrame: IdeFrame) {
          externalChangesModificationTracker.incModificationCount()

          if (settings.isSaveOnFrameDeactivation && canSyncOrSave()) {
            // for many tasks (compilation, web development, etc.), it is important to save documents on frame deactivation ASAP
            WriteIntentReadAction.run {
              (FileDocumentManager.getInstance() as FileDocumentManagerImpl).saveAllDocuments(false)
            }
            if (addToSaveQueue(saveAppAndProjectsSettingsTask)) {
              requestSave()
            }
          }

          if (settings.isBackgroundSync) {
            backgroundRefreshJob = startBackgroundSync()
          }
        }

        override fun applicationActivated(ideFrame: IdeFrame) {
          backgroundRefreshJob?.let {
            backgroundRefreshJob = null
            it.cancel()
          }

          if (settings.isSyncOnFrameActivation && !isSyncBlocked(settings)) {
            scheduleRefresh()
          }
        }
      })

    generalSettingFlow(settings, GeneralSettings.PropertyNames.autoSaveIfInactive) { it.isAutoSaveIfInactive }
      .filter { it }
      .flatMapConcat {
        generalSettingFlow(settings, GeneralSettings.PropertyNames.inactiveTimeout) { it.inactiveTimeout.seconds }
      }
      .distinctUntilChanged()
      .flatMapConcat { delay ->
        serviceAsync<IdleTracker>().events.debounce(delay)
      }
      .collect {
        executeOnIdle()
      }
  }

  private suspend fun executeOnIdle() {
    val fileDocumentManager = serviceAsync<FileDocumentManager>() as FileDocumentManagerImpl
    @Suppress("UsagesOfObsoleteApi")
    withContext(Dispatchers.ui(CoroutineSupport.UiDispatcherKind.LEGACY)) {
      fileDocumentManager.saveAllDocuments(false)
    }
  }

  override fun scheduleSave(task: SaveTask, forceExecuteImmediately: Boolean) {
    if (addToSaveQueue(task) || forceExecuteImmediately) {
      requestSave(forceExecuteImmediately)
    }
  }

  private fun addToSaveQueue(task: SaveTask): Boolean {
    synchronized(saveQueue) {
      if (task.project == null) {
        if (saveQueue.any { it.project == null }) {
          return false
        }

        saveQueue.removeAll { it.project != null }
      }
      else if (saveQueue.any { it.project == null || it.project === task.project }) {
        return false
      }

      return when {
        saveQueue.contains(task) -> false
        else -> saveQueue.add(task)
      }
    }
  }

  /**
   * On app or project closing save is performed. In EDT. It means that if there is already running save in a pooled thread,
   * deadlock may occur because some saving activities require EDT with modality state "not modal" (by intention).
   */
  override fun saveSettingsUnderModalProgress(componentManager: ComponentManager): Boolean {
    // saveSettingsUnderModalProgress is intended to be called only in EDT because
    // otherwise wrapping into a modal progress task is not required and `saveSettings` should be called directly
    EDT.assertIsEdt()

    var isSavedSuccessfully = true
    var isAutoSaveCancelled = false
    disableAutoSave().use {
      val currentJob = currentJob.getAndSet(null)
      currentJob?.let {
        it.cancel(CancellationException("Superseded by explicit save"))
        isAutoSaveCancelled = true
      }

      synchronized(saveQueue) {
        if (componentManager is Application) {
          saveQueue.removeAll { it.project == null }
        }
        else {
          saveQueue.removeAll { it.project === componentManager }
        }
      }

      val project = (componentManager as? Project)?.takeIf { !it.isDefault }
      @Suppress("DialogTitleCapitalization")
      runWithModalProgressBlocking(
        owner = if (project == null) ModalTaskOwner.guess() else ModalTaskOwner.project(project),
        title = getProgressTitle(componentManager),
        cancellation = TaskCancellation.nonCancellable(),
      ) {
        withContext(NonCancellable) {
          // ensure that is fully canceled
          currentJob?.join()

          isSavedSuccessfully = saveSettings(componentManager, forceSavingAllSettings = true)

          if (project != null && !ApplicationManager.getApplication().isUnitTestMode) {
            val stateStore = project.stateStore
            val path = if (stateStore.storageScheme == StorageScheme.DIRECTORY_BASED) stateStore.projectBasePath else stateStore.projectFilePath
            // update last modified for all project files modified between project open and close
            (componentManager as ComponentManagerEx).getServiceAsyncIfDefined(ConversionService::class.java)?.saveConversionResult(path)
          }
        }
      }
    }

    if (isAutoSaveCancelled) {
      requestSave()
    }
    return isSavedSuccessfully
  }

  private fun canSyncOrSave(): Boolean = !LaterInvocator.isInModalContext() && !ProgressManager.getInstance().hasModalProgressIndicator()

  private fun startBackgroundSync(): Job {
    LOG.debug("starting background VFS sync")
    val startTime = System.nanoTime()
    val sessions = AtomicInteger()
    val events = AtomicInteger()
    val job = coroutineScope.launch(CoroutineName("background sync")) {
      val roots = listOf(*ManagingFS.getInstance().localRoots)
      val queue = RefreshQueue.getInstance() as RefreshQueueImpl
      val interval = Registry.intValue("vfs.background.refresh.interval", 15).coerceIn(0, Int.MAX_VALUE).seconds
      while (true) {
        delay(interval)
        if (!isSyncBlockedTemporarily() || roots.any { it is NewVirtualFile && it.isDirty }) {
          val session = queue.createBackgroundRefreshSession(roots)
          session.launch()
          sessions.incrementAndGet()
          events.addAndGet(session.metric("events") as Int)
        }
      }
    }
    job.invokeOnCompletion {
      if (coroutineScope.isActive) {
        VfsUsageCollector.logBackgroundRefresh(NANOSECONDS.toMillis(System.nanoTime() - startTime), sessions.get(), events.get())
      }
    }
    return job
  }

  override fun scheduleRefresh() {
    externalChangesModificationTracker.incModificationCount()
    check(refreshOpenedFilesRequests.tryEmit(Unit))
    check(refreshKnownLocalRootsRequests.tryEmit(Unit))
  }

  override fun maybeRefresh(modalityState: ModalityState) {
    if (isSyncBlocked(GeneralSettings.getInstance())) {
      return
    }

    val session = RefreshQueue.getInstance().createSession(true, true, null, modalityState)
    session.addAllFiles(*ManagingFS.getInstance().localRoots)
    session.launch()
    LOG.debug("VFS refresh started")
  }

  override fun refreshOpenFiles() {
    check(refreshOpenedFilesRequests.tryEmit(Unit))
  }

  override fun disableAutoSave(): AccessToken {
    blockSaveOnFrameDeactivation()
    blockSyncOnFrameActivation()
    return object : AccessToken() {
      override fun finish() {
        unblockSaveOnFrameDeactivation()
        unblockSyncOnFrameActivation()
      }
    }
  }

  override fun blockSaveOnFrameDeactivation() {
    LOG.debug("save blocked")
    currentJob.getAndSet(null)?.cancel(CancellationException("Save on frame deactivation is disabled"))
    blockSaveOnFrameDeactivationCount.incrementAndGet()
  }

  override fun unblockSaveOnFrameDeactivation() {
    blockSaveOnFrameDeactivationCount.decrementAndGet()
    LOG.debug("save unblocked")
  }

  override fun blockSyncOnFrameActivation() {
    LOG.debug("sync blocked")
    blockSyncCount.incrementAndGet()
  }

  override fun unblockSyncOnFrameActivation() {
    blockSyncCount.decrementAndGet()
    LOG.debug("sync unblocked")
  }
}

private suspend fun doRefreshOpenedFiles(refreshQueue: RefreshQueue) {
  val files = getOpenedProjects()
    .flatMap { it.serviceIfCreated<FileEditorManager>()?.selectedEditors?.asSequence() ?: emptySequence() }
    .flatMap { it.filesToRefresh }
    .filter { it is NewVirtualFile }
    .toList()
  if (files.isEmpty()) {
    return
  }

  withContext(Dispatchers.EDT) {
    writeIntentReadAction {
      val session = refreshQueue.createSession(
        /* async = */ false,
        /* recursive = */ false,
        /* finishRunnable = */ null,
        /* state = */ ModalityState.nonModal(),
      )
      session.addAllFiles(files)
      session.launch()
    }
  }
}

private fun <T> generalSettingFlow(settings: GeneralSettings, name: GeneralSettings.PropertyNames, getter: (GeneralSettings) -> T): Flow<T> {
  return merge(
    settings.propertyChangedFlow
      .filter { it == name }
      .map { getter(GeneralSettings.getInstance()) },
    flowOf(getter(GeneralSettings.getInstance())),
  )
}

@NlsContexts.ProgressTitle
private fun getProgressTitle(componentManager: ComponentManager): String {
  if (componentManager is Project) {
    return IdeBundle.message("progress.saving.project", componentManager.name)
  }
  else {
    return IdeBundle.message("progress.saving.app")
  }
}