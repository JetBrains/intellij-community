// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.configurationStore.schemeManager.SchemeChangeApplicator
import com.intellij.configurationStore.schemeManager.SchemeChangeEvent
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.Scheme
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectReloadState
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManagerListener
import com.intellij.ui.AppUIUtil
import com.intellij.util.ExceptionUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

private val CHANGED_FILES_KEY = Key<MutableMap<ComponentStoreImpl, MutableSet<StateStorage>>>("CHANGED_FILES_KEY")
private val CHANGED_SCHEMES_KEY = Key<MutableMap<SchemeChangeApplicator<*,*>, MutableSet<SchemeChangeEvent<*,*>>>>("CHANGED_SCHEMES_KEY")

@ApiStatus.Internal
internal class StoreReloadManagerImpl : StoreReloadManager, Disposable {
  private val reloadBlockCount = AtomicInteger()
  private val blockStackTrace = AtomicReference<Throwable?>()
  private val changedApplicationFiles = LinkedHashSet<StateStorage>()

  private val changedFilesRequests = MutableSharedFlow<Unit>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  @Suppress("DEPRECATION")
  @OptIn(FlowPreview::class)
  private val job = ApplicationManager.getApplication().coroutineScope
    .launch(CoroutineName("configuration store reload request flow processing")) {
      changedFilesRequests
        .debounce(300.milliseconds)
        .collect {
          doReload()
        }
    }

  private suspend fun doReload() {
    if (isReloadBlocked() || !tryToReloadApplication()) {
      return
    }

    val projectsToReload = LinkedHashSet<Project>()
    withContext(Dispatchers.EDT) {
      for (project in (ProjectManager.getInstanceIfCreated()?.openProjects ?: return@withContext)) {
        if (project.isDisposed || !project.isInitialized) {
          continue
        }

        applyProjectChanges(project, projectsToReload)
      }

      if (projectsToReload.isNotEmpty()) {
        for (project in projectsToReload) {
          doReloadProject(project)
        }
      }
    }
  }

  @RequiresEdt
  private suspend fun applyProjectChanges(project: Project, projectsToReload: LinkedHashSet<Project>) {
    val changedSchemes = CHANGED_SCHEMES_KEY.getAndClear(project as UserDataHolderEx)
    val changedStorages = CHANGED_FILES_KEY.getAndClear(project as UserDataHolderEx)
    if ((changedSchemes.isNullOrEmpty()) && (changedStorages.isNullOrEmpty())
        && !mayHaveAdditionalConfigurations(project)) {
      return
    }

    val publisher = project.messageBus.syncPublisher(BatchUpdateListener.TOPIC)
    publisher.onBatchUpdateStarted()
    try {
      // reload schemes first because project file can refer to scheme (e.g. inspection profile)
      if (changedSchemes != null) {
        for ((tracker, files) in changedSchemes) {
          runCatching {
            @Suppress("UNCHECKED_CAST")
            (tracker as SchemeChangeApplicator<Scheme, Scheme>).reload(files as Set<SchemeChangeEvent<Scheme, Scheme>>)
          }.getOrLogException(LOG)
        }
      }

      if (changedStorages != null) {
        for ((store, storages) in changedStorages) {
          if ((store.storageManager as? StateStorageManagerImpl)?.componentManager?.isDisposed == true) {
            continue
          }

          if (reloadStore(storages, store) == ReloadComponentStoreStatus.RESTART_AGREED) {
            projectsToReload.add(project)
          }
        }
      }

      JpsProjectModelSynchronizer.getInstance(project).reloadProjectEntities()
    }
    finally {
      publisher.onBatchUpdateFinished()
    }
  }

  private fun mayHaveAdditionalConfigurations(project: Project): Boolean {
    return JpsProjectModelSynchronizer.getInstance(project).needToReloadProjectEntities()
  }

  internal class MyVirtualFileManagerListener : VirtualFileManagerListener {
    private val manager = StoreReloadManager.getInstance()

    override fun beforeRefreshStart(asynchronous: Boolean) {
      manager.blockReloadingProjectOnExternalChanges()
    }

    override fun afterRefreshFinish(asynchronous: Boolean) {
      manager.unblockReloadingProjectOnExternalChanges()
    }
  }

