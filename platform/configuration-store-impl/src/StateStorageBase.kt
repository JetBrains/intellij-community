// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.settings.SettingsController
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
abstract class StateStorageBase<T : Any> : StateStorage {
  private var isSavingDisabled = false

  @JvmField
  protected val storageDataRef: AtomicReference<T> = AtomicReference()

  protected open val saveStorageDataOnReload: Boolean
    get() = true

  abstract val controller: SettingsController?

  final override fun <T : Any> getState(
    component: Any?,
    componentName: String,
    pluginId: PluginId,
    stateClass: Class<T>,
    mergeInto: T?,
    reload: Boolean,
  ): T? {
    val stateElement = getSerializedState(
      storageData = getStorageData(reload = reload),
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
      pluginId = pluginId,
    )
  }

  @ApiStatus.Internal
  fun getStorageData(): T = getStorageData(reload = false)

  abstract fun getSerializedState(storageData: T, component: Any?, componentName: String, archive: Boolean): Element?

  protected fun getStorageData(reload: Boolean): T {
    val currentStorageData = storageDataRef.get()
    if (currentStorageData != null && !reload) {
      return currentStorageData
    }

    val newStorageData = loadData()
    if (reload && !saveStorageDataOnReload) {
      // it means that you MUST invoke "save all settings" after reload
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
