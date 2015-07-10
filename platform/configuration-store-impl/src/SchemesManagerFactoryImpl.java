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

import kotlin.suppress;

private val LOG = Logger.getInstance(javaClass<SchemesManagerFactoryImpl>())

public class SchemesManagerFactoryImpl : SchemesManagerFactory(), SettingsSavingComponent {
  private val myRegisteredManagers = ContainerUtil.createLockFreeCopyOnWriteList<SchemeManagerImpl<Scheme, ExternalizableScheme>>()

  override fun <T : Scheme, E : ExternalizableScheme> createSchemesManager(fileSpec: String, processor: SchemeProcessor<E>, roamingType: RoamingType): SchemesManager<T, E> {
    val storageManager = (ApplicationManager.getApplication() as ApplicationImpl).getStateStore().getStateStorageManager()
    val baseDirPath = storageManager.expandMacros(fileSpec)
    val provider = storageManager.getStreamProvider()
    val manager = SchemeManagerImpl<T, E>(fileSpec, processor, roamingType, provider, File(baseDirPath))
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
