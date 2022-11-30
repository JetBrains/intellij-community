// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.CommonBundle
import com.intellij.codeWithMe.ClientId
import com.intellij.conversion.ConversionService
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.SaveAndSyncHandlerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.processOpenedProjects
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.wm.IdeFrame
import com.intellij.project.stateStore
import com.intellij.util.application
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import java.beans.PropertyChangeListener
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val LISTEN_DELAY = 15

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal class SaveAndSyncHandlerImpl : SaveAndSyncHandler(), Disposable {
  private val refreshRequests = MutableSharedFlow<Unit>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val saveRequests = MutableSharedFlow<Unit>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val blockSaveOnFrameDeactivationCount = AtomicInteger()
  private val blockSyncOnFrameActivationCount = AtomicInteger()

  @Volatile
  private var refreshSessionId = -1L

  private val saveQueue = ArrayDeque<SaveTask>()
  private val currentJob = AtomicReference<Job?>()

  private val eventPublisher = application.messageBus.syncPublisher(SaveAndSyncHandlerListener.TOPIC)
  private val forceExecuteImmediatelyState = AtomicBoolean()

  init {
    @Suppress("DEPRECATION")
    ApplicationManager.getApplication().coroutineScope.launch {
      launch(CoroutineName("refresh requests flow processing")) {
        // not collectLatest - wait for previous execution
        refreshRequests
          .debounce(300.milliseconds)
          .collect {
            doScheduledRefresh()
          }
      }
      launch(CoroutineName("save requests flow processing")) {
        // not collectLatest - wait for previous execution
        saveRequests
          .collect {
            val forceExecuteImmediately = forceExecuteImmediatelyState.compareAndSet(true, false)
            if (!forceExecuteImmediately) {
              delay(300.milliseconds)
            }

            if (blockSaveOnFrameDeactivationCount.get() != 0) {
              return@collect
            }

            val job = currentJob.updateAndGet { oldJob ->
              oldJob?.cancel()
              launch(start = CoroutineStart.LAZY) { processTasks(forceExecuteImmediately = forceExecuteImmediately) }
            }!!
            try {
              if (job.start()) {
                job.join()
              }
            }
            catch (ignore: CancellationException) {
            }
            finally {
              currentJob.compareAndSet(job, null)
            }
          }
      }

      // add listeners after some delay - doesn't make sense to listen earlier
      delay(15.seconds)
      withContext(Dispatchers.EDT) {
        addListeners(GeneralSettings.getInstance())
      }
    }.cancelOnDispose(this)
  }

  /**
   * If there is already running job, it doesn't mean that queue is processed - maybe paused on delay.
   * But even if `forceExecuteImmediately = true` specified, job is not re-added.
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

  private suspend fun processTasks(forceExecuteImmediately: Boolean) {
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

      runCatching {
        eventPublisher.beforeSave(task, forceExecuteImmediately)
        saveProjectsAndApp(forceSavingAllSettings = task.forceSavingAllSettings, onlyProject = task.project)
      }.getOrLogException(LOG)
    }
  }

  @RequiresEdt
  private fun addListeners(settings: GeneralSettings) {
    val idleListener = Runnable {
      if (settings.isAutoSaveIfInactive && canSyncOrSave()) {
        ClientId.withClientId(ClientId.ownerId) {
          (FileDocumentManagerImpl.getInstance() as FileDocumentManagerImpl).saveAllDocuments(false)
        }
      }
    }

    var disposable: Disposable? = null

    fun addIdleListener() {
      IdeEventQueue.getInstance().addIdleListener(idleListener, settings.inactiveTimeout * 1000)
      disposable = Disposable { IdeEventQueue.getInstance().removeIdleListener(idleListener) }
      Disposer.register(this, disposable!!)
    }

    settings.addPropertyChangeListener(GeneralSettings.PROP_INACTIVE_TIMEOUT, this, PropertyChangeListener {
      disposable?.let { Disposer.dispose(it) }
      addIdleListener()
    })

    addIdleListener()
    if (LISTEN_DELAY >= (settings.inactiveTimeout * 1000)) {
      idleListener.run()
    }

    val busConnection = ApplicationManager.getApplication().messageBus.connect(this)
    busConnection.subscribe(ApplicationActivationListener.TOPIC, object : ApplicationActivationListener {
      override fun applicationDeactivated(ideFrame: IdeFrame) {
        externalChangesModificationTracker.incModificationCount()
        if (!settings.isSaveOnFrameDeactivation || !canSyncOrSave()) {
          return
        }

        // for web development it is crucially important to save documents on frame deactivation as early as possible
        (FileDocumentManager.getInstance() as FileDocumentManagerImpl).saveAllDocuments(false)

        if (addToSaveQueue(saveAppAndProjectsSettingsTask)) {
          requestSave()
        }
      }

      override fun applicationActivated(ideFrame: IdeFrame) {
        if (!ApplicationManager.getApplication().isDisposed && settings.isSyncOnFrameActivation) {
          scheduleRefresh()
        }
      }
    })
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
   * deadlock may be occurred because some save activities requires EDT with modality state "not modal" (by intention).
   */
  override fun saveSettingsUnderModalProgress(componentManager: ComponentManager): Boolean {
    if (!ApplicationManager.getApplication().isDispatchThread) {
      throw IllegalStateException(
        "saveSettingsUnderModalProgress is intended to be called only in EDT because otherwise wrapping into modal progress task is not required" +
        " and `saveSettings` should be called directly")
    }

    var isSavedSuccessfully = true
    var isAutoSaveCancelled = false
    disableAutoSave().use {
      saveRequests.resetReplayCache()
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
      ProgressManager.getInstance().run(object : Task.Modal(project, getProgressTitle(componentManager), /* canBeCancelled = */ false) {
        override fun run(indicator: ProgressIndicator) {
          indicator.isIndeterminate = true

          runBlocking(CoreProgressManager.getCurrentThreadProgressModality().asContextElement()) {
            isSavedSuccessfully = saveSettings(componentManager, forceSavingAllSettings = true)
          }

          if (project != null && !ApplicationManager.getApplication().isUnitTestMode) {
            val stateStore = project.stateStore
            val path = if (stateStore.storageScheme == StorageScheme.DIRECTORY_BASED) stateStore.projectBasePath else stateStore.projectFilePath
            // update last modified for all project files that were modified between project open and close
            ConversionService.getInstance()?.saveConversionResult(path)
          }
        }
      })
    }

    if (isAutoSaveCancelled) {
      requestSave()
    }
    return isSavedSuccessfully
  }

  override fun dispose() {
    if (refreshSessionId != -1L) {
      RefreshQueue.getInstance().cancelSession(refreshSessionId)
    }
  }

  private fun canSyncOrSave(): Boolean {
    return !LaterInvocator.isInModalContext() && !ProgressManager.getInstance().hasModalProgressIndicator()
  }

  override fun scheduleRefresh() {
    externalChangesModificationTracker.incModificationCount()
    check(refreshRequests.tryEmit(Unit))
  }

  private suspend fun doScheduledRefresh() {
    withContext(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement()) {
      eventPublisher.beforeRefresh()
      refreshOpenFiles()
      maybeRefresh(ModalityState.NON_MODAL)
    }
  }

  override fun maybeRefresh(modalityState: ModalityState) {
    if (blockSyncOnFrameActivationCount.get() != 0 || !GeneralSettings.getInstance().isSyncOnFrameActivation) {
      LOG.debug {
        "vfs refresh rejected, blocked: ${blockSyncOnFrameActivationCount.get() != 0}, " +
        "isSyncOnFrameActivation: ${GeneralSettings.getInstance().isSyncOnFrameActivation}"
      }
      return
    }

    val queue = RefreshQueue.getInstance()
    queue.cancelSession(refreshSessionId)

    val session = queue.createSession(true, true, null, modalityState)
    session.addAllFiles(*ManagingFS.getInstance().localRoots)
    refreshSessionId = session.id
    session.launch()
    LOG.debug("vfs refreshed")
  }

  override fun refreshOpenFiles() {
    val files = ArrayList<VirtualFile>()
    processOpenedProjects { project ->
      FileEditorManager.getInstance(project).selectedEditors
        .asSequence()
        .flatMap { it.filesToRefresh }
        .filterTo(files) { it is NewVirtualFile }
    }

    if (files.isNotEmpty()) {
      // refresh open files synchronously, so it doesn't wait for potentially longish refresh request in the queue to finish
      RefreshQueue.getInstance().refresh(false, false, null, files)
    }
  }

  override fun disableAutoSave(): AccessToken {
    blockSaveOnFrameDeactivation()
    return object : AccessToken() {
      override fun finish() {
        unblockSaveOnFrameDeactivation()
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
    blockSyncOnFrameActivationCount.incrementAndGet()
  }

  override fun unblockSyncOnFrameActivation() {
    blockSyncOnFrameActivationCount.decrementAndGet()
    LOG.debug("sync unblocked")
  }
}

private val saveAppAndProjectsSettingsTask = SaveAndSyncHandler.SaveTask()

@NlsContexts.ProgressTitle
private fun getProgressTitle(componentManager: ComponentManager): String {
  return if (componentManager is Application) CommonBundle.message("title.save.app") else CommonBundle.message("title.save.project")
}