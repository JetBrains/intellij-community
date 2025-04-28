// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.swing.Icon

@OptIn(ExperimentalCoroutinesApi::class)
@Service(Service.Level.APP)
class UpdatesInfoProviderManager(coroutineScope: CoroutineScope) {

  companion object {
    @JvmStatic
    fun getInstance(): UpdatesInfoProviderManager = service<UpdatesInfoProviderManager>()

    private val EP = ExtensionPointName.create<ExternalUpdateProvider>("com.intellij.updatesInfoProvider")
  }

  val availableUpdate: IdeUpdateInfo?
    get() = EP.extensionList.firstNotNullOfOrNull { (it.updatesState as? ExternalUpdateState.Available)?.info }

  val updateInProcess: Boolean
    get() = EP.extensionList.any { it.updatesState is ExternalUpdateState.Downloading }

  fun runUpdate() {
    EP.extensionList.firstOrNull { it.updatesState is ExternalUpdateState.Available }?.runUpdate()
  }

  fun getUpdateActions(): List<AnAction> {
    return EP.extensionList.flatMap { it.updatesState.info?.let { info -> listOf(info.action) } ?: emptyList() }
  }

  fun getUpdateIcons(): List<Icon> {
    return EP.extensionList.flatMap { it.updatesState.info?.let { info -> listOf(info.icon) } ?: emptyList() }
  }
}