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
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.catchAndLog
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.roots.ProjectModelElement
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.Function
import com.intellij.util.io.*
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.io.InputStream
import java.util.*

private val EXTERNAL_STORAGE_ANNOTATION = FileStorageAnnotation(StoragePathMacros.MODULE_FILE, false, ExternalProjectStorage::class.java)
private val LOG = logger<ExternalSystemStreamProviderFactory>()

private fun isEnabled() = Registry.`is`("store.imported.project.elements.separately", false) || IS_ENABLED

// test only
internal var IS_ENABLED = false

// todo handle module rename
internal class ExternalSystemStreamProviderFactory(private val project: Project) : StreamProviderFactory {
  val nameToData by lazy { createStorage(project) }

  private var isStorageFlushInProgress = false

  init {
    if (isEnabled()) {
      Disposer.register(project, Disposable { nameToData.close() })

      // flush on save to be sure that data is saved (it is easy to reimport if corrupted (force exit, blue screen), but we need to avoid it if possible)
      ApplicationManager.getApplication().messageBus
        .connect(project)
        .subscribe(ProjectEx.ProjectSaved.TOPIC, ProjectEx.ProjectSaved {
          if (it === project && !isStorageFlushInProgress && nameToData.isDirty) {
            isStorageFlushInProgress = true
            ApplicationManager.getApplication().executeOnPooledThread {
              try {
                LOG.catchAndLog { nameToData.force() }
              }
              finally {
                isStorageFlushInProgress = false
              }
            }
          }
        })
    }

    project.messageBus.connect().subscribe(ProjectTopics.MODULES, object: ModuleListener {
      override fun moduleRemoved(project: Project, module: Module) {
        nameToData.remove(module.name)
      }

      override fun modulesRenamed(project: Project, modules: MutableList<Module>, oldNameProvider: Function<Module, String>) {
        for (module in modules) {
          val oldName = oldNameProvider.`fun`(module)
          nameToData.get(oldName)?.let {
            nameToData.remove(oldName)
            nameToData.put(module.name, it)
          }
        }
      }
    })
  }

  override fun customizeStorageSpecs(component: PersistentStateComponent<*>, componentManager: ComponentManager, storages: List<Storage>, operation: StateStorageOperation): List<Storage>? {
    if (componentManager !is Module || component !is ProjectModelElement || !isEnabled()) {
      return null
    }

    if (operation == StateStorageOperation.WRITE) {
      // Keep in mind - this call will require storage for module because module option values are used.
      // We cannot just check that module name exists in the nameToData - new external system module will be not in the nameToData because not yet saved.
      @Suppress("INTERFACE_STATIC_METHOD_CALL_FROM_JAVA6_TARGET")
      if (ExternalProjectSystemRegistry.getInstance().getExternalSource(componentManager) == null) {
        return null
      }
    }
    else {
      // on read we cannot check because on import module is just created and not yet marked as external system module,
      // so, we just add our storage as first and default storages in the end as fallback
      val result = ArrayList<Storage>(storages.size + 1)
      result.add(EXTERNAL_STORAGE_ANNOTATION)
      result.addAll(storages)
      return result
    }

    // todo we can return on StateStorageOperation.WRITE default iml storage and then somehow using StateStorageChooserEx return Resolution.CLEAR to remove data from iml
    return listOf(EXTERNAL_STORAGE_ANNOTATION)
  }
}

private val MODULE_FILE_FORMAT_VERSION = 0

private fun createStorage(project: Project): PersistentHashMap<String, ByteArray> {
  val dir = ExternalProjectsDataStorage.getProjectConfigurationDir(project)
  val versionFile = dir.resolve("modules.version")
  val file = dir.resolve("modules")

  fun createMap() = PersistentHashMap<String, ByteArray>(file.toFile(), EnumeratorStringDescriptor.INSTANCE, object : DataExternalizer<ByteArray> {
    override fun read(`in`: DataInput): ByteArray {
      val available = (`in` as InputStream).available()
      val result = ByteArray(available)
      `in`.readFully(result)
      return result
    }

    override fun save(out: DataOutput, value: ByteArray) {
      out.write(value)
    }
  })

  fun deleteFileAndWriteLatestFormatVersion() {
    dir.deleteChildrenStartingWith(file.fileName.toString())
    file.delete()
    versionFile.outputStream().use { it.write(MODULE_FILE_FORMAT_VERSION) }

    StartupManager.getInstance(project).runWhenProjectIsInitialized {
      val externalProjectsManager = ServiceManager.getService(project, ExternalProjectsManager::class.java)
      externalProjectsManager.runWhenInitialized {
        externalProjectsManager.externalProjectsWatcher.markDirtyAllExternalProjects()
      }
    }
  }

  try {
    val fileVersion = versionFile.inputStreamIfExists()?.use { it.read() } ?: -1
    if (fileVersion != MODULE_FILE_FORMAT_VERSION) {
      deleteFileAndWriteLatestFormatVersion()
    }
    return createMap()
  }
  catch (e: IOException) {
    LOG.info(e)
    deleteFileAndWriteLatestFormatVersion()
  }
  return createMap()
}