// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.ComponentManager

internal class HeadlessSaveAndSyncHandler : NoOpSaveAndSyncHandler() {
  override fun saveSettingsUnderModalProgress(componentManager: ComponentManager): Boolean =
    StoreUtil.saveComponentManagerSettings(componentManager, true)
}