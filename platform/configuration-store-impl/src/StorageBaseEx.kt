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
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.isEmpty
import org.jdom.Element

abstract class StorageBaseEx<T : Any> : StateStorageBase<T>() {
  fun <S : Any> createGetSession(component: PersistentStateComponent<S>, componentName: String, stateClass: Class<S>, reload: Boolean = false): StateGetter<S> {
    return StateGetterImpl(component, componentName, getStorageData(reload), stateClass, this)
  }

  /**
   * serializedState is null if state equals to default (see XmlSerializer.serializeIfNotDefault)
   */
  abstract fun archiveState(storageData: T, componentName: String, serializedState: Element?)
}

fun <S : Any> createStateGetter(isUseLoadedStateAsExisting: Boolean, storage: StateStorage, component: PersistentStateComponent<S>, componentName: String, stateClass: Class<S>, reloadData: Boolean): StateGetter<S> {
  if (isUseLoadedStateAsExisting && storage is StorageBaseEx<*>) {
    return storage.createGetSession(component, componentName, stateClass, reloadData)
  }

  return object : StateGetter<S> {
    override fun getState(mergeInto: S?): S? {
      return storage.getState(component, componentName, stateClass, mergeInto, reloadData)
    }

    override fun close() {
    }
  }
}

interface StateGetter<S : Any> {
  fun getState(mergeInto: S? = null): S?

  fun close()
}

private class StateGetterImpl<S : Any, T : Any>(private val component: PersistentStateComponent<S>,
                                                private val componentName: String,
                                                private val storageData: T,
                                                private val stateClass: Class<S>,
                                                private val storage: StorageBaseEx<T>) : StateGetter<S> {
  private var serializedState: Element? = null

  override fun getState(mergeInto: S?): S? {
    LOG.assertTrue(serializedState == null)

    serializedState = storage.getSerializedState(storageData, component, componentName, archive = false)
    return storage.deserializeState(serializedState, stateClass, mergeInto)
  }

  override fun close() {
    if (serializedState == null) {
      return
    }

    val stateAfterLoad = try {
      component.state
    }
    catch (e: Throwable) {
      LOG.error("Cannot get state after load", e)
      null
    }

    val serializedStateAfterLoad = if (stateAfterLoad == null) {
      serializedState
    }
    else {
      serializeState(stateAfterLoad)?.normalizeRootName().let {
        if (it.isEmpty()) null else it
      }
    }

    if (ApplicationManager.getApplication().isUnitTestMode &&
      serializedState != serializedStateAfterLoad &&
      (serializedStateAfterLoad == null || !JDOMUtil.areElementsEqual(serializedState, serializedStateAfterLoad))) {
      LOG.warn("$componentName (from ${component.javaClass.name}) state changed after load. \nOld: ${JDOMUtil.writeElement(serializedState!!)}\n\nNew: ${serializedStateAfterLoad?.let { JDOMUtil.writeElement(it) } ?: "null"}\n")
    }

    storage.archiveState(storageData, componentName, serializedStateAfterLoad)
  }
}