// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.configurationStore

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.ModuleEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.project.isDirectoryBased
import com.intellij.workspaceModel.ide.legacyBridge.ModuleStore
import org.jdom.Element
import java.io.IOException
import java.nio.file.Path
import kotlin.concurrent.write
import kotlin.io.path.invariantSeparatorsPathString

private val MODULE_FILE_STORAGE_ANNOTATION = FileStorageAnnotation(StoragePathMacros.MODULE_FILE, false)

internal class ModuleStoreImpl(module: Module, private val pathMacroManager: PathMacroManager) : ComponentStoreImpl(), ModuleStore {
  override val project: Project = module.project

  override val storageManager: StateStorageManagerImpl = ModuleStateStorageManager(TrackingPathMacroSubstitutorImpl(pathMacroManager), module)

  override fun createSaveSessionProducerManager(): SaveSessionProducerManager {
    return SaveSessionProducerManager(isUseVfsForWrite = storageManager.isUseVfsForWrite, collectVfsEvents = true)
  }

  override fun isReportStatisticAllowed(stateSpec: State, storageSpec: Storage): Boolean = false

  override fun getPathMacroManagerForDefaults(): PathMacroManager = pathMacroManager

  override fun <T> getStorageSpecs(
    component: PersistentStateComponent<T>,
    stateSpec: State,
    operation: StateStorageOperation,
  ): List<Storage> {
    val result = if (stateSpec.storages.isEmpty()) {
      listOf(MODULE_FILE_STORAGE_ANNOTATION)
    }
    else {
      super.getStorageSpecs(component = component, stateSpec = stateSpec, operation = operation)
    }

    if (project.isDirectoryBased) {
      for (provider in StreamProviderFactory.EP_NAME.getExtensions(project)) {
        runCatching {
          provider.customizeStorageSpecs(
            component = component,
            storageManager = storageManager,
            stateSpec = stateSpec,
            storages = result,
            operation = operation,
          )
        }.getOrLogException(LOG)?.let {
          return it
        }
      }
    }

    return result
  }

  override fun reloadStates(componentNames: Set<String>) {
    batchReloadStates(componentNames, project.messageBus)
  }

  override fun setPath(path: Path) {
    setPath(path = path, isNew = false)
  }

  override fun setPath(path: Path, isNew: Boolean) {
    val isMacroAdded = storageManager.setMacros(java.util.List.of(Macro(StoragePathMacros.MODULE_FILE, path))).isEmpty()
    // if file not null - update storage
    storageManager.getOrCreateStorage(
      collapsedPath = StoragePathMacros.MODULE_FILE,
      roamingType = RoamingType.DEFAULT,
      storageCustomizer = {
        if (this !is FileBasedStorage) {
          return@getOrCreateStorage
        }

        setFile(virtualFile = null, ioFileIfChanged = if (isMacroAdded) null else path)
        // ModifiableModuleModel#newModule should always create a new module from scratch
        // https://youtrack.jetbrains.com/issue/IDEA-147530
        if (isMacroAdded) {
          // preload to ensure that we will get a FileNotFound error (no module file) during initialization
          // and not later in some unexpected place (because otherwise will be loaded by demand)
          preloadStorageData(isNew)
        }
        else {
          storageManager.updatePath(spec = StoragePathMacros.MODULE_FILE, newPath = path)
        }
      })
  }
}

private class ModuleStateStorageManager(macroSubstitutor: TrackingPathMacroSubstitutor, module: Module)
  : StateStorageManagerImpl(rootTagName = "module", macroSubstitutor, componentManager = module, controller = null),
    RenameableStateStorageManager
{
  override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String = StoragePathMacros.MODULE_FILE

  // the only macro is supported by ModuleStateStorageManager
  override fun expandMacro(collapsedPath: String): Path {
    if (collapsedPath != StoragePathMacros.MODULE_FILE) {
      throw IllegalStateException("Cannot resolve $collapsedPath in $macros")
    }
    return macros[0].value
  }

  override fun rename(newName: String) {
    storageLock.write {
      val storage = getOrCreateStorage(collapsedPath = StoragePathMacros.MODULE_FILE, roamingType = RoamingType.DEFAULT) as FileBasedStorage
      val file = storage.getVirtualFile()
      try {
        if (file != null) {
          file.rename(storage, newName)
        }
        else if (storage.file.fileName.toString() != newName) {
          // the old file didn't exist or renaming failed
          val newFile = storage.file.parent.resolve(newName)
          storage.setFile(virtualFile = null, ioFileIfChanged = newFile)
          pathRenamed(newPath = newFile, event = null)
        }
      }
      catch (e: IOException) {
        LOG.debug(e)
      }
    }
  }

  override fun clearVirtualFileTracker(virtualFileTracker: StorageVirtualFileTracker) {
    virtualFileTracker.remove(expandMacro(StoragePathMacros.MODULE_FILE).invariantSeparatorsPathString)
  }

  override fun pathRenamed(newPath: Path, event: VFileEvent?) {
    try {
      setMacros(java.util.List.of(Macro(StoragePathMacros.MODULE_FILE, newPath)))
    }
    finally {
      val requestor = event?.requestor
      if (requestor == null || requestor !is StateStorage /* not renamed as a result of explicit rename */) {
        val module = componentManager as ModuleEx
        module.rename(newPath.fileName.toString().removeSuffix(ModuleFileType.DOT_DEFAULT_EXTENSION), false)
      }
    }
  }

  override fun beforeElementLoaded(element: Element) {
    val optionElement = Element("component").setAttribute("name", "DeprecatedModuleOptionManager")
    val iterator = element.attributes.iterator()
    for (attribute in iterator) {
      if (attribute.name != VERSION_OPTION) {
        iterator.remove()
        optionElement.addContent(Element("option").setAttribute("key", attribute.name).setAttribute("value", attribute.value))
      }
    }

    element.addContent(optionElement)
  }

  override fun beforeElementSaved(elements: MutableList<Element>, rootAttributes: MutableMap<String, String>) {
    val componentIterator = elements.iterator()
    for (component in componentIterator) {
      if (component.getAttributeValue("name") == "DeprecatedModuleOptionManager") {
        componentIterator.remove()
        for (option in component.getChildren("option")) {
          rootAttributes.put(option.getAttributeValue("key"), option.getAttributeValue("value"))
        }
        break
      }
    }

    // need be last for compat reasons
    rootAttributes.put(VERSION_OPTION, "4")
  }

  override val isExternalSystemStorageEnabled: Boolean
    get() = (componentManager as Module?)?.project?.isExternalStorageEnabled == true

  override val isUseVfsForWrite: Boolean
    get() = !useBackgroundSave()

  override fun createFileBasedStorage(
    file: Path,
    collapsedPath: String,
    roamingType: RoamingType,
    usePathMacroManager: Boolean,
    rootTagName: String?
  ): StateStorage {
    val provider = if (roamingType == RoamingType.DISABLED) null else streamProvider
    return TrackedFileStorage(
      storageManager = this,
      file = file,
      fileSpec = collapsedPath,
      rootElementName = rootTagName,
      roamingType = roamingType,
      pathMacroManager = macroSubstitutor,
      provider = provider,
      controller = null,
    )
  }
}
