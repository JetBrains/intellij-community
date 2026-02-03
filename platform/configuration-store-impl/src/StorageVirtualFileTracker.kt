// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.configurationStore

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.ComponentStorageUtil
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.service
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.*
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.APP)
internal class StorageVirtualFileTracker {
  private val filePathToStorage = ConcurrentHashMap<String, TrackedStorage>()

  @Volatile
  private var hasDirectoryBasedStorages = false

  interface TrackedStorage : StateStorage {
    val storageManager: StateStorageManagerImpl
  }

  fun put(path: String, storage: TrackedStorage) {
    filePathToStorage.put(path, storage)
    if (storage is DirectoryBasedStorage) {
      hasDirectoryBasedStorages = true
    }
  }

  fun remove(path: String) {
    filePathToStorage.remove(path)
  }

  fun remove(filter: (TrackedStorage) -> Boolean) {
    filePathToStorage.values.removeIf(filter)
  }

  fun schedule(events: List<VFileEvent>) {
    var projectToChanges: MutableMap<Project, MutableMap<IComponentStore, MutableSet<StateStorage>>>? = null
    eventLoop@ for (event in events) {
      var storage: StateStorage?
      if (event is VFilePropertyChangeEvent && VirtualFile.PROP_NAME == event.propertyName) {
        val oldPath = event.oldPath
        storage = filePathToStorage.remove(oldPath)
        if (storage != null) {
          val newPath = event.newPath
          val newFile = Path.of(newPath)
          filePathToStorage.put(newPath, storage)
          if (storage is FileBasedStorage) {
            storage.setFile(virtualFile = null, ioFileIfChanged = newFile)
          }
          // we don't support DirectoryBasedStorage renaming

          // StoragePathMacros.MODULE_FILE -> old path, we must update value
          (storage.storageManager as? RenameableStateStorageManager)?.pathRenamed(newFile, event)
        }
      }
      else {
        val path = event.path
        storage = filePathToStorage.get(path)
        // We don't care about parent directory creation (because it doesn't affect anything) and move (because it is not a supported case),
        // but we should detect deletion (though again, it is not a supported case).
        // So, we don't check if some of the registered storages located inside changed directory,
        // but if we have `DirectoryBasedStorage`, we check if a file is located inside it.
        if (storage == null && hasDirectoryBasedStorages && path.endsWith(ComponentStorageUtil.DEFAULT_EXT, ignoreCase = true)) {
          storage = filePathToStorage.get(VfsUtil.getParentDir(path))
        }
      }

      if (storage == null) {
        continue
      }

      when (event) {
        is VFileMoveEvent -> {
          if (storage is FileBasedStorage) {
            storage.setFile(null, Path.of(event.newPath))
          }
        }
        is VFileCreateEvent -> {
          if (storage is FileBasedStorage && event.requestor !is SaveSession) {
            storage.setFile(event.file, null)
          }
        }
        is VFileDeleteEvent -> {
          if (storage is FileBasedStorage) {
            storage.setFile(null, null)
          }
          else {
            (storage as DirectoryBasedStorage).setVirtualDir(null)
          }
        }
        is VFileCopyEvent -> continue@eventLoop
      }

      if (isFireStorageFileChangedEvent(event)) {
        val componentManager = storage.storageManager.componentManager!!
        if (projectToChanges == null) {
          projectToChanges = LinkedHashMap()
        }

        val project: Project = when (componentManager) {
          is Project -> componentManager
          is Module -> componentManager.project
          else -> continue
        }

        projectToChanges
          .computeIfAbsent(project) { LinkedHashMap() }
          .computeIfAbsent(componentManager.stateStore) { LinkedHashSet() }
          .add(storage)
      }
    }

    if (projectToChanges == null) {
      return
    }

    for ((project, batchStorageEvents) in projectToChanges) {
      StoreReloadManager.getInstance(project).storageFilesBatchProcessing(batchStorageEvents)
    }
  }
}

internal class StorageVfsListener : AsyncFileListener {
  override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
    service<StorageVirtualFileTracker>().schedule(events)
    return null
  }
}
