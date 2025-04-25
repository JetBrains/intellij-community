// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
class UpdatesInfoProviderManager {
  companion object {
    @JvmStatic
    fun getInstance(): UpdatesInfoProviderManager = service<UpdatesInfoProviderManager>()

    private val EP = ExtensionPointName.create<ExternalUpdateProvider>("com.intellij.updatesInfoProvider")
  }

  val availableUpdate: IdeUpdateInfo?
    get() = EP.extensionList.firstNotNullOfOrNull { (it.updatesState as? ExternalUpdateState.UpdateAvailable)?.info }

  val updateInProcess: Boolean
    get() = EP.extensionList.any { it.updatesState is ExternalUpdateState.Preparing }

  fun runUpdate() {
    EP.extensionList
      .firstOrNull { it.updatesState is ExternalUpdateState.UpdateAvailable }
      ?.let {
        (it.updatesState as? ExternalUpdateState.UpdateAvailable)?.info?.let { info ->
          it.runUpdate(info)
        }
      }
  }
}