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

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.impl.stores.StateStorageBase
import org.jdom.Element

abstract class StorageBaseEx<T : Any> : StateStorageBase<T>() {
  fun <S : Any> createGetSession(component: PersistentStateComponent<S>, componentName: String, stateClass: Class<S>, reload: Boolean = false) = StateGetter(component, componentName, getStorageData(reload), stateClass, this)

  /**
   * serializedState is null if state equals to default (see XmlSerializer.serializeIfNotDefault)
   */
  abstract fun archiveState(storageData: T, componentName: String, serializedState: Element?)
}

class StateGetter<S : Any, T : Any>(private val component: PersistentStateComponent<S>,
                                    private val componentName: String,
                                    private val storageData: T,
                                    private val stateClass: Class<S>,
                                    private val storage: StorageBaseEx<T>) {
  var serializedState: Element? = null

  fun getState(mergeInto: S? = null): S? {
    LOG.assertTrue(serializedState == null)

    serializedState = storage.getSerializedState(storageData, component, componentName, false)
    if (serializedState != null) {
      //System.out.println("open $componentName to read state, ${hashCode()} $storage, ${Thread.currentThread()}")
    }
    return storage.deserializeState(serializedState, stateClass, mergeInto)
  }

  fun close() {
    if (serializedState == null) {
      return
    }

    //System.out.println("close $componentName to read state, ${hashCode()} $storage, ${Thread.currentThread()}")

    val stateAfterLoad: S?
    try {
      stateAfterLoad = component.state
    }
    catch (e: Throwable) {
      LOG.error("Cannot get state after load", e)
      stateAfterLoad = null
    }

    val serializedStateAfterLoad = if (stateAfterLoad == null) {
      serializedState
    }
    else {
      serializeState(stateAfterLoad)?.normalizeRootName()
    }

    storage.archiveState(storageData, componentName, serializedStateAfterLoad)
  }
}