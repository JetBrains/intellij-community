// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.configurationStore.schemeManager.SchemeChangeApplicator
import com.intellij.configurationStore.schemeManager.SchemeChangeEvent
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.options.Scheme
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectReloadState
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManagerListener
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.AppUIUtil
import com.intellij.util.ExceptionUtil
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
internal class StoreReloadManagerImpl(private val project: Project, coroutineScope: CoroutineScope) : StoreReloadManager {
  private val reloadBlockCount = AtomicInteger()
  private val blockStackTrace = AtomicReference<Throwable?>()
  private val changedStorages = LinkedHashMap<ComponentStoreImpl, MutableSet<StateStorage>>()
  private val changedSchemes = LinkedHashMap<SchemeChangeApplicator<*,*>, MutableSet<SchemeChangeEvent<*,*>>>()

  private val changedFilesRequests = MutableSharedFlow<Unit>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    @OptIn(FlowPreview::class)
    coroutineScope.launch(CoroutineName("configuration store reload request flow processing")) {
      changedFilesRequests
        .debounce(300.milliseconds)
        .collect {
          doReload()
        }
    }

    project.messageBus.simpleConnect().subscribe(VirtualFileManagerListener.TOPIC, object : VirtualFileManagerListener {
      override fun beforeRefreshStart(asynchronous: Boolean) {
        blockReloadingProjectOnExternalChanges()
      }

      override fun afterRefreshFinish(asynchronous: Boolean) {
        unblockReloadingProjectOnExternalChanges()
      }
    })
  }

  private suspend fun doReload() {
    if (isReloadBlocked()) {
      return
    }

    val projectsToReload = LinkedHashSet<Project>()
    withContext(Dispatchers.EDT) {
      LOG.debug("Dispatch to EDT")
      applyProjectChanges(projectsToReload)

      if (projectsToReload.isNotEmpty()) {
        for (project in projectsToReload) {
          doReloadProject(project)
        }
      }
    }
  }

  @RequiresEdt
  private suspend fun applyProjectChanges(projectsToReload: LinkedHashSet<Project>) {
    if (changedSchemes.isEmpty() && changedStorages.isEmpty()
        && !JpsProjectModelSynchronizer.getInstance(project).needToReloadProjectEntities()) {
      return
    }

    val changedSchemesCopy: LinkedHashMap<SchemeChangeApplicator<*, *>, MutableSet<SchemeChangeEvent<*, *>>>
    synchronized(changedSchemes) {
      changedSchemesCopy = LinkedHashMap(changedSchemes)
      changedSchemes.clear()
    }

    val changedStoragesCopy: LinkedHashMap<ComponentStoreImpl, MutableSet<StateStorage>>
    synchronized(changedStorages) {
      changedStoragesCopy = LinkedHashMap(changedStorages)
      changedStorages.clear()
    }

    if (changedSchemesCopy.isEmpty() && changedStoragesCopy.isEmpty()
        && !JpsProjectModelSynchronizer.getInstance(project).needToReloadProjectEntities()) {
      return
    }

    val publisher = project.messageBus.syncPublisher(BatchUpdateListener.TOPIC)
    publisher.onBatchUpdateStarted()
    try {
      // reload schemes first because project file can refer to scheme (e.g. inspection profile)
      for ((tracker, files) in changedSchemesCopy) {
        runCatching {
          SlowOperations.knownIssue("IDEA-307617, EA-680581").use {
            writeIntentReadAction {
              @Suppress("UNCHECKED_CAST")
              (tracker as SchemeChangeApplicator<Scheme, Scheme>).reload(files as Set<SchemeChangeEvent<Scheme, Scheme>>)
            }
          }
        }.getOrLogException(LOG)
      }

      for ((store, storages) in changedStoragesCopy) {
        if ((store.storageManager as? StateStorageManagerImpl)?.componentManager?.isDisposed == true) {
          continue
        }

        if (reloadStore(storages, store) == ReloadComponentStoreStatus.RESTART_AGREED) {
          projectsToReload.add(project)
        }
      }
    }
    finally {
      publisher.onBatchUpdateFinished()
    }

    withContext(Dispatchers.IO) {
      withBackgroundProgress(project, ConfigurationStoreBundle.message("progress.title.reloading.project.configuration")) {
        JpsProjectModelSynchronizer.getInstance(project).reloadProjectEntities()
      }
    }
  }

  override fun isReloadBlocked(): Boolean {
    val count = reloadBlockCount.get()
    LOG.debug { "[RELOAD] reloadBlockCount = $count" }
    return count > 0
  }

  override fun saveChangedProjectFile(file: VirtualFile) {
    val store = project.stateStore as ComponentStoreImpl
    val storageManager = store.storageManager as? StateStorageManagerImpl ?: return
    storageManager.getCachedFileStorages(listOf(storageManager.collapseMacro(file.path))).firstOrNull()?.let {
      // if empty, so, storage is not yet loaded, so, we don't have to reload
      storageFilesChanged(store, listOf(it))
    }
  }

  override fun blockReloadingProjectOnExternalChanges() {
    if (reloadBlockCount.getAndIncrement() == 0 && !ApplicationManagerEx.isInStressTest()) {
      blockStackTrace.set(Throwable())
    }
  }

  override fun unblockReloadingProjectOnExternalChanges() {
    val counter = reloadBlockCount.get()
    if (counter <= 0) {
      LOG.error("Block counter $counter must be > 0, first block stack trace: ${blockStackTrace.get()?.let { ExceptionUtil.getThrowableText(it) }}")
    }

    if (reloadBlockCount.decrementAndGet() != 0) {
      return
    }

    blockStackTrace.set(null)
    check(changedFilesRequests.tryEmit(Unit))
  }

  /**
   * Internal use only. Force reload changed project files.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  override suspend fun reloadChangedStorageFiles() {
    changedFilesRequests.resetReplayCache()
    doReload()
  }

  override fun reloadProject() {
    synchronized(changedStorages) {
      changedStorages.clear()
    }
    doReloadProject(project)
  }

  override fun storageFilesChanged(store: IComponentStore, storages: Collection<StateStorage>) {
    if (LOG.isDebugEnabled) {
      LOG.debug("[RELOAD] registering to reload: ${storages.joinToString("\n")}", Exception())
    }

    synchronized(changedStorages) {
      changedStorages.computeIfAbsent(store as ComponentStoreImpl) { LinkedHashSet() }.addAll(storages)
    }

    for (storage in storages) {
      if (storage is StateStorageBase<*>) {
        storage.disableSaving()
      }
    }

    scheduleProcessingChangedFiles()
  }

  override fun storageFilesBatchProcessing(batchStorageEvents: Map<IComponentStore, Collection<StateStorage>>) {
    if (LOG.isDebugEnabled) {
      LOG.debug("[RELOAD] registering to reload: ${batchStorageEvents.entries.joinToString("\n")}", Exception())
    }

    for ((store, storages) in batchStorageEvents) {
      synchronized(changedStorages) {
        changedStorages.computeIfAbsent(store as ComponentStoreImpl) { LinkedHashSet() }.addAll(storages)
      }

      for (storage in storages) {
        if (storage is StateStorageBase<*>) {
          storage.disableSaving()
        }
      }
    }

    scheduleProcessingChangedFiles()
  }

  internal fun <T : Scheme, M : T> registerChangedSchemes(events: List<SchemeChangeEvent<T, M>>, schemeFileTracker: SchemeChangeApplicator<T, M>) {
    if (LOG.isDebugEnabled) {
      LOG.debug("[RELOAD] Registering schemes to reload: $events", Exception())
    }

    synchronized(changedSchemes) {
      changedSchemes.computeIfAbsent(schemeFileTracker) { LinkedHashSet() }.addAll(events)
    }

    scheduleProcessingChangedFiles()
  }

  override fun scheduleProcessingChangedFiles() {
    if (!isReloadBlocked()) {
      check(changedFilesRequests.tryEmit(Unit))
    }
  }
}

@ApiStatus.Internal
fun reloadAppStore(changes: Set<StateStorage>): Boolean {
  val status = reloadStore(changes, ApplicationManager.getApplication().stateStore as ComponentStoreImpl)
  if (status == ReloadComponentStoreStatus.RESTART_AGREED) {
    ApplicationManagerEx.getApplicationEx().restart(true)
    return false
  }
  else {
    return status == ReloadComponentStoreStatus.SUCCESS || status == ReloadComponentStoreStatus.RESTART_CANCELLED
  }
}

internal fun reloadStore(changedStorages: Set<StateStorage>, store: ComponentStoreImpl): ReloadComponentStoreStatus {
  val notReloadableComponents: Collection<String>?
  var willBeReloaded = false
  try {
    try {
      notReloadableComponents = store.reload(changedStorages)
    }
    catch (e: Throwable) {
      LOG.warn(e)
      AppUIUtil.invokeOnEdt {
        Messages.showWarningDialog(ConfigurationStoreBundle.message("project.reload.failed", e.message), ConfigurationStoreBundle.message("project.reload.failed.title"))
      }
      return ReloadComponentStoreStatus.ERROR
    }

    if (notReloadableComponents.isNullOrEmpty()) {
      return ReloadComponentStoreStatus.SUCCESS
    }

    willBeReloaded = askToRestart(store, notReloadableComponents, changedStorages, store.project == null)
    return if (willBeReloaded) ReloadComponentStoreStatus.RESTART_AGREED else ReloadComponentStoreStatus.RESTART_CANCELLED
  }
  finally {
    if (!willBeReloaded) {
      for (storage in changedStorages) {
        if (storage is StateStorageBase<*>) {
          storage.enableSaving()
        }
      }
    }
  }
}

// used in settings repository plugin
@ApiStatus.Internal
fun askToRestart(store: IComponentStore, notReloadableComponents: Collection<String>, changedStorages: Set<StateStorage>?, isApp: Boolean): Boolean {
  val firstMessage = if (store is IProjectStore) {
    ConfigurationStoreBundle.message("configuration.project.files.changed.message.start", store.projectName)
  }
  else {
    ConfigurationStoreBundle.message("configuration.application.files.changed.message.start")
  }

  val nonReloadableComponentsJoined = notReloadableComponents.take(10).joinToString("\n").let {
    if (notReloadableComponents.size > 10) {
      ConfigurationStoreBundle.message("configuration.project.components.changed.and.several.more", it, notReloadableComponents.size - 10)
    }
    else {
      it
    }
  }

  val question = if (isApp) {
    val productName = ApplicationNamesInfo.getInstance().productName
    if (ApplicationManager.getApplication().isRestartCapable) {
      ConfigurationStoreBundle.message("configuration.project.files.changed.restart.proposal", productName)
    }
    else {
      ConfigurationStoreBundle.message("configuration.project.files.changed.shutdown.proposal", productName)
    }
  }
  else {
    ConfigurationStoreBundle.message("configuration.project.files.changed.reload.project.proposal")
  }

  @Suppress("HardCodedStringLiteral")
  val message = """
    $firstMessage

    $nonReloadableComponentsJoined
    $question
  """.trimIndent()

  val title = if (store is IProjectStore)
    ConfigurationStoreBundle.message("configuration.project.files.changed.restart.prompt.title", store.projectName)
    else ConfigurationStoreBundle.message("configuration.application.files.changed.restart.prompt.title")

  if (Messages.showYesNoDialog(message, title, Messages.getQuestionIcon()) != Messages.YES) {
    return false
  }

  if (changedStorages != null) {
    for (storage in changedStorages) {
      if (storage is StateStorageBase<*>) {
        storage.disableSaving()
      }
    }
  }
  return true
}

internal enum class ReloadComponentStoreStatus {
  RESTART_AGREED,
  RESTART_CANCELLED,
  ERROR,
  SUCCESS
}

private fun doReloadProject(project: Project) {
  ProjectReloadState.getInstance(project).onBeforeAutomaticProjectReload()
  ApplicationManager.getApplication().invokeLater({
    LOG.debug("Reloading project")

    // must compute here, before dispose of the project
    val presentableUrl = project.presentableUrl!!
    if (!ProjectManager.getInstance().closeAndDispose(project)) {
      return@invokeLater
    }

    ProjectManagerEx.getInstanceEx().openProject(Path.of(presentableUrl), OpenProjectTask())
  }, ModalityState.nonModal(), project.disposed)
}