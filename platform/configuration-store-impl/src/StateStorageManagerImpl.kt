// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.configurationStore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.components.StateStorageChooserEx.Resolution
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.ProjectModelElement
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.settings.SettingsController
import com.intellij.util.ReflectionUtil
import com.intellij.util.SmartList
import com.intellij.util.ThreeState
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.io.path.invariantSeparatorsPathString

/**
 * If componentManager not specified, storage will not add file tracker
 */
@Internal
open class StateStorageManagerImpl(
  @NonNls private val rootTagName: String,
  final override val macroSubstitutor: PathMacroSubstitutor? = null,
  final override val componentManager: ComponentManager?,
  private val settingsController: SettingsController? = null,
) : StateStorageManager {
  private val virtualFileTracker = createDefaultVirtualTracker(componentManager)

  @Volatile
  protected var macros: List<Macro> = Collections.emptyList()

  protected val storageLock = ReentrantReadWriteLock()
  private val storages = HashMap<String, StateStorage>()

  val compoundStreamProvider: CompoundStreamProvider = CompoundStreamProvider()

  override fun addStreamProvider(provider: StreamProvider, first: Boolean) {
    compoundStreamProvider.addStreamProvider(provider = provider, first = first)
  }

  override fun removeStreamProvider(aClass: Class<out StreamProvider>) {
    compoundStreamProvider.removeStreamProvider(aClass)
  }

  // access under storageLock
  private var isUseVfsListener = when (componentManager) {
    null -> ThreeState.NO
    else -> ThreeState.UNSURE // unsure because depends on stream provider state
  }

  open fun getFileBasedStorageConfiguration(fileSpec: String): FileBasedStorageConfiguration = defaultFileBasedStorageConfiguration

  protected open val isUseXmlProlog: Boolean
    get() = true

  /**
   * Returns an old map.
   */
  fun setMacros(map: List<Macro>): List<Macro> {
    val oldValue = macros
    macros = map
    return oldValue
  }

  @Suppress("CAST_NEVER_SUCCEEDS")
  final override fun getStateStorage(storageSpec: Storage): StateStorage {
    return getOrCreateStorage(
      collapsedPath = storageSpec.path,
      roamingType = storageSpec.roamingType,
      storageClass = storageSpec.storageClass.java,
      stateSplitter = storageSpec.stateSplitter.java,
      exclusive = storageSpec.exclusive,
      storageCreator = storageSpec as? StorageCreator,
      usePathMacroManager = storageSpec.usePathMacroManager,
    )
  }

  protected open fun normalizeFileSpec(fileSpec: String): String {
    val path = FileUtilRt.toSystemIndependentName(fileSpec)
    // fileSpec for directory-based storage could be erroneously specified as "name/"
    return if (path.endsWith('/')) path.substring(0, path.length - 1) else path
  }

  // storageCustomizer - to ensure that other threads will use fully constructed and configured storage (invoked under the same lock as created)
  fun getOrCreateStorage(collapsedPath: String,
                         roamingType: RoamingType,
                         storageClass: Class<out StateStorage> = StateStorage::class.java,
                         @Suppress("DEPRECATION", "removal") stateSplitter: Class<out StateSplitter> = StateSplitterEx::class.java,
                         exclusive: Boolean = false,
                         storageCustomizer: (StateStorage.() -> Unit)? = null,
                         storageCreator: StorageCreator? = null,
                         usePathMacroManager: Boolean = true): StateStorage {
    val normalizedCollapsedPath = normalizeFileSpec(collapsedPath)
    val key = computeStorageKey(storageClass = storageClass,
                                normalizedCollapsedPath = normalizedCollapsedPath,
                                collapsedPath = collapsedPath,
                                storageCreator = storageCreator)
    val storage = storageLock.read { storages.get(key) } ?: return storageLock.write {
      storages.getOrPut(key) {
        val storage = when (storageCreator) {
          null -> createStateStorage(storageClass = storageClass,
                                     collapsedPath = normalizedCollapsedPath,
                                     roamingType = roamingType,
                                     stateSplitter = stateSplitter,
                                     usePathMacroManager = usePathMacroManager,
                                     exclusive = exclusive)
          else -> storageCreator.create(this)
        }
        storageCustomizer?.let { storage.it() }
        storage
      }
    }

    storageCustomizer?.let { storage.it() }
    return storage
  }

  private fun computeStorageKey(storageClass: Class<out StateStorage>,
                                normalizedCollapsedPath: String,
                                collapsedPath: String,
                                storageCreator: StorageCreator?): String {
    if (storageClass != StateStorage::class.java) {
      return storageClass.name
    }
    check(!normalizedCollapsedPath.isEmpty()) {
      "Normalized path is empty, raw path '$collapsedPath'"
    }
    return storageCreator?.key ?: normalizedCollapsedPath
  }

  fun getCachedFileStorages(): Set<StateStorage> = storageLock.read { storages.values.toSet() }

  fun getCachedFileStorages(changed: Collection<String>,
                            deleted: Collection<String>,
                            pathNormalizer: ((String) -> String)? = null): Pair<Collection<FileBasedStorage>, Collection<FileBasedStorage>> {
    return storageLock.read {
      Pair(getCachedFileStorages(changed, pathNormalizer), getCachedFileStorages(deleted, pathNormalizer))
    }
  }

  fun updatePath(spec: String, newPath: Path) {
    val storage = getCachedFileStorages(listOf(spec)).firstOrNull() ?: return
    if (storage is StorageVirtualFileTracker.TrackedStorage && virtualFileTracker != null) {
      virtualFileTracker.remove(storage.file.invariantSeparatorsPathString)
      virtualFileTracker.put(newPath.invariantSeparatorsPathString, storage)
    }
    storage.setFile(null, newPath)
  }

  fun getCachedFileStorages(fileSpecs: Collection<String>, pathNormalizer: ((String) -> String)? = null): Collection<FileBasedStorage> {
    if (fileSpecs.isEmpty()) {
      return emptyList()
    }

    storageLock.read {
      var result: MutableList<FileBasedStorage>? = null
      for (fileSpec in fileSpecs) {
        val path = normalizeFileSpec(pathNormalizer?.invoke(fileSpec) ?: fileSpec)
        val storage = storages.get(path)
        if (storage is FileBasedStorage) {
          if (result == null) {
            result = SmartList()
          }
          result.add(storage)
        }
      }
      return result ?: emptyList()
    }
  }

  private fun createStateStorage(storageClass: Class<out StateStorage>,
                                 collapsedPath: String,
                                 roamingType: RoamingType,
                                 @Suppress("DEPRECATION", "removal") stateSplitter: Class<out StateSplitter>,
                                 usePathMacroManager: Boolean,
                                 exclusive: Boolean = false): StateStorage {
    if (storageClass != StateStorage::class.java) {
      val constructor = storageClass.constructors.first { it.parameterCount <= 3 }
      constructor.isAccessible = true
      if (constructor.parameterCount == 2) {
        return constructor.newInstance(componentManager!!, this) as StateStorage
      }
      else {
        return constructor.newInstance(collapsedPath, componentManager!!, this) as StateStorage
      }
    }

    val effectiveRoamingType = getEffectiveRoamingType(roamingType, collapsedPath)
    if (isUseVfsListener == ThreeState.UNSURE) {
      isUseVfsListener = ThreeState.fromBoolean(!compoundStreamProvider.isApplicable(collapsedPath, effectiveRoamingType))
    }

    val filePath = expandMacro(collapsedPath)
    @Suppress("DEPRECATION", "removal")
    if (stateSplitter != StateSplitter::class.java && stateSplitter != StateSplitterEx::class.java) {
      val storage = createDirectoryBasedStorage(filePath, collapsedPath, ReflectionUtil.newInstance(stateSplitter))
      if (storage is StorageVirtualFileTracker.TrackedStorage && virtualFileTracker != null) {
        virtualFileTracker.put(filePath.invariantSeparatorsPathString, storage)
      }
      return storage
    }

    val app = ApplicationManager.getApplication()
    if (app != null && !app.isHeadlessEnvironment && !collapsedPath.endsWith('$') && !collapsedPath.contains('.')) {
      throw IllegalArgumentException("Extension is missing for storage file: $collapsedPath")
    }

    val storage = createFileBasedStorage(path = filePath,
                                         collapsedPath = collapsedPath,
                                         roamingType = effectiveRoamingType,
                                         usePathMacroManager = usePathMacroManager,
                                         rootTagName = if (exclusive) null else rootTagName)
    if (isUseVfsListener == ThreeState.YES && storage is StorageVirtualFileTracker.TrackedStorage && virtualFileTracker != null) {
      virtualFileTracker.put(filePath.invariantSeparatorsPathString, storage)
    }
    return storage
  }

  protected open fun createFileBasedStorage(path: Path,
                                            collapsedPath: String,
                                            roamingType: RoamingType,
                                            usePathMacroManager: Boolean,
                                            rootTagName: String?): StateStorage {
    compoundStreamProvider.deleteIfObsolete(collapsedPath, roamingType)
    if (roamingType == RoamingType.DISABLED && settingsController != null) {
      settingsController.createStateStorage(collapsedPath)?.let {
        return it  as StateStorage
      }
    }

    return MyFileStorage(storageManager = this,
                         file = path,
                         fileSpec = collapsedPath,
                         rootElementName = rootTagName,
                         roamingType = roamingType,
                         pathMacroManager = if (usePathMacroManager) macroSubstitutor else null,
                         provider = compoundStreamProvider)
  }

  // open for upsource
  protected open fun createDirectoryBasedStorage(file: Path,
                                                 collapsedPath: String,
                                                 @Suppress("DEPRECATION", "removal") splitter: StateSplitter): StateStorage {
    return object : DirectoryBasedStorage(dir = file,
                                          splitter = splitter,
                                          pathMacroSubstitutor = macroSubstitutor), StorageVirtualFileTracker.TrackedStorage {
      override val storageManager = this@StateStorageManagerImpl
    }
  }

  protected open class MyFileStorage(
    override val storageManager: StateStorageManagerImpl,
    file: Path,
    fileSpec: String,
    rootElementName: String?,
    roamingType: RoamingType,
    pathMacroManager: PathMacroSubstitutor? = null,
    provider: StreamProvider? = null,
  ) : FileBasedStorage(file = file,
                       fileSpec = fileSpec,
                       rootElementName = rootElementName,
                       pathMacroManager = pathMacroManager,
                       roamingType = roamingType,
                       provider = provider), StorageVirtualFileTracker.TrackedStorage {
    override val isUseXmlProlog: Boolean
      get() = rootElementName != null && storageManager.isUseXmlProlog && !isSpecialStorage(fileSpec)

    override val configuration: FileBasedStorageConfiguration
      get() = storageManager.getFileBasedStorageConfiguration(fileSpec)

    override fun beforeElementSaved(elements: MutableList<Element>, rootAttributes: MutableMap<String, String>) {
      if (rootElementName != null) {
        storageManager.beforeElementSaved(elements, rootAttributes)
      }
      super.beforeElementSaved(elements, rootAttributes)
    }

    override fun beforeElementLoaded(element: Element) {
      storageManager.beforeElementLoaded(element)
      super.beforeElementLoaded(element)
    }

    override fun providerDataStateChanged(writer: DataWriter?, type: DataStateChanged) {
      storageManager.providerDataStateChanged(storage = this, writer = writer, type = type)
      super.providerDataStateChanged(writer, type)
    }

    override fun getResolution(component: PersistentStateComponent<*>, operation: StateStorageOperation): Resolution {
      if (operation == StateStorageOperation.WRITE && component is ProjectModelElement &&
          storageManager.isExternalSystemStorageEnabled && component.externalSource != null) {
        return Resolution.CLEAR
      }
      return Resolution.DO
    }
  }

  open val isExternalSystemStorageEnabled: Boolean
    get() = false

  // function must be pure and do not use anything outside passed arguments
  protected open fun beforeElementSaved(elements: MutableList<Element>, rootAttributes: MutableMap<String, String>) {
  }

  protected open fun providerDataStateChanged(storage: FileBasedStorage, writer: DataWriter?, type: DataStateChanged) {
  }

  protected open fun beforeElementLoaded(element: Element) {
  }

  fun clearStorages() {
    storageLock.write {
      try {
        if (virtualFileTracker != null) {
          clearVirtualFileTracker(virtualFileTracker)
        }
      }
      finally {
        storages.clear()
      }
    }
  }

  internal open fun clearVirtualFileTracker(virtualFileTracker: StorageVirtualFileTracker) {
    for (collapsedPath in storages.keys) {
      virtualFileTracker.remove(expandMacro(collapsedPath).invariantSeparatorsPathString)
    }
  }

  override fun expandMacro(collapsedPath: String): Path {
    for ((key, value) in macros) {
      if (key == collapsedPath) {
        return value
      }

      if (collapsedPath.length > (key.length + 2) && collapsedPath.get(key.length) == '/' && collapsedPath.startsWith(key)) {
        return value.resolve(collapsedPath.substring(key.length + 1))
      }
    }

    throw IllegalStateException("Cannot resolve $collapsedPath in $macros")
  }

  fun collapseMacro(path: String): String {
    for ((key, value) in macros) {
      val result = path.replace(value.invariantSeparatorsPathString, key)
      if (result !== path) {
        return result
      }
    }
    return normalizeFileSpec(path)
  }

  final override fun getOldStorage(component: Any, componentName: String, operation: StateStorageOperation): StateStorage? {
    val oldStorageSpec = getOldStorageSpec(component = component, componentName = componentName, operation = operation) ?: return null
    return getOrCreateStorage(oldStorageSpec, RoamingType.DEFAULT)
  }

  protected open fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String? = null

  internal fun disposed() {
    virtualFileTracker?.remove { it.storageManager === this }
  }
}

