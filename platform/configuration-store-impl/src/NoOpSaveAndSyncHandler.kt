// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * Trivial implementation used in tests and in the headless mode.
 */
private open class NoOpSaveAndSyncHandler : SaveAndSyncHandler() {
  override fun scheduleSave(task: SaveTask, forceExecuteImmediately: Boolean) {}

  override fun scheduleRefresh() {}

  override fun refreshOpenFiles() {}

  override fun blockSaveOnFrameDeactivation() {}

  override fun unblockSaveOnFrameDeactivation() {}

  override fun blockSyncOnFrameActivation() {}

  override fun unblockSyncOnFrameActivation() {}

  override fun maybeRefresh(modalityState: ModalityState) {}

  @RequiresEdt
  override fun saveSettingsUnderModalProgress(componentManager: ComponentManager): Boolean = true
}

private class HeadlessSaveAndSyncHandler : NoOpSaveAndSyncHandler() {
  override fun saveSettingsUnderModalProgress(componentManager: ComponentManager): Boolean {
    runInAutoSaveDisabledMode {
      runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
        componentManager.stateStore.save(forceSavingAllSettings = true)
      }
    }
    return true
  }
}