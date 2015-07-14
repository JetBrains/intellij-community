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
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SettingsSavingComponent
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.*
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.lang.CompoundRuntimeException
import java.io.File

private val LOG = Logger.getInstance(javaClass<SchemeManagerFactoryImpl>())

public class SchemeManagerFactoryImpl : SchemesManagerFactory(), SettingsSavingComponent {
  private val myRegisteredManagers = ContainerUtil.createLockFreeCopyOnWriteList<SchemeManagerImpl<Scheme, ExternalizableScheme>>()

  override fun <T : Scheme, E : ExternalizableScheme> createSchemesManager(fileSpec: String, processor: SchemeProcessor<E>, roamingType: RoamingType): SchemesManager<T, E> {
    val storageManager = (ApplicationManager.getApplication().getPicoContainer().getComponentInstance(javaClass<IComponentStore>()) as IComponentStore).getStateStorageManager()

    val absoluteFileSpec = if (fileSpec.startsWith('$')) fileSpec else "${StoragePathMacros.ROOT_CONFIG}/$fileSpec"

    val manager = SchemeManagerImpl<T, E>(absoluteFileSpec, processor, roamingType, storageManager.getStreamProvider(), File(storageManager.expandMacros(absoluteFileSpec)))
    @suppress("CAST_NEVER_SUCCEEDS")
    myRegisteredManagers.add(manager as SchemeManagerImpl<Scheme, ExternalizableScheme>)
    return manager
  }

  public fun process(processor: (SchemeManagerImpl<Scheme, ExternalizableScheme>) -> Unit) {
    for (manager in myRegisteredManagers) {
      try {
        processor(manager)
      }
      catch (e: Throwable) {
        LOG.error("Cannot reload settings for ${manager.javaClass.getName()}", e)
      }
    }
  }

  override fun save() {
    val errors = SmartList<Throwable>()
    for (registeredManager in myRegisteredManagers) {
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