fun removeMacroIfStartsWith(path: String, macro: String): String = path.removePrefix("$macro/")

@Suppress("DEPRECATION", "removal")
internal val Storage.path: String
  get() = value.ifEmpty { file }

internal fun getEffectiveRoamingType(roamingType: RoamingType, collapsedPath: String): RoamingType {
  if (roamingType != RoamingType.DISABLED &&
      (collapsedPath == StoragePathMacros.WORKSPACE_FILE ||
       collapsedPath == StoragePathMacros.NON_ROAMABLE_FILE ||
       isSpecialStorage(collapsedPath))) {
    return RoamingType.DISABLED
  }
  else {
    return roamingType
  }
}

@Internal
data class Macro(@JvmField val key: String, @JvmField var value: Path)

@TestOnly
fun checkStorageIsNotTracked(module: ComponentManager) {
  service<StorageVirtualFileTracker>().remove {
    if (it.storageManager.componentManager === module) {
      throw AssertionError("Storage manager is not disposed, module $module, storage $it")
    }
    false
  }
}

private class MyAsyncVfsListener : AsyncFileListener {
  override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
    LOG.debug { "Got a change in MyAsyncVfsListener: $events" }
    service<StorageVirtualFileTracker>().schedule(events)
    return null
  }

  companion object {
    private val LOG = logger<MyAsyncVfsListener>()
  }
}

private fun createDefaultVirtualTracker(componentManager: ComponentManager?): StorageVirtualFileTracker? {
  return if (componentManager == null) null else service<StorageVirtualFileTracker>()
}
