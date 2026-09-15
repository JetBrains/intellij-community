// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdleTracker
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.SaveAndSyncHandlerListener
import com.intellij.idea.AppMode
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.CoroutineSupport
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.ui
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.isControlFlowException
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.RefreshSession
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector.logBackgroundRefresh
import com.intellij.openapi.wm.IdeFrame
import com.intellij.platform.backend.observation.Observation
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val EP_NAME = ExtensionPointName<SaveAndSyncHandlerListener>("com.intellij.saveAndSyncHandlerListener")
private val LISTEN_DELAY = 15.seconds

@OptIn(FlowPreview::class)
internal class SaveAndSyncHandlerImpl @JvmOverloads constructor(
  private val coroutineScope: CoroutineScope,
  listenDelay: Duration = LISTEN_DELAY,
) : SaveAndSyncHandler() {
  private val refreshKnownLocalRootsRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val scopedVfsRefreshScheduler = ScopedVfsRefreshScheduler()
  private val refreshOpenedFilesRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val saveRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val blockSaveOnFrameDeactivationCount = AtomicInteger()
  private val blockSyncCount = AtomicInteger()
  private val suppressPeriodicRefreshReasons = CopyOnWriteArrayList<String>()

  private val saveAppAndProjectsSettingsTask = SaveTask()
  private val saveQueue = ArrayDeque<SaveTask>()
  private val currentJob = AtomicReference<Job?>()

  private val forceExecuteImmediatelyState = AtomicBoolean()

  init {
    coroutineScope.launch {
      // add listeners after some delay - doesn't make sense to listen earlier
      delay(listenDelay)

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
              notifyBeforeRefresh()
              doRefreshAllKnownLocalRoots(refreshQueue, refreshSession)
            }
          }
      }

      scopedVfsRefreshScheduler.launchProcessing(
        coroutineScope = this,
        refreshQueue = refreshQueue,
        refreshGate = { getVfsRefreshGate(settings) },
        beforeRefresh = ::notifyBeforeRefresh,
      )

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
        val saveScope = this
        // not collectLatest - wait for previous execution
        saveRequests.collect {
          val forceExecuteImmediately = forceExecuteImmediatelyState.compareAndSet(true, false)
          if (!forceExecuteImmediately) {
            delay(300.milliseconds)
          }

          val job = synchronized(saveQueue) {
            if (blockSaveOnFrameDeactivationCount.get() != 0) {
              return@collect
            }
            saveScope.launch(start = CoroutineStart.LAZY) {
              processSaveTasks(forceExecuteImmediately)
            }.also { currentJob.set(it) }
          }
          try {
            job.start()
            job.join()
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

  private suspend fun notifyBeforeRefresh() {
    for (listener in EP_NAME.extensionList) {
      runCatching {
        listener.beforeRefresh()
      }.getOrLogException(LOG)
    }
  }

  private fun isSyncBlocked(settings: GeneralSettings): Boolean {
    return getVfsRefreshGate(settings) != ScopedVfsRefreshGate.Ready
  }

  private fun getVfsRefreshGate(settings: GeneralSettings): ScopedVfsRefreshGate {
    if (!settings.isSyncOnFrameActivation) {
      LOG.debug("VFS refresh rejected: isSyncOnFrameActivation=false")
      return ScopedVfsRefreshGate.DropPending
    }
    return if (isSyncBlockedTemporarily()) ScopedVfsRefreshGate.RetryLater else ScopedVfsRefreshGate.Ready
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
      val task = synchronized(saveQueue) {
        if (blockSaveOnFrameDeactivationCount.get() != 0 || ProgressManager.getInstance().hasModalProgressIndicator()) {
          return
        }
        saveQueue.pollFirst() ?: return
      }
      if (task.project?.isDisposed == true) {
        continue
      }

      try {
        for (listener in EP_NAME.extensionList) {
          try {
            listener.beforeSave(task, forceExecuteImmediately)
          }
          catch (e: Throwable) {
            rethrowControlFlowException(e)
            LOG.error(e)
          }
        }
        saveProjectsAndApp(forceSavingAllSettings = task.forceSavingAllSettings, onlyProject = task.project)
      }
      catch (e: Throwable) {
        if (e.isControlFlowException) {
          // an interrupted task runs again once the save is unblocked
          addToSaveQueue(task)
          requestSave()
          throw e
        }
        LOG.error(e)
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun listenIdleAndActivate(settings: GeneralSettings) {
    if (settings.isAutoSaveIfInactive && settings.inactiveTimeout.seconds <= LISTEN_DELAY) {
      executeOnIdle()
    }

    val backgroundRefreshController = createBackgroundRefreshController(settings)
    backgroundRefreshController.start()

    ApplicationManager.getApplication().messageBus.connect(coroutineScope)
      .subscribe(ApplicationActivationListener.TOPIC, object : ApplicationActivationListener {

        override fun applicationDeactivated(ideFrame: IdeFrame) {
          externalChangesModificationTracker.incModificationCount()

          if (settings.isSaveOnFrameDeactivation && canSyncOrSave()) {
            // for many tasks (compilation, web development, etc.), it is important to save documents on frame deactivation ASAP
            if (!AppMode.isRemoteDevHost() && Registry.`is`("document.save.in.background.allowed")) {
              saveDocumentsInBackgroundWriteAction()
            }
            else {
              WriteIntentReadAction.run {
                (FileDocumentManager.getInstance() as FileDocumentManagerImpl).saveAllDocuments(false)
              }
              //flush pending IO tasks, if any:
              ManagingFS.getInstance().flushPendingUpdates()
            }
            if (addToSaveQueue(saveAppAndProjectsSettingsTask)) {
              requestSave()
            }
          }
          backgroundRefreshController.applicationDeactivated()
        }

        override fun applicationActivated(ideFrame: IdeFrame) {
          backgroundRefreshController.applicationActivated()

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

  private val savingDispatcher = Dispatchers.IO.limitedParallelism(1)

  private fun saveDocumentsInBackgroundWriteAction() {
    coroutineScope.launch(CoroutineName("Saving documents on frame deactivation") + savingDispatcher + NonCancellable) {
      (FileDocumentManager.getInstance() as FileDocumentManagerImpl).saveAllDocuments(false)
      //flush pending IO tasks, if any:
      ManagingFS.getInstance().flushPendingUpdates()
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

  override fun saveSettingsUnderModalProgress(componentManager: ComponentManager): Boolean {
    return saveSettingsUnderModalProgress(listOf(componentManager))
  }

  override fun saveSettingsUnderModalProgress(componentManagers: List<ComponentManager>): Boolean {
    EDT.assertIsEdt()
    val targets = componentManagers.distinct()
    require(targets.none { it is Project && it.isDefault }) { "Must not save the default project here" }
    val project = targets.singleOrNull() as? Project
    var saved = false
    withDisabledAutoSaveBlocking {
      val interruptedJob = currentJob.get()
      val coveredTasks = mutableListOf<SaveTask>()
      try {
        @Suppress("DialogTitleCapitalization")
        runWithModalProgressBlocking(
          owner = if (project == null) ModalTaskOwner.guess() else ModalTaskOwner.project(project),
          title = if (project == null) IdeBundle.message("progress.saving.app") else getProgressTitle(project),
          cancellation = TaskCancellation.nonCancellable(),
        ) {
          interruptedJob?.join()
          val coversAllProjects = targets.any { it is Application } && getOpenedProjects().all { it in targets }
          synchronized(saveQueue) {
            saveQueue.removeAll { task ->
              val covered = if (task.project == null) coversAllProjects else targets.any { it === task.project }
              if (covered) {
                coveredTasks.add(task)
              }
              covered
            }
          }
          saved = saveSettingsBatch(targets)
        }
      }
      finally {
        if (!saved) {
          for (task in coveredTasks) {
            addToSaveQueue(task)
          }
        }
      }
    }
    return saved
  }

  private fun canSyncOrSave(): Boolean = !LaterInvocator.isInModalContext() && !ProgressManager.getInstance().hasModalProgressIndicator()

  private suspend fun refreshAllLocalRootsInBackground(queue: RefreshQueue): Boolean {
    val roots = ManagingFS.getInstance().localRoots
    if (roots.isEmpty()) {
      return false
    }
    if (suppressPeriodicRefreshReasons.isNotEmpty()) {
      LOG.trace { "Periodic background VFS refresh skipped, suppressed by: ${suppressPeriodicRefreshReasons.joinToString()}" }
      return false
    }
    if (isSyncBlockedTemporarily() || roots.none { it is NewVirtualFile && it.isDirty }) {
      return false
    }

    LOG.debug("VFS refresh started (background sync)")
    queue.refresh(true, roots.toList())
    return true
  }

  override fun scheduleRefresh() {
    externalChangesModificationTracker.incModificationCount()
    check(refreshOpenedFilesRequests.tryEmit(Unit))
    check(refreshKnownLocalRootsRequests.tryEmit(Unit))
  }

  override fun scheduleRefresh(paths: Collection<Path>) {
    if (paths.isEmpty()) {
      return
    }
    externalChangesModificationTracker.incModificationCount()
    scopedVfsRefreshScheduler.schedule(paths)
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

  private fun disableAutoSave(): AccessToken {
    blockSaveOnFrameDeactivation()
    blockSyncOnFrameActivation()
    return object : AccessToken() {
      override fun finish() {
        unblockSaveOnFrameDeactivation()
        unblockSyncOnFrameActivation()
      }
    }
  }

  override fun <T> withDisabledAutoSaveBlocking(action: () -> T): T {
    return disableAutoSave().use {
      action()
    }
  }

  override suspend fun <T> withDisabledAutoSave(action: suspend CoroutineScope.() -> T): T {
    return disableAutoSave().use {
      coroutineScope {
        action()
      }
    }
  }

  override fun blockSaveOnFrameDeactivation() {
    LOG.debug("save blocked")
    val job = synchronized(saveQueue) {
      blockSaveOnFrameDeactivationCount.incrementAndGet()
      currentJob.get()
    }
    job?.cancel(CancellationException("Save on frame deactivation is disabled"))
  }

  override fun unblockSaveOnFrameDeactivation() {
    val resume = synchronized(saveQueue) {
      blockSaveOnFrameDeactivationCount.decrementAndGet() == 0 && saveQueue.isNotEmpty()
    }
    if (resume) {
      requestSave()
    }
    LOG.debug("save unblocked")
  }

  override fun blockSyncOnFrameActivation() {
    LOG.debug("sync blocked")
    blockSyncCount.incrementAndGet()
  }

  override fun unblockSyncOnFrameActivation() {
    if (blockSyncCount.decrementAndGet() == 0) {
      scopedVfsRefreshScheduler.requestProcessing()
    }
    LOG.debug("sync unblocked")
  }

  override fun suppressPeriodicRefresh(reason: String): AccessToken {
    suppressPeriodicRefreshReasons.add(reason)
    LOG.info("Periodic background VFS refresh suppressed: $reason")
    val released = AtomicBoolean()
    return object : AccessToken() {
      override fun finish() {
        if (released.compareAndSet(false, true)) {
          suppressPeriodicRefreshReasons.remove(reason)
          LOG.info("Periodic background VFS refresh resumed: $reason")
        }
      }
    }
  }


  private suspend fun createBackgroundRefreshController(settings: GeneralSettings): BackgroundRefreshController {
    val registryManager = serviceAsync<RegistryManager>()
    return if (registryManager.`is`("vfs.background.refresh.on.idle")) {
      IdleBackgroundRefreshController(settings, registryManager)
    }
    else {
      UnfocusedBackgroundRefreshController(settings, registryManager)
    }
  }


  private interface BackgroundRefreshController {
    fun start()
    fun applicationActivated()
    fun applicationDeactivated()
  }

  private enum class BackgroundRefreshEvents { START, STOP }

  /**
   * Runs vfs refresh in the background when the user is inactive
   */
  @OptIn(FlowPreview::class)
  private inner class IdleBackgroundRefreshController(
    private val settings: GeneralSettings,
    private val registryManager: RegistryManager,
  ) : BackgroundRefreshController {
    private var isStarted = false
    private var refreshJob: Job? = null

    @Volatile
    private var jobNumber: Int = 0

    override fun start() {
      check(!isStarted)
      isStarted = true

      coroutineScope.launch(CoroutineName("idle background sync")) {
        val interval = registryManager.backgroundVfsRefreshInterval()
        val idleTracker = serviceAsync<IdleTracker>()

        val inactivityEvents = idleTracker.events.debounce(interval).map { BackgroundRefreshEvents.START }
        // drop(1) to skip the repeating first event immediately firing
        val activityEvents = idleTracker.events.drop(1).map { BackgroundRefreshEvents.STOP }

        merge(inactivityEvents, activityEvents).collect { event ->
          when (event) {
            BackgroundRefreshEvents.START -> this@launch.startRefreshWindow()
            BackgroundRefreshEvents.STOP -> stopRefreshWindow()
          }
        }
      }
    }

    private fun CoroutineScope.startRefreshWindow() {
      if (!settings.isBackgroundSync || refreshJob != null) return

      val currentJobNumber = jobNumber
      refreshJob = this.launch(CoroutineName("background sync")) {
        val interval = registryManager.backgroundVfsRefreshInterval()
        backgroundRefreshWindow(settings, interval) { currentJobNumber == jobNumber }
      }
    }

    private fun stopRefreshWindow() {
      jobNumber += 1
      refreshJob = null
    }

    override fun applicationActivated() = Unit
    override fun applicationDeactivated() = Unit
  }

  /**
   * Runs vfs refresh in the background when ide frame is not focused
   */
  private inner class UnfocusedBackgroundRefreshController(
    private val settings: GeneralSettings,
    private val registryManager: RegistryManager,
  ) : BackgroundRefreshController {
    private var refreshJob: Job? = null
    private var isStarted = false

    override fun start() {
      check(!isStarted)
      isStarted = true
    }

    override fun applicationActivated() {
      cancelBackgroundRefreshJob()
    }

    override fun applicationDeactivated() {
      cancelBackgroundRefreshJob()
      if (settings.isBackgroundSync) {
        val interval = registryManager.backgroundVfsRefreshInterval()
        refreshJob = coroutineScope.launch(CoroutineName("background sync")) {
          delay(interval)
          backgroundRefreshWindow(settings, interval) { true }
        }
      }
    }

    private fun cancelBackgroundRefreshJob() {
      refreshJob?.cancel()
      refreshJob = null
    }
  }

  private suspend fun backgroundRefreshWindow(settings: GeneralSettings, interval: Duration, keepRefreshing: () -> Boolean) {
    val startTime = System.nanoTime()
    val sessions = AtomicInteger()
    val queue = serviceAsync<RefreshQueue>()
    try {
      while (keepRefreshing()) {
        val projectManager = ProjectManager.getInstanceIfCreated()

        // do not store projects themselves to avoid "leak" if a project is closed while waiting for other to be configured
        val projectHashes = projectManager?.openProjects?.filter { !it.isDisposed }?.map { it.locationHash }.orEmpty()
        val canTryRefresh = settings.isBackgroundSync &&
                            projectHashes.isNotEmpty() &&
                            projectManager != null &&
                            // wait for all projects to be configured. `true` if all are already configured
                            awaitAllConfigurations(projectManager, projectHashes)

        if (canTryRefresh && keepRefreshing() && refreshAllLocalRootsInBackground(queue)) {
          sessions.incrementAndGet()
        }
        delay(interval)
      }
    }
    finally {
      if (coroutineScope.isActive) {
        logBackgroundRefresh(NANOSECONDS.toMillis(System.nanoTime() - startTime), sessions.get(), 0)
      }
    }
  }
}

/**
 * @return `true` if all projects are already configured. `false`, if a project was configured during the execution
 * @see Observation.awaitConfiguration
 */
private suspend fun awaitAllConfigurations(projectManager: ProjectManager, projectHashes: List<@NonNls String>): Boolean {
  return projectHashes.all { hash ->
    val project = projectManager.findOpenProjectByHash(hash) ?: return@all false
    !Observation.awaitConfiguration(project) { message -> LOG.trace("Periodic VFS refresh is blocked because project.name=${project.name} being configured, message=$message") }
  }
}

private suspend fun doRefreshOpenedFiles(refreshQueue: RefreshQueue) {
  val files = getOpenedProjects()
    .flatMap { it.serviceIfCreated<FileEditorManager>()?.selectedEditorWithRemotes?.asSequence() ?: emptySequence() }
    .flatMap { it.filesToRefresh }
    .filter { it is NewVirtualFile }
    .toList()
  if (files.isEmpty()) {
    return
  }

  withContext(Dispatchers.Default) {
    refreshQueue.refreshWithHighPriority(false, files)
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

private fun RegistryManager.backgroundVfsRefreshInterval(): Duration {
  return intValue("vfs.background.refresh.interval", 15).coerceIn(0, Int.MAX_VALUE).seconds
}