  override fun isReloadBlocked(): Boolean {
    val count = reloadBlockCount.get()
    LOG.debug { "[RELOAD] myReloadBlockCount = $count" }
    return count > 0
  }

  override fun saveChangedProjectFile(file: VirtualFile, project: Project) {
    val storageManager = (project.stateStore as ComponentStoreImpl).storageManager as? StateStorageManagerImpl ?: return
    storageManager.getCachedFileStorages(listOf(storageManager.collapseMacro(file.path))).firstOrNull()?.let {
      // if empty, so, storage is not yet loaded, so, we don't have to reload
      storageFilesChanged(mapOf(project to listOf(it)))
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

  override fun reloadProject(project: Project) {
    CHANGED_FILES_KEY.set(project, null)
    doReloadProject(project)
  }

  override fun storageFilesChanged(componentManagerToStorages: Map<ComponentManager, Collection<StateStorage>>) {
    if (componentManagerToStorages.isEmpty()) {
      return
    }

    if (LOG.isDebugEnabled) {
      LOG.debug("[RELOAD] registering to reload: ${componentManagerToStorages.map { "${it.key}: ${it.value.joinToString()}" }.joinToString("\n")}", Exception())
    }

    for ((componentManager, storages) in componentManagerToStorages) {
      val project: Project? = when (componentManager) {
        is Project -> componentManager
        is Module -> componentManager.project
        else -> null
      }

      if (project == null) {
        val changes = changedApplicationFiles
        synchronized(changes) {
          changes.addAll(storages)
        }
      }
      else {
        val changes = CHANGED_FILES_KEY.get(project) ?: (project as UserDataHolderEx).putUserDataIfAbsent(CHANGED_FILES_KEY, linkedMapOf())
        synchronized(changes) {
          changes.computeIfAbsent(componentManager.stateStore as ComponentStoreImpl) { LinkedHashSet() }.addAll(storages)
        }
      }

      for (storage in storages) {
        if (storage is StateStorageBase<*>) {
          storage.disableSaving()
        }
      }
    }

    scheduleProcessingChangedFiles()
  }

  internal fun <T : Scheme, M:T>registerChangedSchemes(events: List<SchemeChangeEvent<T,M>>, schemeFileTracker: SchemeChangeApplicator<T,M>, project: Project) {
    if (LOG.isDebugEnabled) {
      LOG.debug("[RELOAD] Registering schemes to reload: $events", Exception())
    }

    val changes = CHANGED_SCHEMES_KEY.get(project) ?: (project as UserDataHolderEx).putUserDataIfAbsent(CHANGED_SCHEMES_KEY, linkedMapOf())
    synchronized(changes) {
      changes.computeIfAbsent(schemeFileTracker) { LinkedHashSet() }.addAll(events)
    }

    scheduleProcessingChangedFiles()
  }

  override fun scheduleProcessingChangedFiles() {
    if (!isReloadBlocked()) {
      check(changedFilesRequests.tryEmit(Unit))
    }
  }

  private fun tryToReloadApplication(): Boolean {
    if (ApplicationManager.getApplication().isDisposed) {
      return false
    }

    if (changedApplicationFiles.isEmpty()) {
      return true
    }

    val changes = LinkedHashSet(changedApplicationFiles)
    changedApplicationFiles.clear()

    return reloadAppStore(changes)
  }

  override fun dispose() {
    job.cancel()
  }
}

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

private fun <T : Any> Key<T>.getAndClear(holder: UserDataHolderEx): T? {
  val value = holder.getUserData(this) ?: return null
  holder.replace(this, value, null)
  return value
}

private fun doReloadProject(project: Project) {
  val projectRef = Ref.create(project)
  ProjectReloadState.getInstance(project).onBeforeAutomaticProjectReload()
  AppUIExecutor.onWriteThread(ModalityState.NON_MODAL).later().submit {
    LOG.debug("Reloading project.")
    val project1 = projectRef.get()
    // Let it go
    projectRef.set(null)

    if (project1.isDisposed) {
      return@submit
    }

    // must compute here, before project dispose
    val presentableUrl = project1.presentableUrl!!
    if (!ProjectManager.getInstance().closeAndDispose(project1)) {
      return@submit
    }

    ProjectManagerEx.getInstanceEx().openProject(Paths.get(presentableUrl), OpenProjectTask())
  }
}