// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.configurationStore.schemeManager.SchemeChangeEvent
import com.intellij.configurationStore.schemeManager.SchemeFileTracker
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.ex.VirtualFileManagerAdapter
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.SingleAlarm
import com.intellij.util.containers.MultiMap
import gnu.trove.THashSet
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

private val CHANGED_FILES_KEY = Key.create<MultiMap<ComponentStoreImpl, StateStorage>>("CHANGED_FILES_KEY")
private val CHANGED_SCHEMES_KEY = Key.create<MultiMap<SchemeFileTracker, SchemeChangeEvent>>("CHANGED_SCHEMES_KEY")

/**
 * Should be a separate service, not closely related to ProjectManager, but it requires some cleanup/investigation.
 */
class StoreAwareProjectManager(virtualFileManager: VirtualFileManager, progressManager: ProgressManager) : ProjectManagerImpl(progressManager) {
  private val reloadBlockCount = AtomicInteger()
  private val changedApplicationFiles = LinkedHashSet<StateStorage>()

  private val restartApplicationOrReloadProjectTask = Runnable {
    if (!isReloadUnblocked() || !tryToReloadApplication()) {
      return@Runnable
    }

    val projectsToReload = THashSet<Project>()
    for (project in openProjects) {
      if (project.isDisposed) {
        continue
      }

      val changedSchemes = project.getUserData(CHANGED_SCHEMES_KEY)
      if (changedSchemes != null) {
        CHANGED_SCHEMES_KEY.set(project, null)
      }

      val changedStorages = project.getUserData(CHANGED_FILES_KEY)
      if (changedStorages != null) {
        CHANGED_FILES_KEY.set(project, null)
      }

      if ((changedSchemes == null || changedSchemes.isEmpty) && (changedStorages == null || changedStorages.isEmpty)) {
        continue
      }

      runBatchUpdate(project.messageBus) {
        // reload schemes first because project file can refer to scheme (e.g. inspection profile)
        if (changedSchemes != null) {
          for ((tracker, files) in changedSchemes.entrySet()) {
            LOG.runAndLogException {
              tracker.reload(files)
            }
          }
        }

        if (changedStorages != null) {
          for ((store, storages) in changedStorages.entrySet()) {
            if ((store.storageManager as? StateStorageManagerImpl)?.componentManager?.isDisposed == true) {
              continue
            }

            @Suppress("UNCHECKED_CAST")
            if (reloadStore(storages as Set<StateStorage>, store) == ReloadComponentStoreStatus.RESTART_AGREED) {
              projectsToReload.add(project)
            }
          }
        }
      }
    }

    for (project in projectsToReload) {
      ProjectManagerImpl.doReloadProject(project)
    }
  }

  private val changedFilesAlarm = SingleAlarm(restartApplicationOrReloadProjectTask, 300, this)

  init {
    ApplicationManager.getApplication().messageBus.connect().subscribe(STORAGE_TOPIC, object : StorageManagerListener {
      override fun storageFileChanged(event: VFileEvent, storage: StateStorage, componentManager: ComponentManager) {
        if (event is VFilePropertyChangeEvent) {
          // ignore because doesn't affect content
          return
        }

        if (event.requestor is StateStorage.SaveSession || event.requestor is StateStorage || event.requestor is ProjectManagerImpl) {
          return
        }

        registerChangedStorage(storage, componentManager)
      }
    })

    virtualFileManager.addVirtualFileManagerListener(object : VirtualFileManagerAdapter() {
      override fun beforeRefreshStart(asynchronous: Boolean) {
        blockReloadingProjectOnExternalChanges()
      }

      override fun afterRefreshFinish(asynchronous: Boolean) {
        unblockReloadingProjectOnExternalChanges()
      }
    })
  }

  private fun isReloadUnblocked(): Boolean {
    val count = reloadBlockCount.get()
    LOG.debug { "[RELOAD] myReloadBlockCount = $count" }
    return count == 0
  }

  override fun saveChangedProjectFile(file: VirtualFile, project: Project) {
    val storageManager = (project.stateStore as ComponentStoreImpl).storageManager as? StateStorageManagerImpl ?: return
    storageManager.getCachedFileStorages(listOf(storageManager.collapseMacros(file.path))).firstOrNull()?.let {
      // if empty, so, storage is not yet loaded, so, we don't have to reload
      registerChangedStorage(it, project)
    }
  }

  override fun blockReloadingProjectOnExternalChanges() {
    reloadBlockCount.incrementAndGet()
  }

