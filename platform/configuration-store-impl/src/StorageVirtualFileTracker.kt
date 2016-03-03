package com.intellij.configurationStore

import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.components.impl.stores.StateStorageManager
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.messages.MessageBus
import java.io.File
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicBoolean

class StorageVirtualFileTracker(private val messageBus: MessageBus) {
  private val filePathToStorage: ConcurrentMap<String, TrackedStorage> = ContainerUtil.newConcurrentMap()
  private @Volatile var hasDirectoryBasedStorages = false

  private val vfsListenerAdded = AtomicBoolean()

  interface TrackedStorage : StateStorage {
    val storageManager: StateStorageManagerImpl
  }

  fun put(path: String, storage: TrackedStorage) {
    filePathToStorage.put(path, storage)
    if (storage is DirectoryBasedStorage) {
      hasDirectoryBasedStorages = true
    }

    if (vfsListenerAdded.compareAndSet(false, true)) {
      addVfsChangesListener()
    }
  }

  fun remove(path: String) {
    filePathToStorage.remove(path)
  }

  fun remove(processor: (TrackedStorage) -> Boolean) {
    val iterator = filePathToStorage.values.iterator()
    for (storage in iterator) {
      if (processor(storage)) {
        iterator.remove()
      }
    }
  }

  private fun addVfsChangesListener() {
    messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener.Adapter() {
      override fun after(events: MutableList<out VFileEvent>) {
        eventLoop@
        for (event in events) {
          var storage: StateStorage?
          if (event is VFilePropertyChangeEvent && VirtualFile.PROP_NAME.equals(event.propertyName)) {
            val oldPath = event.oldPath
            storage = filePathToStorage.remove(oldPath)
            if (storage != null) {
              filePathToStorage.put(event.path, storage)
              if (storage is FileBasedStorage) {
                storage.setFile(null, File(event.path))
              }
              // we don't support DirectoryBasedStorage renaming

              // StoragePathMacros.MODULE_FILE -> old path, we must update value
              storage.storageManager.pathRenamed(oldPath, event.path, event)
            }
          }
          else {
            val path = event.path
            storage = filePathToStorage[path]
            // we don't care about parent directory create (because it doesn't affect anything) and move (because it is not supported case),
            // but we should detect deletion - but again, it is not supported case. So, we don't check if some of registered storages located inside changed directory.

            // but if we have DirectoryBasedStorage, we check - if file located inside it
            if (storage == null && hasDirectoryBasedStorages && StringUtilRt.endsWithIgnoreCase(path, FileStorageCoreUtil.DEFAULT_EXT)) {
              storage = filePathToStorage[VfsUtil.getParentDir(path)]
            }
          }

          if (storage == null) {
            continue
          }

          when (event) {
            is VFileMoveEvent -> {
              if (storage is FileBasedStorage) {
                storage.setFile(null, File(event.path))
              }
            }
            is VFileCreateEvent -> {
              if (storage is FileBasedStorage) {
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

          val componentManager = storage.storageManager.componentManager!!
          componentManager.messageBus.syncPublisher(StateStorageManager.STORAGE_TOPIC).storageFileChanged(event, storage, componentManager)
        }
      }
    })
  }
}