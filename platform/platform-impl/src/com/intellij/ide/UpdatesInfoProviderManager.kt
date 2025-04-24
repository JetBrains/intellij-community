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

    private val EP = ExtensionPointName.create<UpdatesInfoProvider>("com.intellij.updatesInfoProvider")
  }

  val updateInfo: IdeUpdateInfo?
    get() = EP.extensionList.firstNotNullOfOrNull { it.updateInfo }

  val updateAvailable: Boolean
    get() = EP.extensionList.any { it.updateAvailable }

  fun runUpdate() {
    EP.extensionList
      .firstOrNull { it.updateAvailable }
      ?.let { it.runUpdate(it.updateInfo!!) }
  }
}