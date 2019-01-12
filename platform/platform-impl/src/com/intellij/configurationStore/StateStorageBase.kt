// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.BatchUpdateListener
import com.intellij.openapi.diagnostic.debugOrInfoIfTestMode
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.MessageBus
import org.jdom.Element
import java.util.concurrent.atomic.AtomicReference

private val LOG = logger<StateStorageBase<*>>()

abstract class StateStorageBase<T : Any> : StateStorage {
  private var isSavingDisabled = false

  protected val storageDataRef: AtomicReference<T> = AtomicReference()

  override fun <T : Any> getState(component: Any?, componentName: String, stateClass: Class<T>, mergeInto: T?, reload: Boolean): T? {
    return getState(component, componentName, stateClass, reload, mergeInto)
  }

  fun <T : Any> getState(component: Any?, componentName: String, stateClass: Class<T>, reload: Boolean = false, mergeInto: T? = null): T? {
    return deserializeState(getSerializedState(getStorageData(reload), component, componentName, archive = true), stateClass, mergeInto)
  }

  open fun <S: Any> deserializeState(serializedState: Element?, stateClass: Class<S>, mergeInto: S?): S? {
    return com.intellij.configurationStore.deserializeState(serializedState, stateClass, mergeInto)
  }

  abstract fun getSerializedState(storageData: T, component: Any?, componentName: String, archive: Boolean = true): Element?

  protected abstract fun hasState(storageData: T, componentName: String): Boolean

  final override fun hasState(componentName: String, reloadData: Boolean): Boolean {
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
    LOG.debugOrInfoIfTestMode { "Disable saving: ${toString()}" }
    isSavingDisabled = true
  }

  fun enableSaving() {
    LOG.debugOrInfoIfTestMode { "Enable saving: ${toString()}" }
    isSavingDisabled = false
  }

  protected fun checkIsSavingDisabled(): Boolean {
    if (isSavingDisabled) {
      LOG.debugOrInfoIfTestMode { "Saving disabled: ${toString()}" }
      return true
    }
    else {
      return false
    }
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

class UnresolvedReadOnlyFilesException(val files: List<VirtualFile>) : RuntimeException()