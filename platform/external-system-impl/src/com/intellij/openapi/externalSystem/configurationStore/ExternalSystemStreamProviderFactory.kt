/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.configurationStore

import com.intellij.ProjectTopics
import com.intellij.configurationStore.FileStorageAnnotation
import com.intellij.configurationStore.StreamProviderFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.ProjectModelElement
import com.intellij.util.Function
import org.jdom.Element
import java.util.*

private val EXTERNAL_MODULE_STORAGE_ANNOTATION = FileStorageAnnotation(StoragePathMacros.MODULE_FILE, false, ExternalModuleStorage::class.java)
private val LOG = logger<ExternalSystemStreamProviderFactory>()

// todo handle module rename
internal class ExternalSystemStreamProviderFactory(private val project: Project) : StreamProviderFactory {
  val moduleStorage = ModuleFileSystemExternalSystemStorage(project)
  val fileStorage = ProjectFileSystemExternalSystemStorage(project)

  private var isStorageFlushInProgress = false

  init {
    // flush on save to be sure that data is saved (it is easy to reimport if corrupted (force exit, blue screen), but we need to avoid it if possible)
    ApplicationManager.getApplication().messageBus
      .connect(project)
      .subscribe(ProjectEx.ProjectSaved.TOPIC, ProjectEx.ProjectSaved {
        if (it === project && !isStorageFlushInProgress && moduleStorage.isDirty) {
          isStorageFlushInProgress = true
          ApplicationManager.getApplication().executeOnPooledThread {
            try {
              LOG.runAndLogException { moduleStorage.forceSave() }
            }
            finally {
              isStorageFlushInProgress = false
            }
          }
        }
      })
    project.messageBus.connect().subscribe(ProjectTopics.MODULES, object : ModuleListener {
      override fun moduleRemoved(project: Project, module: Module) {
        moduleStorage.remove(module.name)
      }

      override fun modulesRenamed(project: Project, modules: MutableList<Module>, oldNameProvider: Function<Module, String>) {
        for (module in modules) {
          moduleStorage.rename(oldNameProvider.`fun`(module), module.name)
        }
      }
    })
  }

  override fun customizeStorageSpecs(component: PersistentStateComponent<*>, componentManager: ComponentManager, storages: List<Storage>, operation: StateStorageOperation): List<Storage>? {
    val project = componentManager as? Project ?: (componentManager as Module).project
    // we store isExternalStorageEnabled option in the project workspace file, so, for such components external storage is always disabled and not applicable
    if ((storages.size == 1 && storages.first().value == StoragePathMacros.WORKSPACE_FILE) || !project.isExternalStorageEnabled) {
      return null
    }

    if (componentManager is Project) {
      val fileSpec = storages.firstOrNull()?.value
      if (fileSpec == "libraries") {
        val result = ArrayList<Storage>(storages.size + 1)
        result.add(FileStorageAnnotation("$fileSpec.xml", false, ExternalProjectStorage::class.java))
        result.addAll(storages)
        return result
      }
    }

    if (component !is ProjectModelElement) {
      return null
    }

    // Keep in mind - this call will require storage for module because module option values are used.
    // We cannot just check that module name exists in the nameToData - new external system module will be not in the nameToData because not yet saved.
    if (operation == StateStorageOperation.WRITE && component.externalSource == null) {
      return null
    }

    // on read we cannot check because on import module is just created and not yet marked as external system module,
    // so, we just add our storage as first and default storages in the end as fallback

    // on write default storages also returned, because default FileBasedStorage will remove data if component has external source

    val result = ArrayList<Storage>(storages.size + 1)
    if (componentManager is Project) {
      result.add(FileStorageAnnotation(storages.get(0).value, false, ExternalProjectStorage::class.java))
    }
    else {
      result.add(EXTERNAL_MODULE_STORAGE_ANNOTATION)
    }
    result.addAll(storages)
    return result
  }

  fun readModuleData(name: String): Element? {
    val result = moduleStorage.read(name)
    if (result == null) {
      // todo we must detect situation when module was really stored but file was somehow deleted by user / corrupted
      // now we use file-based storage, and, so, not easy to detect this situation on start (because all modules data stored not in the one file, but per file)
    }
    return result
  }
}