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

import com.intellij.openapi.components.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.ModuleEx
import com.intellij.openapi.module.impl.ModuleManagerImpl
import com.intellij.openapi.module.impl.getModuleNameByFilePath
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.LineSeparator
import com.intellij.util.loadElement
import org.jdom.Element
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths

internal class ModuleStateStorageManager(macroSubstitutor: TrackingPathMacroSubstitutor, module: Module) : StateStorageManagerImpl("module", macroSubstitutor, module) {
  override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation) = StoragePathMacros.MODULE_FILE

  override fun pathRenamed(oldPath: String, newPath: String, event: VFileEvent?) {
    try {
      super.pathRenamed(oldPath, newPath, event)
    }
    finally {
      val requestor = event?.requestor
      if (requestor == null || requestor !is StateStorage /* not renamed as result of explicit rename */) {
        val module = componentManager as ModuleEx
        val oldName = module.name
        module.rename(getModuleNameByFilePath(newPath), false)
        (ModuleManager.getInstance(module.project) as? ModuleManagerImpl)?.fireModuleRenamedByVfsEvent(module, oldName)
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

  override fun beforeElementSaved(element: Element) {
    val componentIterator = element.getChildren("component").iterator()
    for (component in componentIterator) {
      if (component.getAttributeValue("name") == "DeprecatedModuleOptionManager") {
        componentIterator.remove()
        for (option in component.getChildren("option")) {
          element.setAttribute(option.getAttributeValue("key"), option.getAttributeValue("value"))
        }
        break
      }
    }

    // need be last for compat reasons
    element.setAttribute(ProjectStateStorageManager.VERSION_OPTION, "4")
  }

  override val isExternalSystemStorageEnabled: Boolean
    get() = (componentManager as Module).project.isExternalStorageEnabled

  override fun createFileBasedStorage(path: String, collapsedPath: String, roamingType: RoamingType, rootTagName: String?): StateStorage
    = ModuleFileStorage(this, Paths.get(path), collapsedPath, rootTagName, roamingType, getMacroSubstitutor(collapsedPath), if (roamingType == RoamingType.DISABLED) null else compoundStreamProvider)

  private class ModuleFileStorage(storageManager: ModuleStateStorageManager,
                                  file: Path,
                                  fileSpec: String,
                                  rootElementName: String?,
                                  roamingType: RoamingType,
                                  pathMacroManager: TrackingPathMacroSubstitutor? = null,
                                  provider: StreamProvider? = null) : MyFileStorage(storageManager, file, fileSpec, rootElementName, roamingType, pathMacroManager, provider) {
    // use VFS to load module file because it is refreshed and loaded into VFS in any case
    override fun loadLocalData(): Element? {
      blockSavingTheContent = false
      val virtualFile = virtualFile
      if (virtualFile == null || !virtualFile.exists()) {
        // only on first load
        if (storageDataRef.get() == null && !storageManager.isExternalSystemStorageEnabled) {
          throw FileNotFoundException(ProjectBundle.message("module.file.does.not.exist.error", file.toString()))
        }
        else {
          return null
        }
      }

      if (virtualFile.length == 0L) {
        processReadException(null)
      }
      else {
        runAndHandleExceptions {
          val charBuffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(virtualFile.contentsToByteArray()))
          lineSeparator = detectLineSeparators(charBuffer, if (isUseXmlProlog) null else LineSeparator.LF)
          return loadElement(charBuffer)
        }
      }
      return null
    }
  }
}