  override fun unblockReloadingProjectOnExternalChanges() {
    assert(reloadBlockCount.get() > 0)
    if (reloadBlockCount.decrementAndGet() == 0 && changedFilesAlarm.isEmpty) {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        // todo fix test to handle invokeLater
        changedFilesAlarm.request(true)
      }
      else {
        ApplicationManager.getApplication().invokeLater(restartApplicationOrReloadProjectTask, ModalityState.NON_MODAL)
      }
    }
  }

  override fun flushChangedProjectFileAlarm() {
    changedFilesAlarm.flush()
  }

  override fun reloadProject(project: Project) {
    CHANGED_FILES_KEY.set(project, null)
    super.reloadProject(project)
  }

  private fun registerChangedStorage(storage: StateStorage, componentManager: ComponentManager) {
    if (LOG.isDebugEnabled) {
      LOG.debug("[RELOAD] Registering project to reload: $storage", Exception())
    }

    val project: Project? = when (componentManager) {
      is Project -> componentManager
      is Module -> componentManager.project
      else -> null
    }

    if (project == null) {
      val changes = changedApplicationFiles
      synchronized (changes) {
        changes.add(storage)
      }
    }
    else {
      var changes = CHANGED_FILES_KEY.get(project)
      if (changes == null) {
        changes = MultiMap.createLinkedSet()
        CHANGED_FILES_KEY.set(project, changes)
      }

      synchronized (changes) {
        changes.putValue(componentManager.stateStore as ComponentStoreImpl, storage)
      }
    }

    if (storage is StateStorageBase<*>) {
      storage.disableSaving()
    }

    if (isReloadUnblocked()) {
      changedFilesAlarm.cancelAndRequest()
    }
  }

  internal fun registerChangedScheme(event: SchemeChangeEvent, schemeFileTracker: SchemeFileTracker, project: Project) {
    if (LOG.isDebugEnabled) {
      LOG.debug("[RELOAD] Registering scheme to reload: $event", Exception())
    }

    var changes = CHANGED_SCHEMES_KEY.get(project)
    if (changes == null) {
      changes = MultiMap.createLinkedSet()
      CHANGED_SCHEMES_KEY.set(project, changes)
    }

    synchronized(changes) {
      changes.putValue(schemeFileTracker, event)
    }

    if (isReloadUnblocked()) {
      changedFilesAlarm.cancelAndRequest()
    }
  }

  private fun tryToReloadApplication(): Boolean {
    if (ApplicationManager.getApplication().isDisposed) {
      return false
    }

    if (changedApplicationFiles.isEmpty()) {
      return true
    }

    val changes = LinkedHashSet<StateStorage>(changedApplicationFiles)
    changedApplicationFiles.clear()

    return reloadAppStore(changes)
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

fun reloadStore(changedStorages: Set<StateStorage>, store: ComponentStoreImpl): ReloadComponentStoreStatus {
  val notReloadableComponents: Collection<String>?
  var willBeReloaded = false
  try {
    try {
      notReloadableComponents = store.reload(changedStorages)
    }
    catch (e: Throwable) {
      LOG.warn(e)
      Messages.showWarningDialog(ProjectBundle.message("project.reload.failed", e.message), ProjectBundle.message("project.reload.failed.title"))
      return ReloadComponentStoreStatus.ERROR
    }

    if (notReloadableComponents == null || notReloadableComponents.isEmpty()) {
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
  val message = StringBuilder()
  val storeName = if (store is IProjectStore) "Project '${store.projectName}'" else "Application"
  message.append(storeName).append(' ')
  message.append("components were changed externally and cannot be reloaded:\n\n")
  var count = 0
  for (component in notReloadableComponents) {
    if (count == 10) {
      message.append('\n').append("and ").append(notReloadableComponents.size - count).append(" more").append('\n')
    }
    else {
      message.append(component).append('\n')
      count++
    }
  }

  message.append("\nWould you like to ")
  if (isApp) {
    message.append(if (ApplicationManager.getApplication().isRestartCapable) "restart" else "shutdown").append(' ')
    message.append(ApplicationNamesInfo.getInstance().productName).append('?')
  }
  else {
    message.append("reload project?")
  }

  if (Messages.showYesNoDialog(message.toString(), "$storeName Files Changed", Messages.getQuestionIcon()) == Messages.YES) {
    if (changedStorages != null) {
      for (storage in changedStorages) {
        if (storage is StateStorageBase<*>) {
          storage.disableSaving()
        }
      }
    }
    return true
  }
  return false
}

enum class ReloadComponentStoreStatus {
  RESTART_AGREED,
  RESTART_CANCELLED,
  ERROR,
  SUCCESS
}