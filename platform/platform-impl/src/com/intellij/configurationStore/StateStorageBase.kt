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
package com.intellij.configurationStore

import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.BatchUpdateListener
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.messages.MessageBus
import org.jdom.Element
import java.util.concurrent.atomic.AtomicReference

private val LOG = logger<StateStorageBase<*>>()

abstract class StateStorageBase<T : Any> : StateStorage {
  private var mySavingDisabled = false

  protected val storageDataRef: AtomicReference<T> = AtomicReference()

  override final fun <S : Any> getState(component: Any?, componentName: String, stateClass: Class<S>, mergeInto: S?, reload: Boolean): S? {
    return getState(component, componentName, stateClass, reload, mergeInto)
  }

  fun <S: Any> getState(component: Any?,
                        componentName: String,
                        stateClass: Class<S>,
                        reload: Boolean = false,
                        mergeInto: S? = null): S? {
    return deserializeState(getSerializedState(getStorageData(reload), component, componentName, true), stateClass, mergeInto)
  }

  open fun <S: Any> deserializeState(serializedState: Element?, stateClass: Class<S>, mergeInto: S?): S? {
    return com.intellij.configurationStore.deserializeState(serializedState, stateClass, mergeInto)
  }

  abstract fun getSerializedState(storageData: T, component: Any?, componentName: String, archive: Boolean = true): Element?

  protected abstract fun hasState(storageData: T, componentName: String): Boolean

  override final fun hasState(componentName: String, reloadData: Boolean): Boolean {
    return hasState(getStorageData(reloadData), componentName)
  }

  protected fun getStorageData(reload: Boolean = false): T {
    val storageData = storageDataRef.get()
    if (storageData != null && !reload) {
      return storageData
    }

    val newStorageData = loadData()
    if (storageDataRef.compareAndSet(storageData, newStorageData)) {
      return newStorageData
    }
    else {
      return getStorageData(false)
    }
  }

  protected abstract fun loadData(): T

  fun disableSaving() {
    LOG.debug { "Disabled saving for ${toString()}" }
    mySavingDisabled = true
  }

  fun enableSaving() {
    LOG.debug { "Enabled saving ${toString()}" }
    mySavingDisabled = false
  }

  protected fun checkIsSavingDisabled(): Boolean {
    LOG.debug { "Saving disabled for ${toString()}" }
    return mySavingDisabled
  }
}

inline fun <T> runBatchUpdate(messageBus: MessageBus, runnable: () -> T): T {
  val publisher = messageBus.syncPublisher(BatchUpdateListener.TOPIC)
  publisher.onBatchUpdateStarted()
  try {
    return runnable()
  }
  finally {
    publisher.onBatchUpdateFinished()
  }
}