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

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.components.StateStorage.SaveSession
import com.intellij.openapi.components.StateStorageChooserEx.Resolution
import com.intellij.openapi.components.impl.stores.DirectoryBasedStorage
import com.intellij.openapi.components.impl.stores.FileStorage
import com.intellij.openapi.components.impl.stores.StateStorageManager
import com.intellij.openapi.components.impl.stores.StreamProvider
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.PathUtilRt
import com.intellij.util.ReflectionUtil
import com.intellij.util.SmartList
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil
import gnu.trove.THashMap
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern
import kotlin.concurrent.withLock
import kotlin.reflect.jvm.java

/**
 * If componentManager not specified, storage will not add file tracker (see VirtualFileTracker)
 */
open class StateStorageManagerImpl(private val rootTagName: String,
                                   private val pathMacroSubstitutor: TrackingPathMacroSubstitutor? = null,
                                   val componentManager: ComponentManager? = null,
                                   private val virtualFileTracker: StorageVirtualFileTracker? = StateStorageManagerImpl.createDefaultVirtualTracker(componentManager) ) : StateStorageManager {
  private val macros: MutableList<Macro> = ContainerUtil.createLockFreeCopyOnWriteList()
  private val storageLock = ReentrantLock()
  private val storages = THashMap<String, StateStorage>()

  private var streamProvider: StreamProvider? = null

  // access under storageLock
  private var isUseVfsListener = if (componentManager == null) ThreeState.NO else ThreeState.UNSURE // unsure because depends on stream provider state

  protected open val isUseXmlProlog: Boolean
    get() = true

  companion object {
    private val MACRO_PATTERN = Pattern.compile("(\\$[^\\$]*\\$)")

    fun createDefaultVirtualTracker(componentManager: ComponentManager?) = when (componentManager) {
      null -> {
        null
      }
      is Application -> {
        StorageVirtualFileTracker(componentManager.getMessageBus())
      }
      else -> {
        val tracker = (ApplicationManager.getApplication().stateStore.getStateStorageManager() as StateStorageManagerImpl).virtualFileTracker
        if (tracker != null) {
          Disposer.register(componentManager, Disposable {
            tracker.remove { it.storageManager.componentManager == componentManager }
          })
        }
        tracker
      }
    }
  }

  override final fun getStreamProvider() = streamProvider

  override final fun setStreamProvider(value: StreamProvider?) {
    streamProvider = value
  }

  override final fun getMacroSubstitutor() = pathMacroSubstitutor

  private data class Macro(val key: String, var value: String)

  TestOnly fun getVirtualFileTracker() = virtualFileTracker

  /**
   * @param expansion System-independent.
   */
  fun addMacro(key: String, expansion: String) {
    assert(!key.isEmpty())

    val value: String
    if (expansion.contains("\\")) {
      val message = "Macro $key set to system-dependent expansion $expansion"
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw IllegalArgumentException(message)
      }
      else {
        LOG.warn(message)
        value = FileUtilRt.toSystemIndependentName(expansion)
      }
    }
    else {
      value = expansion
    }

    // you must not add duplicated macro, but our ModuleImpl.setModuleFilePath does it (it will be fixed later)
    for (macro in macros) {
      if (key.equals(macro.key)) {
        macro.value = value
        return
      }
    }

    macros.add(Macro(key, value))
  }

  // system-independent paths
  open fun pathRenamed(oldPath: String, newPath: String, event: VFileEvent?) {
    for (macro in macros) {
      if (oldPath.equals(macro.value)) {
        macro.value = newPath
      }
    }
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

  override final fun getCachedFileStateStorages(changed: MutableCollection<String>,
                                                deleted: MutableCollection<String>): Couple<MutableCollection<FileStorage>> {
    val result = storageLock.withLock { Couple.of(getCachedFileStorages(changed), getCachedFileStorages(deleted)) }
    return Couple.of(result.first.toMutableSet(), result.second.toMutableSet())
  }

  fun getCachedFileStorages(fileSpecs: Collection<String>): Collection<FileStorage> {
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

  // overridden in upsource
  protected open fun createStateStorage(storageClass: Class<out StateStorage>, fileSpec: String, roamingType: RoamingType, SuppressWarnings("deprecation") stateSplitter: Class<out StateSplitter>): StateStorage {
    if (storageClass != javaClass<StateStorage>()) {
      val constructor = storageClass.getConstructors()[0]!!
      constructor.setAccessible(true)
      return constructor.newInstance(componentManager!!, this) as StateStorage
    }

    val filePath = expandMacros(fileSpec)
    val file = File(filePath)

    if (isUseVfsListener == ThreeState.UNSURE) {
      isUseVfsListener = ThreeState.fromBoolean(streamProvider == null || !streamProvider!!.enabled)
    }

    //noinspection deprecation
    if (stateSplitter != javaClass<StateSplitter>() && stateSplitter != javaClass<StateSplitterEx>()) {
      val directoryBasedStorage = MyDirectoryStorage(this, file, ReflectionUtil.newInstance(stateSplitter))
      virtualFileTracker?.put(filePath.normalizePath(), directoryBasedStorage)
      return directoryBasedStorage
    }

    if (!ApplicationManager.getApplication().isHeadlessEnvironment() && PathUtilRt.getFileName(filePath).lastIndexOf('.') < 0) {
      throw IllegalArgumentException("Extension is missing for storage file: $filePath")
    }

    val effectiveRoamingType = if (roamingType == RoamingType.PER_USER && fileSpec == StoragePathMacros.WORKSPACE_FILE) RoamingType.DISABLED else roamingType
    val storage = MyFileStorage(this, file, fileSpec, rootTagName, effectiveRoamingType, getMacroSubstitutor(fileSpec), streamProvider)
    if (isUseVfsListener == ThreeState.YES) {
      virtualFileTracker?.put(filePath.normalizePath(), storage)
    }
    return storage
  }

  private class MyDirectoryStorage(override val storageManager: StateStorageManagerImpl, file: File, splitter: StateSplitter) : DirectoryBasedStorage(storageManager.pathMacroSubstitutor, file, splitter), StorageVirtualFileTracker.TrackedStorage

  private class MyFileStorage(override val storageManager: StateStorageManagerImpl,
                              file: File,
                              fileSpec: String,
                              rootElementName: String,
                              roamingType: RoamingType,
                              pathMacroManager: TrackingPathMacroSubstitutor? = null,
                              provider: StreamProvider? = null) : FileBasedStorage(file, fileSpec, rootElementName, pathMacroManager, roamingType, provider), StorageVirtualFileTracker.TrackedStorage {
    override val isUseXmlProlog: Boolean
      get() = storageManager.isUseXmlProlog

    override fun beforeElementSaved(element: Element) {
      storageManager.beforeElementSaved(element)
      super<FileBasedStorage>.beforeElementSaved(element)
    }

    override fun beforeElementLoaded(element: Element) {
      storageManager.beforeElementLoaded(element)
      super<FileBasedStorage>.beforeElementLoaded(element)
    }
  }

  private fun String.normalizePath(): String {
    val path = FileUtilRt.toSystemIndependentName(this)
    // fileSpec for directory based storage could be erroneously specified as "name/"
    return if (path.endsWith('/')) path.substring(0, path.length() - 1) else path
  }

  protected open fun beforeElementSaved(element: Element) {
  }

  protected open fun beforeElementLoaded(element: Element) {
  }

  override final fun rename(path: String, newName: String) {
    storageLock.withLock {
      val storage = getOrCreateStorage(collapseMacros(path), RoamingType.PER_USER) as FileBasedStorage

      val file = storage.getVirtualFile()
      try {
        if (file != null) {
          file.rename(storage, newName)
        }
        else if (storage.getFile().getName() != newName) {
          // old file didn't exist or renaming failed
          val expandedPath = expandMacros(path)
          val parentPath = PathUtilRt.getParentPath(expandedPath)
          storage.setFile(null, File(parentPath, newName))
          pathRenamed(expandedPath, "$parentPath/$newName", null)
        }
      }
      catch (e: IOException) {
        LOG.debug(e)
      }
    }
  }

  fun clearStorages() {
    storageLock.withLock {
      try {
        if (virtualFileTracker != null) {
          storages.forEachEntry({ collapsedPath, storage ->
            virtualFileTracker.remove(expandMacros(collapsedPath.normalizePath()))
            true
          })
        }
      }
      finally {
        storages.clear()
      }
    }
  }

  protected open fun getMacroSubstitutor(fileSpec: String): TrackingPathMacroSubstitutor? = pathMacroSubstitutor

  override final fun expandMacros(path: String): String {
    // replacement can contains $ (php tests), so, this check must be performed before expand
    val matcher = MACRO_PATTERN.matcher(path)
    matcherLoop@
    while (matcher.find()) {
      val m = matcher.group(1)
      for (macro in macros) {
        if (macro.key == m) {
          continue@matcherLoop
        }
      }
      throw IllegalArgumentException("Unknown macro: $m in storage file spec: $path")
    }

    var expanded = path
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

  class StateStorageManagerExternalizationSession(protected val storageManager: StateStorageManagerImpl) : StateStorageManager.ExternalizationSession {
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
    val oldStorageSpec = getOldStorageSpec(component, componentName, operation) ?: return null
    @suppress("DEPRECATED_SYMBOL_WITH_MESSAGE")
    return getStateStorage(oldStorageSpec, if (component is com.intellij.openapi.util.RoamingTypeDisabled) RoamingType.DISABLED else RoamingType.PER_USER)
  }

  protected open fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String? = null
}
