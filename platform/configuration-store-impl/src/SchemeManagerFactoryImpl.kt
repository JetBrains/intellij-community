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

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SettingsSavingComponent
import com.intellij.openapi.components.impl.stores.StateStorageManager
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.options.*
import com.intellij.openapi.project.Project
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.lang.CompoundRuntimeException
import java.nio.file.Path
import java.nio.file.Paths

const val ROOT_CONFIG = "\$ROOT_CONFIG$"

sealed class SchemeManagerFactoryBase : SchemesManagerFactory(), SettingsSavingComponent {
  private val managers = ContainerUtil.createLockFreeCopyOnWriteList<SchemeManagerImpl<Scheme, ExternalizableScheme>>()

  abstract val componentManager: ComponentManager

  override final fun <T : Scheme, E : ExternalizableScheme> create(directoryName: String, processor: SchemeProcessor<E>, roamingType: RoamingType, presentableName: String?): SchemesManager<T, E> {
    val storageManager = (componentManager.stateStore).stateStorageManager

    val path = checkPath(directoryName)
    val manager = SchemeManagerImpl<T, E>(path, processor, (storageManager as? StateStorageManagerImpl)?.streamProvider, pathToFile(path, storageManager), roamingType, componentManager, presentableName)
    @Suppress("CAST_NEVER_SUCCEEDS")
    managers.add(manager as SchemeManagerImpl<Scheme, ExternalizableScheme>)
    return manager
  }

  open fun checkPath(originalPath: String): String {
    fun error(message: String) {
      // as error because it is not a new requirement
      if (ApplicationManager.getApplication().isUnitTestMode) throw AssertionError(message) else LOG.error(message)
    }

    when {
      originalPath.contains('\\') -> error("Path must be system-independent, use forward slash instead of backslash")
      originalPath.isEmpty() -> error("Path must not be empty")
    }
    return originalPath
  }

  abstract fun pathToFile(path: String, storageManager: StateStorageManager): Path

  fun process(processor: (SchemeManagerImpl<Scheme, ExternalizableScheme>) -> Unit) {
    for (manager in managers) {
      try {
        processor(manager)
      }
      catch (e: Throwable) {
        LOG.error("Cannot reload settings for ${manager.javaClass.name}", e)
      }
    }
  }

  override final fun save() {
    val errors = SmartList<Throwable>()
    for (registeredManager in managers) {
      try {
        registeredManager.save(errors)
      }
      catch (e: Throwable) {
        errors.add(e)
      }
    }

    CompoundRuntimeException.throwIfNotEmpty(errors)
  }

  private class ApplicationSchemeManagerFactory : SchemeManagerFactoryBase() {
    override val componentManager: ComponentManager
      get() = ApplicationManager.getApplication()

    override fun checkPath(originalPath: String): String {
      var path = super.checkPath(originalPath)
      if (path.startsWith(ROOT_CONFIG)) {
        path = path.substring(ROOT_CONFIG.length + 1)
        val message = "Path must not contains ROOT_CONFIG macro, corrected: $path"
        if (ApplicationManager.getApplication().isUnitTestMode) throw AssertionError(message) else LOG.warn(message)
      }
      return path
    }

    override fun pathToFile(path: String, storageManager: StateStorageManager) = Paths.get(storageManager.expandMacros(ROOT_CONFIG), path)
  }

  private class ProjectSchemeManagerFactory(private val project: Project) : SchemeManagerFactoryBase() {
    override val componentManager = project

    override fun pathToFile(path: String, storageManager: StateStorageManager) = Paths.get(project.basePath, if (ProjectUtil.isDirectoryBased(project)) "${Project.DIRECTORY_STORE_FOLDER}/$path" else ".$path")
  }
}