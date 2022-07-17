// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.configurationStore

import com.intellij.ProjectTopics
import com.intellij.configurationStore.FileStorageAnnotation
import com.intellij.configurationStore.StateStorageManager
import com.intellij.configurationStore.StreamProviderFactory
import com.intellij.openapi.components.*
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.ProjectModelElement
import com.intellij.openapi.startup.StartupManager
import com.intellij.util.Function
import org.jdom.Element
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

// todo handle module rename
internal class ExternalSystemStreamProviderFactory(private val project: Project) : StreamProviderFactory {
  val moduleStorage by lazy { ModuleFileSystemExternalSystemStorage(project) }
  val fileStorage by lazy { ProjectFileSystemExternalSystemStorage(project) }

  private val isReimportOnMissedExternalStorageScheduled = AtomicBoolean(false)

  private val storageSpecLock = ReentrantReadWriteLock()
  private val storages = HashMap<String, Storage>()

  init {
    project.messageBus.connect().subscribe(ProjectTopics.MODULES, object : ModuleListener {
      override fun moduleRemoved(project: Project, module: Module) {
        moduleStorage.remove(module.name)
      }

      override fun modulesRenamed(project: Project, modules: List<Module>, oldNameProvider: Function<in Module, String>) {
        for (module in modules) {
          moduleStorage.rename(oldNameProvider.`fun`(module), module.name)
        }
      }
    })
  }

  override fun customizeStorageSpecs(component: PersistentStateComponent<*>, storageManager: StateStorageManager, stateSpec: State, storages: List<Storage>, operation: StateStorageOperation): List<Storage>? {
    val componentManager = storageManager.componentManager ?: return null
    val project = componentManager as? Project ?: (componentManager as Module).project

    if (storages.size == 1) {
      val storage = storages.first()
      // do not customize project file storage spec, see ProjectStoreBase.PROJECT_FILE_STORAGE_ANNOTATION
      if (storage is FileStorageAnnotation && storage.value == "\$PROJECT_FILE$" && !storage.deprecated) {
        return null
      }
      // we store isExternalStorageEnabled option in the project workspace file, so, for such components external storage is always disabled and not applicable
      if (storage.value == StoragePathMacros.WORKSPACE_FILE || !project.isExternalStorageEnabled) {
        return null
      }
    }

    if (componentManager is Project) {
      val fileSpec = storages.firstOrNull()?.value
      if (fileSpec == "libraries" || fileSpec == "artifacts") {
        val externalStorageSpec = getOrCreateStorageSpec("$fileSpec.xml", stateSpec)
        if (operation == StateStorageOperation.READ) {
          return listOf(externalStorageSpec)
        }

        // write is separated, state is written to both storages and filtered by on serialization
        val result = ArrayList<Storage>(storages.size + 1)
        result.add(externalStorageSpec)
        result.addAll(storages)
        return result
      }
    }

    if (component !is ProjectModelElement) {
      return null
    }
    val externalStorageOnly = stateSpec.externalStorageOnly

    // Keep in mind - this call will require storage for module because module option values are used.
    // We cannot just check that module name exists in the nameToData - new external system module will be not in the nameToData because not yet saved.
    if (operation == StateStorageOperation.WRITE && component.externalSource == null && !externalStorageOnly) {
      return null
    }

    // on read we cannot check because on import module is just created and not yet marked as external system module,
    // so, we just add our storage as first and default storages in the end as fallback

    // on write default storages also returned, because default FileBasedStorage will remove data if component has external source
    val annotation: Storage
    if (componentManager is Project) {
      annotation = getOrCreateStorageSpec(storages.get(0).value)
    }
    else {
      annotation = getOrCreateStorageSpec(StoragePathMacros.MODULE_FILE)
    }

    if (externalStorageOnly) {
      return listOf(annotation)
    }

    val result = ArrayList<Storage>(storages.size + 1)
    result.add(annotation)
    result.addAll(storages)
    return result
  }

  override fun getOrCreateStorageSpec(fileSpec: String, inProjectStateSpec: State?): Storage {
    return storageSpecLock.read { storages.get(fileSpec) } ?: return storageSpecLock.write {
      storages.computeIfAbsent(fileSpec) { ExternalStorageSpec(fileSpec, inProjectStateSpec) }
    }
  }

  fun readModuleData(name: String): Element? {
    if (!moduleStorage.hasSomeData &&
        isReimportOnMissedExternalStorageScheduled.compareAndSet(false, true) &&
        !project.isInitialized &&
        !ExternalSystemUtil.isNewProject(project)) {
      StartupManager.getInstance(project).runWhenProjectIsInitialized {
        val externalProjectsManager = ExternalProjectsManager.getInstance(project)
        externalProjectsManager.runWhenInitialized {
          if (!ExternalSystemUtil.isNewProject(project)) {
            externalProjectsManager.externalProjectsWatcher.markDirtyAllExternalProjects()
          }
        }
      }
    }

    val result = moduleStorage.read(name)
    if (result == null) {
      // todo we must detect situation when module was really stored but file was somehow deleted by user / corrupted
      // now we use file-based storage, and, so, not easy to detect this situation on start (because all modules data stored not in the one file, but per file)
    }
    return result
  }
}