// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")
@file:OptIn(SettingsInternalApi::class)

package com.intellij.configurationStore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.components.StateStorageChooserEx.Resolution
import com.intellij.openapi.roots.ProjectModelElement
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.settings.SettingsController
import com.intellij.util.ReflectionUtil
import com.intellij.util.SmartList
import com.intellij.util.ThreeState
import com.intellij.util.xmlb.SettingsInternalApi
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.io.path.invariantSeparatorsPathString

/**
 * If componentManager not specified, storage will not add file tracker
 */
@ApiStatus.Internal
open class StateStorageManagerImpl(
  private val rootTagName: String,
  final override val macroSubstitutor: PathMacroSubstitutor? = null,
  final override val componentManager: ComponentManager?,
  private val controller: SettingsController?,
) : StateStorageManager {
  private val virtualFileTracker = if (componentManager == null) null else service<StorageVirtualFileTracker>()

  @Volatile
  @JvmField
  internal var macros: List<Macro> = java.util.List.of()

  @JvmField
  protected val storageLock: ReentrantReadWriteLock = ReentrantReadWriteLock()
  private val storages = HashMap<String, StateStorage>()

  override val streamProvider: StreamProvider
    get() = compoundStreamProvider

  private val compoundStreamProvider = CompoundStreamProvider()

  override fun addStreamProvider(provider: StreamProvider, first: Boolean) {
    compoundStreamProvider.addStreamProvider(provider = provider, first = first)
  }

  override fun removeStreamProvider(aClass: Class<out StreamProvider>) {
    compoundStreamProvider.removeStreamProvider(aClass)
  }

  // access under storageLock
  private var isUseVfsListener = when (componentManager) {
    null -> ThreeState.NO
    // unsure because depends on stream provider state
    else -> ThreeState.UNSURE
  }

  protected open val isUseXmlProlog: Boolean
    get() = true

  /**
   * Returns an old list.
   */
  internal fun setMacros(list: List<Macro>): List<Macro> {
    val oldValue = macros
    macros = list
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
  fun getOrCreateStorage(
    collapsedPath: String,
    roamingType: RoamingType,
    storageClass: Class<out StateStorage> = StateStorage::class.java,
    @Suppress("DEPRECATION", "removal") stateSplitter: Class<out StateSplitter> = StateSplitterEx::class.java,
    exclusive: Boolean = false,
    storageCustomizer: (StateStorage.() -> Unit)? = null,
    storageCreator: StorageCreator? = null,
    usePathMacroManager: Boolean = true,
  ): StateStorage {
    val normalizedCollapsedPath = normalizeFileSpec(collapsedPath)
    val key = computeStorageKey(
      storageClass = storageClass,
      normalizedCollapsedPath = normalizedCollapsedPath,
      collapsedPath = collapsedPath,
      storageCreator = storageCreator,
    )
    val storage = storageLock.read { storages.get(key) } ?: return storageLock.write {
      storages.getOrPut(key) {
        val storage = when (storageCreator) {
          null -> createStateStorage(
            storageClass = storageClass,
            collapsedPath = normalizedCollapsedPath,
            roamingType = roamingType,
            stateSplitter = stateSplitter,
            usePathMacroManager = usePathMacroManager,
            exclusive = exclusive,
          )
          else -> storageCreator.create(this)
        }
        storageCustomizer?.invoke(storage)
        storage
      }
    }

    storageCustomizer?.invoke(storage)
    return storage
  }

  private fun computeStorageKey(
    storageClass: Class<out StateStorage>,
    normalizedCollapsedPath: String,
    collapsedPath: String,
    storageCreator: StorageCreator?,
  ): String {
    if (storageClass != StateStorage::class.java) {
      return storageClass.name
    }
    check(!normalizedCollapsedPath.isEmpty()) {
      "Normalized path is empty, raw path '$collapsedPath'"
    }
    return storageCreator?.key ?: normalizedCollapsedPath
  }

  fun getCachedFileStorages(): Set<StateStorage> = storageLock.read { storages.values.toSet() }

  fun getCachedFileStorages(
    changed: Collection<String>,
    deleted: Collection<String>,
    pathNormalizer: ((String) -> String)? = null,
  ): Pair<Collection<FileBasedStorage>, Collection<FileBasedStorage>> {
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
        val path = normalizeFileSpec(fileSpec = pathNormalizer?.invoke(fileSpec) ?: fileSpec)
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

  private fun createStateStorage(
    storageClass: Class<out StateStorage>,
    collapsedPath: String,
    roamingType: RoamingType,
    @Suppress("DEPRECATION", "removal") stateSplitter: Class<out StateSplitter>,
    usePathMacroManager: Boolean,
    exclusive: Boolean
  ): StateStorage {
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
      val storage = TrackedDirectoryStorage(
        storageManager = this,
        dir = filePath,
        splitter = ReflectionUtil.newInstance(stateSplitter),
        macroSubstitutor = macroSubstitutor,
        controller = controller,
      )
      virtualFileTracker?.put(filePath.invariantSeparatorsPathString, storage)
      return storage
    }

    val app = ApplicationManager.getApplication()
    if (app != null && !app.isHeadlessEnvironment && !collapsedPath.endsWith('$') && !collapsedPath.contains('.')) {
      throw IllegalArgumentException("Extension is missing for storage file: $collapsedPath")
    }

    val storage = createFileBasedStorage(
      file = filePath,
      collapsedPath = collapsedPath,
      roamingType = effectiveRoamingType,
      usePathMacroManager = usePathMacroManager,
      rootTagName = if (exclusive) null else rootTagName,
    )
    if (isUseVfsListener == ThreeState.YES && storage is StorageVirtualFileTracker.TrackedStorage && virtualFileTracker != null) {
      virtualFileTracker.put(filePath.invariantSeparatorsPathString, storage)
    }
    return storage
  }

  internal open val isUseVfsForWrite: Boolean
    get() = false

  protected open fun createFileBasedStorage(
    file: Path,
    collapsedPath: String,
    roamingType: RoamingType,
    usePathMacroManager: Boolean,
    rootTagName: String?
  ): StateStorage {
    compoundStreamProvider.deleteIfObsolete(collapsedPath, roamingType)
    if (roamingType == RoamingType.DISABLED && controller != null) {
      controller.createStateStorage(collapsedPath, file)?.let {
        return it as StateStorage
      }
    }
    val pathMacroManager = if (usePathMacroManager) macroSubstitutor else null
    val controller = controller?.takeIf { it.isPersistenceStateComponentProxy() }
    return TrackedFileStorage(
      storageManager = this,
      file = file,
      fileSpec = collapsedPath,
      rootElementName = rootTagName,
      roamingType = roamingType,
      pathMacroManager = pathMacroManager,
      provider = compoundStreamProvider,
      controller = controller,
    )
  }

  internal class TrackedFileStorage(
    override val storageManager: StateStorageManagerImpl,
    file: Path,
    fileSpec: String,
    rootElementName: String?,
    roamingType: RoamingType,
    pathMacroManager: PathMacroSubstitutor?,
    provider: StreamProvider?,
    override val controller: SettingsController?,
  ) : FileBasedStorage(
    file = file,
    fileSpec = fileSpec,
    rootElementName = rootElementName,
    pathMacroManager = pathMacroManager,
    roamingType = roamingType,
    provider = provider,
  ), StorageVirtualFileTracker.TrackedStorage {
    override val isUseXmlProlog: Boolean
      get() = rootElementName != null && storageManager.isUseXmlProlog && !isSpecialStorage(fileSpec)

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
      val clearExtStorage = operation == StateStorageOperation.WRITE &&
                            storageManager.isExternalSystemStorageEnabled &&
                            (component as? ProjectModelElement)?.externalSource != null
      return if (clearExtStorage) Resolution.CLEAR else Resolution.DO
    }
  }

  open val isExternalSystemStorageEnabled: Boolean
    get() = false

  // the function must be pure and do not use anything outside passed arguments
  protected open fun beforeElementSaved(elements: MutableList<Element>, rootAttributes: MutableMap<String, String>) { }

  protected open fun providerDataStateChanged(storage: FileBasedStorage, writer: DataWriter?, type: DataStateChanged) { }

  protected open fun beforeElementLoaded(element: Element) { }

  final override fun clearStorages() {
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

      if (collapsedPath.length > (key.length + 2) && collapsedPath[key.length] == '/' && collapsedPath.startsWith(key)) {
        return value.resolve(collapsedPath.substring(key.length + 1))
      }
    }

    throw IllegalStateException("Cannot resolve $collapsedPath in $macros")
  }

  final override fun collapseMacro(path: String): String {
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
    return getOrCreateStorage(collapsedPath = oldStorageSpec, roamingType = RoamingType.DEFAULT)
  }

  protected open fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String? = null

  final override fun release() {
    virtualFileTracker?.remove { it.storageManager === this }
    controller?.release()
  }
}

private class TrackedDirectoryStorage(
  override val storageManager: StateStorageManagerImpl,
  dir: Path,
  @Suppress("DEPRECATION", "removal") splitter: StateSplitter,
  macroSubstitutor: PathMacroSubstitutor?,
  controller: SettingsController?,
) : DirectoryBasedStorage(
  dir = dir,
  splitter = splitter,
  pathMacroSubstitutor = macroSubstitutor,
  controller = controller,
), StorageVirtualFileTracker.TrackedStorage

internal data class Macro(@JvmField val key: String, @JvmField var value: Path)