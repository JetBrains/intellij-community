// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.platform.settings.SettingsController
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicReference

@Internal
abstract class StateStorageBase<T : Any> : StateStorage {
  private var isSavingDisabled = false

  @JvmField
  protected val storageDataRef: AtomicReference<T> = AtomicReference()

  protected open val saveStorageDataOnReload: Boolean
    get() = true

  abstract val controller: SettingsController?

  final override fun <T : Any> getState(component: Any?, componentName: String, stateClass: Class<T>, mergeInto: T?, reload: Boolean): T? {
    val stateElement = getSerializedState(
      storageData = getStorageData(reload),
      component = component,
      componentName = componentName,
      archive = false,
    )
    return deserializeStateWithController(
      stateElement = stateElement,
      stateClass = stateClass,
      mergeInto = mergeInto,
      controller = controller,
      componentName = componentName,
    )
  }

  @TestOnly
  fun <T : Any> getState(component: Any?, componentName: String, stateClass: Class<T>): T? {
    return getState(component = component, componentName = componentName, stateClass = stateClass, mergeInto = null, reload = false)
  }

  fun getStorageData(): T = getStorageData(false)

  fun <S: Any> deserializeState(serializedState: Element?, stateClass: Class<S>, mergeInto: S?, componentName: String): S? {
    return deserializeStateWithController(
      stateElement = serializedState,
      stateClass = stateClass,
      mergeInto = mergeInto,
      controller = controller,
      componentName = componentName,
    )
  }

  abstract fun getSerializedState(storageData: T, component: Any?, componentName: String, archive: Boolean = true): Element?

  protected fun getStorageData(reload: Boolean = false): T {
    val currentStorageData = storageDataRef.get()
    if (currentStorageData != null && !reload) {
      return currentStorageData
    }

    val newStorageData = loadData()
    if (reload && !saveStorageDataOnReload) {
      // it means, that you MUST invoke save all settings after reload
      if (storageDataRef.compareAndSet(currentStorageData, null)) {
        return newStorageData
      }
      else {
        return getStorageData(reload = true)
      }
    }
    else if (storageDataRef.compareAndSet(currentStorageData, newStorageData)) {
      return newStorageData
    }
    else {
      return getStorageData(reload = false)
    }
  }

  protected abstract fun loadData(): T

  fun disableSaving() {
    LOG.debug { "Disable saving: ${toString()}" }
    isSavingDisabled = true
  }

  fun enableSaving() {
    LOG.debug { "Enable saving: ${toString()}" }
    isSavingDisabled = false
  }

  protected fun checkIsSavingDisabled(): Boolean {
    if (isSavingDisabled) {
      LOG.debug { "Saving disabled: ${toString()}" }
      return true
    }
    else {
      return false
    }
  }
}

internal inline fun <T> runBatchUpdate(project: Project, runnable: () -> T): T {
  val publisher = project.messageBus.syncPublisher(BatchUpdateListener.TOPIC)
  publisher.onBatchUpdateStarted()
  try {
    return runnable()
  }
  finally {
    publisher.onBatchUpdateFinished()
  }
}