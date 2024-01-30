// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

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
import java.io.IOException
import java.nio.file.Path
import kotlin.concurrent.write

@ApiStatus.Internal
internal class ModuleStateStorageManager(macroSubstitutor: TrackingPathMacroSubstitutor, module: Module)
  : StateStorageManagerImpl("module", macroSubstitutor, module,), RenameableStateStorageManager
{
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
      val file = storage.getVirtualFile()
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

  override fun isUseVfsForWrite(): Boolean = true

  override fun createFileBasedStorage(file: Path,
                                      collapsedPath: String,
                                      roamingType: RoamingType,
                                      usePathMacroManager: Boolean,
                                      rootTagName: String?): StateStorage {
    val provider = if (roamingType == RoamingType.DISABLED) null else compoundStreamProvider
    return TrackedFileStorage(storageManager = this, file, collapsedPath, rootTagName, roamingType, macroSubstitutor, provider)
  }
}
