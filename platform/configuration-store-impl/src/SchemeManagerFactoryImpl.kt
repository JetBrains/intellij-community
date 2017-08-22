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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SettingsSavingComponent
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.options.Scheme
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.options.SchemeProcessor
import com.intellij.openapi.project.Project
import com.intellij.project.isDirectoryBased
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.lang.CompoundRuntimeException
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.nio.file.Paths

const val ROOT_CONFIG = "\$ROOT_CONFIG$"

sealed class SchemeManagerFactoryBase : SchemeManagerFactory(), SettingsSavingComponent {
  private val managers = ContainerUtil.createLockFreeCopyOnWriteList<SchemeManagerImpl<Scheme, out Scheme>>()

  protected open val componentManager: ComponentManager? = null

  override final fun <T : Scheme, MutableT : T> create(directoryName: String,
                                                       processor: SchemeProcessor<T, MutableT>,
                                                       presentableName: String?,
                                                       roamingType: RoamingType,
                                                       schemeNameToFileName: SchemeNameToFileName,
                                                       streamProvider: StreamProvider?,
                                                       directoryPath: Path?,
                                                       autoSave: Boolean): SchemeManager<T> {
    val path = checkPath(directoryName)
    val manager = SchemeManagerImpl(path,
                                    processor,
                                    streamProvider ?: (componentManager?.stateStore?.stateStorageManager as? StateStorageManagerImpl)?.compoundStreamProvider,
                                    directoryPath ?: pathToFile(path),
                                    roamingType,
                                    presentableName,
                                    schemeNameToFileName,
                                    componentManager?.messageBus)
    if (autoSave) {
      @Suppress("UNCHECKED_CAST")
      managers.add(manager as SchemeManagerImpl<Scheme, out Scheme>)
    }
    return manager
  }

  override fun dispose(schemeManager: SchemeManager<*>) {
    managers.remove(schemeManager)
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

  abstract fun pathToFile(path: String): Path

  fun process(processor: (SchemeManagerImpl<Scheme, out Scheme>) -> Unit) {
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

  @Suppress("unused")
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

    override fun pathToFile(path: String) = Paths.get(ApplicationManager.getApplication().stateStore.stateStorageManager.expandMacros(ROOT_CONFIG), path)!!
  }

  @Suppress("unused")
  private class ProjectSchemeManagerFactory(private val project: Project) : SchemeManagerFactoryBase() {
    override val componentManager = project

    override fun pathToFile(path: String) = Paths.get(project.basePath, if (project.isDirectoryBased) "${Project.DIRECTORY_STORE_FOLDER}/$path" else ".$path")!!
  }

  @TestOnly
  class TestSchemeManagerFactory(private val basePath: Path) : SchemeManagerFactoryBase() {
    override fun pathToFile(path: String) = basePath.resolve(path)!!
  }
}