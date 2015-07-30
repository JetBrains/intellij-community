/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.configurationStore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.components.StateStorage.SaveSession
import com.intellij.openapi.components.StateStorageChooserEx.Resolution
import com.intellij.openapi.components.impl.stores.*
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.util.PathUtilRt
import com.intellij.util.ReflectionUtil
import com.intellij.util.SmartList
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil
import gnu.trove.THashMap
import org.jdom.Element
import org.picocontainer.MutablePicoContainer
import org.picocontainer.PicoContainer
import java.io.File
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern
import kotlin.concurrent.withLock
import kotlin.reflect.jvm.java

/**
 * If componentManager not specified, storage will not add file tracker (see VirtualFileTracker)
 */
open class StateStorageManagerImpl(private val pathMacroSubstitutor: TrackingPathMacroSubstitutor,
                                   protected val rootTagName: String,
                                   private val picoContainer: PicoContainer,
                                   private val componentManager: ComponentManager? = null) : StateStorageManager {
  private val macros: MutableList<Macro> = ContainerUtil.createLockFreeCopyOnWriteList()
  private val storageLock = ReentrantLock()
  private val storages = THashMap<String, StateStorage>()

  private val filePathToStorage: ConcurrentMap<String, StateStorage> = ContainerUtil.newConcurrentMap()

  private var streamProvider: StreamProvider? = null

  private volatile var hasDirectoryBasedStorages = false

  // access under storageLock
  private var isUseVfsListener = if (componentManager == null) ThreeState.NO else ThreeState.UNSURE // unsure because depends on stream provider state

  protected open val isUseXmlProlog: Boolean
    get() = true

  companion object {
    private val MACRO_PATTERN = Pattern.compile("(\\$[^\\$]*\\$)")
  }

  override final fun getStreamProvider() = streamProvider

  override final fun setStreamProvider(value: StreamProvider?) {
    streamProvider = value
  }

  override final fun getMacroSubstitutor() = pathMacroSubstitutor

  private data class Macro(val key: String, var value: String)

  fun addMacro(key: String, expansion: String) {
    assert(!key.isEmpty())

    // you must not add duplicated macro, but our ModuleImpl.setModuleFilePath does it (it will be fixed later)
    for (macro in macros) {
      if (key.equals(macro.key)) {
        macro.value = expansion
        return
      }
    }

    macros.add(Macro(key, expansion))
  }

  override final fun getStateStorage(storageSpec: Storage) = getOrCreateStorage(storageSpec.file, storageSpec.roamingType, storageSpec.storageClass.java as Class<out StateStorage>, storageSpec.stateSplitter.java)

  override final fun getStateStorage(fileSpec: String, roamingType: RoamingType) = getOrCreateStorage(fileSpec, roamingType)

  fun getOrCreateStorage(fileSpec: String, roamingType: RoamingType, storageClass: Class<out StateStorage> = javaClass<StateStorage>(), SuppressWarnings("deprecation") stateSplitter: Class<out StateSplitter> = javaClass<StateSplitterEx>()): StateStorage {
    val key = if (storageClass == javaClass<StateStorage>()) fileSpec else storageClass.getName()
    storageLock.withLock {
      var stateStorage = storages.get(key)
      if (stateStorage == null) {
        stateStorage = createStateStorage(storageClass, fileSpec, roamingType, stateSplitter)
        storages.put(key, stateStorage)
      }
      return stateStorage
    }
  }

  override final fun getCachedFileStateStorages(changed: Collection<String>, deleted: Collection<String>) = storageLock.withLock { Couple.of(getCachedFileStorages(changed), getCachedFileStorages(deleted)) }

  fun getCachedFileStorages(fileSpecs: Collection<String>): Collection<FileBasedStorage> {
    if (fileSpecs.isEmpty()) {
      return emptyList()
    }

    var result: MutableList<FileBasedStorage>? = null
    for (fileSpec in fileSpecs) {
      val storage = storages.get(fileSpec)
      if (storage is FileBasedStorage) {
        if (result == null) {
          result = SmartList<FileBasedStorage>()
        }
        result.add(storage)
      }
    }
    return result ?: emptyList<FileBasedStorage>()
  }

  override final fun getStorageFileNames() = storageLock.withLock { storages.keySet() }

  // overridden in upsource
  protected open fun createStateStorage(storageClass: Class<out StateStorage>, fileSpec: String, roamingType: RoamingType, SuppressWarnings("deprecation") stateSplitter: Class<out StateSplitter>): StateStorage {
    if (storageClass != javaClass<StateStorage>()) {
      val key = UUID.randomUUID().toString()
      (picoContainer as MutablePicoContainer).registerComponentImplementation(key, storageClass)
      return picoContainer.getComponentInstance(key) as StateStorage
    }

    val filePath = expandMacros(fileSpec)
    val file = File(filePath)

    if (isUseVfsListener == ThreeState.UNSURE) {
      if (streamProvider != null && streamProvider!!.enabled) {
        isUseVfsListener = ThreeState.NO
      }
      else {
        isUseVfsListener = ThreeState.YES
        addVfsChangesListener(componentManager!!)
      }
    }

    //noinspection deprecation
    if (stateSplitter != javaClass<StateSplitter>() && stateSplitter != javaClass<StateSplitterEx>()) {
      val directoryBasedStorage = DirectoryBasedStorage(pathMacroSubstitutor, file, ReflectionUtil.newInstance(stateSplitter))
      hasDirectoryBasedStorages = true
      filePathToStorage.put(filePath.normalizePath(), directoryBasedStorage)
      return directoryBasedStorage
    }

    if (!ApplicationManager.getApplication().isHeadlessEnvironment() && PathUtilRt.getFileName(filePath).lastIndexOf('.') < 0) {
      throw IllegalArgumentException("Extension is missing for storage file: $filePath")
    }

    val effectiveRoamingType = if (roamingType == RoamingType.PER_USER && fileSpec == StoragePathMacros.WORKSPACE_FILE) RoamingType.DISABLED else roamingType
    val storage = object : FileBasedStorage(file, fileSpec, effectiveRoamingType, getMacroSubstitutor(fileSpec), rootTagName, streamProvider) {
      override fun createStorageData() = createStorageData(myFileSpec, getFilePath())

      override fun isUseXmlProlog() = isUseXmlProlog
    }
    filePathToStorage.put(filePath.normalizePath(), storage)
    return storage
  }

  private fun String.normalizePath(): String {
    val path = FileUtilRt.toSystemIndependentName(this)
    // fileSpec for directory based storage could be erroneously specified as "name/"
    return if (path.endsWith('/')) path.substring(0, path.length() - 1) else path
  }

  override final fun clearStateStorage(fileSpec: String) {
    storageLock.withLock {
      try {
        filePathToStorage.remove(expandMacros(fileSpec))
      }
      finally {
        storages.remove(fileSpec)
      }
    }
  }

  protected open fun getMacroSubstitutor(fileSpec: String): TrackingPathMacroSubstitutor? = pathMacroSubstitutor

  protected open fun createStorageData(fileSpec: String, filePath: String): StorageData = StorageData(rootTagName)

  override final fun expandMacros(file: String): String {
    // replacement can contains $ (php tests), so, this check must be performed before expand
    val matcher = MACRO_PATTERN.matcher(file)
    matcherLoop@
    while (matcher.find()) {
      val m = matcher.group(1)
      for (macro in macros) {
        if (macro.key == m) {
          continue@matcherLoop
        }
      }
      throw IllegalArgumentException("Unknown macro: $m in storage file spec: $file")
    }

    var expanded = file
    for ((key, value) in macros) {
      expanded = StringUtil.replace(expanded, key, value)
    }
    return expanded
  }

  override final fun collapseMacros(path: String): String {
    var result = path
    for ((key, value) in macros) {
      result = StringUtil.replace(result, value, key)
    }
    return result
  }

  override fun startExternalization() = StateStorageManagerExternalizationSession(this)

  open class StateStorageManagerExternalizationSession(protected val storageManager: StateStorageManagerImpl) : StateStorageManager.ExternalizationSession {
    private val mySessions = LinkedHashMap<StateStorage, StateStorage.ExternalizationSession>()

    override fun setState(storageSpecs: Array<Storage>, component: Any, componentName: String, state: Any) {
      val stateStorageChooser = component as? StateStorageChooserEx
      for (storageSpec in storageSpecs) {
        val resolution = if (stateStorageChooser == null) Resolution.DO else stateStorageChooser.getResolution(storageSpec, StateStorageOperation.WRITE)
        if (resolution == Resolution.SKIP) {
          continue
        }

        getExternalizationSession(storageManager.getStateStorage(storageSpec))?.setState(component, componentName, if (storageSpec.deprecated || resolution === Resolution.CLEAR) Element("empty") else state, storageSpec)
      }
    }

    override fun setStateInOldStorage(component: Any, componentName: String, state: Any) {
      val stateStorage = storageManager.getOldStorage(component, componentName, StateStorageOperation.WRITE)
      if (stateStorage != null) {
        getExternalizationSession(stateStorage)?.setState(component, componentName, state, null)
      }
    }

    protected fun getExternalizationSession(stateStorage: StateStorage): StateStorage.ExternalizationSession? {
      var session: StateStorage.ExternalizationSession? = mySessions.get(stateStorage)
      if (session == null) {
        session = stateStorage.startExternalization()
        if (session != null) {
          mySessions.put(stateStorage, session)
        }
      }
      return session
    }

    override fun createSaveSessions(): List<SaveSession> {
      if (mySessions.isEmpty()) {
        return emptyList()
      }

      var saveSessions: MutableList<SaveSession>? = null
      val externalizationSessions = mySessions.values()
      for (session in externalizationSessions) {
        val saveSession = session.createSaveSession()
        if (saveSession != null) {
          if (saveSessions == null) {
            if (externalizationSessions.size() == 1) {
              return listOf(saveSession)
            }
            saveSessions = SmartList<SaveSession>()
          }
          saveSessions.add(saveSession)
        }
      }
      return ContainerUtil.notNullize(saveSessions)
    }
  }

  override fun getOldStorage(component: Any, componentName: String, operation: StateStorageOperation): StateStorage? {
    val oldStorageSpec = getOldStorageSpec(component, componentName, operation)
    @suppress("DEPRECATED_SYMBOL_WITH_MESSAGE")
    return if (oldStorageSpec == null) null else getStateStorage(oldStorageSpec, if (component is com.intellij.openapi.util.RoamingTypeDisabled) RoamingType.DISABLED else RoamingType.PER_USER)
  }

  protected open fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String? = null

  private fun addVfsChangesListener(componentManager: ComponentManager) {
    componentManager.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener.Adapter() {
      override fun after(events: MutableList<out VFileEvent>) {
        for (event in events) {
          var storage: StateStorage?
          if (event is VFilePropertyChangeEvent && VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
            val oldPath = event.getOldPath()
            storage = filePathToStorage.remove(oldPath)
            if (storage != null) {
              filePathToStorage.put(event.getPath(), storage)
              if (storage is FileBasedStorage) {
                storage.setFile(null, File(event.getPath()))
              }
              // we don't support DirectoryBasedStorage renaming
            }

            // StoragePathMacros.MODULE_FILE -> old path, we must update value
            for (macro in macros) {
              if (oldPath.equals(macro.value)) {
                macro.value = event.getPath()
              }
            }
          }
          else {
            val path = event.getPath()
            storage = filePathToStorage.get(path)
            // we don't care about parent directory create (because it doesn't affect anything) and move (because it is not supported case),
            // but we should detect deletion - but again, it is not supported case. So, we don't check if some of registered storages located inside changed directory.

            // but if we have DirectoryBasedStorage, we check - if file located inside it
            if (storage == null && hasDirectoryBasedStorages && StringUtilRt.endsWithIgnoreCase(path, DirectoryStorageData.DEFAULT_EXT)) {
              storage = filePathToStorage.get(VfsUtil.getParentDir(path))
            }
          }

          if (storage != null) {
            when (event) {
              is VFileContentChangeEvent -> {
                storageFileChanged(event, storage)
              }
              is VFileMoveEvent -> {
                if (storage is FileBasedStorage) {
                  storage.setFile(null, File(event.getPath()))
                }
              }
              is VFileCreateEvent -> {
                if (storage is FileBasedStorage) {
                  storage.setFile(event.getFile(), null)
                }
                storageFileChanged(event, storage)
              }
              is VFileDeleteEvent -> {
                if (storage is FileBasedStorage) {
                  storage.setFile(null, null)
                }
                else {
                  (storage as DirectoryBasedStorage).setVirtualDir(null)
                }
                storageFileChanged(event, storage)
              }
              is VFilePropertyChangeEvent -> {
                storageFileChanged(event, storage)
              }
            }
          }
        }
      }

      private fun storageFileChanged(event: VFileEvent, storage: StateStorage) {
        componentManager.getMessageBus().syncPublisher(StateStorageManager.STORAGE_TOPIC).storageFileChanged(event, storage, componentManager)
      }
    })
  }
}
