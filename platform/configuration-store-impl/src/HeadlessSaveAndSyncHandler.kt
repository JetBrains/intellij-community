// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.stateStore
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking

internal class HeadlessSaveAndSyncHandler : NoOpSaveAndSyncHandler() {
  override fun saveSettingsUnderModalProgress(componentManager: ComponentManager): Boolean {
    runInAutoSaveDisabledMode {
      runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
        componentManager.stateStore.save(forceSavingAllSettings = true)
      }
    }
    return true
  }
}