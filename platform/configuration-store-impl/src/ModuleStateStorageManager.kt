// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.configurationStore

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.components.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.ModuleEx
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.io.systemIndependentPath
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Path
import kotlin.concurrent.write

@ApiStatus.Internal
internal class ModuleStateStorageManager(macroSubstitutor: TrackingPathMacroSubstitutor, module: Module) : StateStorageManagerImpl("module", macroSubstitutor, module), RenameableStateStorageManager {
  override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation) = StoragePathMacros.MODULE_FILE

  // the only macro is supported by ModuleStateStorageManager
  override fun expandMacro(collapsedPath: String): Path {
    if (collapsedPath != StoragePathMacros.MODULE_FILE) {
      throw IllegalStateException("Cannot resolve $collapsedPath in $macros")
    }
    return macros.get(0).value
  }

  override fun rename(newName: String) {
    storageLock.write {
      val storage = getOrCreateStorage(StoragePathMacros.MODULE_FILE, RoamingType.DEFAULT) as FileBasedStorage
      val file = storage.getVirtualFile(StateStorageOperation.WRITE)
      try {
        if (file != null) {
          file.rename(storage, newName)
        }
        else if (storage.file.fileName.toString() != newName) {
          // old file didn't exist or renaming failed
          val newFile = storage.file.parent.resolve(newName)
          storage.setFile(null, newFile)
          pathRenamed(newFile, null)
        }
      }
      catch (e: IOException) {
        LOG.debug(e)
      }
    }
  }

  override fun clearVirtualFileTracker(virtualFileTracker: StorageVirtualFileTracker) {
    virtualFileTracker.remove(expandMacro(StoragePathMacros.MODULE_FILE).systemIndependentPath)
  }

  override fun pathRenamed(newPath: Path, event: VFileEvent?) {
    try {
      setMacros(listOf(Macro(StoragePathMacros.MODULE_FILE, newPath)))
    }
    finally {
      val requestor = event?.requestor
      if (requestor == null || requestor !is StateStorage /* not renamed as result of explicit rename */) {
        val module = componentManager as ModuleEx
        module.rename(newPath.fileName.toString().removeSuffix(ModuleFileType.DOT_DEFAULT_EXTENSION), false)
      }
    }
  }

  override fun beforeElementLoaded(element: Element) {
    val optionElement = Element("component").setAttribute("name", "DeprecatedModuleOptionManager")
    val iterator = element.attributes.iterator()
    for (attribute in iterator) {
      if (attribute.name != ProjectStateStorageManager.VERSION_OPTION) {
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
    rootAttributes.put(ProjectStateStorageManager.VERSION_OPTION, "4")
  }

  override val isExternalSystemStorageEnabled: Boolean
    get() = (componentManager as Module?)?.project?.isExternalStorageEnabled ?: false

  override fun createFileBasedStorage(path: Path,
                                      collapsedPath: String,
                                      roamingType: RoamingType,
                                      usePathMacroManager: Boolean,
                                      rootTagName: String?): StateStorage {
    return ModuleFileStorage(storageManager = this,
                             file = path,
                             fileSpec = collapsedPath,
                             rootElementName = rootTagName,
                             roamingType = roamingType,
                             pathMacroManager = macroSubstitutor,
                             provider = if (roamingType == RoamingType.DISABLED) null else compoundStreamProvider)
  }

  override fun getFileBasedStorageConfiguration(fileSpec: String) = moduleFileBasedStorageConfiguration

  private class ModuleFileStorage(storageManager: ModuleStateStorageManager,
                                  file: Path,
                                  fileSpec: String,
                                  rootElementName: String?,
                                  roamingType: RoamingType,
                                  pathMacroManager: PathMacroSubstitutor? = null,
                                  provider: StreamProvider? = null) : MyFileStorage(storageManager, file, fileSpec, rootElementName, roamingType, pathMacroManager, provider) {
    override fun handleVirtualFileNotFound() {
      if (storageDataRef.get() == null && !storageManager.isExternalSystemStorageEnabled) {
        throw FileNotFoundException(ConfigurationStoreBundle.message("module.file.does.not.exist.error", file.toString()))
      }
    }
  }
}

private val moduleFileBasedStorageConfiguration = object : FileBasedStorageConfiguration {
  override val isUseVfsForWrite: Boolean
    get() = true

  // use VFS to load module file because it is refreshed and loaded into VFS in any case
  override val isUseVfsForRead: Boolean
    get() = false
}
