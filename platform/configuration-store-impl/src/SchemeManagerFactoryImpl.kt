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
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SettingsSavingComponent
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.StateStorageManager
import com.intellij.openapi.options.*
import com.intellij.openapi.project.Project
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.lang.CompoundRuntimeException
import org.picocontainer.PicoContainer
import java.io.File

public abstract class SchemeManagerFactoryBase : SchemesManagerFactory(), SettingsSavingComponent {
  private val managers = ContainerUtil.createLockFreeCopyOnWriteList<SchemeManagerImpl<Scheme, ExternalizableScheme>>()

  abstract val picoContainer: PicoContainer

  override final fun <T : Scheme, E : ExternalizableScheme> createSchemesManager(directoryName: String, processor: SchemeProcessor<E>, roamingType: RoamingType): SchemesManager<T, E> {
    val storageManager = (picoContainer.getComponentInstance(javaClass<IComponentStore>()) as IComponentStore).getStateStorageManager()

    val path = normalizeDirectoryName(directoryName)
    val manager = SchemeManagerImpl<T, E>(path, processor, roamingType, storageManager.getStreamProvider(), pathToFile(path, storageManager))
    @suppress("CAST_NEVER_SUCCEEDS")
    managers.add(manager as SchemeManagerImpl<Scheme, ExternalizableScheme>)
    return manager
  }

  open fun normalizeDirectoryName(directoryName: String) = directoryName

  abstract fun pathToFile(path: String, storageManager: StateStorageManager): File

  public fun process(processor: (SchemeManagerImpl<Scheme, ExternalizableScheme>) -> Unit) {
    for (manager in managers) {
      try {
        processor(manager)
      }
      catch (e: Throwable) {
        LOG.error("Cannot reload settings for ${manager.javaClass.getName()}", e)
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

    CompoundRuntimeException.doThrow(errors)
  }
}

private class ApplicationSchemeManagerFactory : SchemeManagerFactoryBase() {
  override val picoContainer: PicoContainer
    get() = ApplicationManager.getApplication().getPicoContainer()

  override fun normalizeDirectoryName(directoryName: String) = if (directoryName.startsWith('$')) directoryName else "${StoragePathMacros.ROOT_CONFIG}/$directoryName"

  override fun pathToFile(path: String, storageManager: StateStorageManager) = File(storageManager.expandMacros(path))
}

private class ProjectSchemeManagerFactory(private val project: Project) : SchemeManagerFactoryBase() {
  override val picoContainer: PicoContainer
    get() = project.getPicoContainer()

  override fun pathToFile(path: String, storageManager: StateStorageManager) = File(project.getBasePath(), if (ProjectUtil.isDirectoryBased(project)) "${Project.DIRECTORY_STORE_FOLDER}/$path" else ".$path")
}