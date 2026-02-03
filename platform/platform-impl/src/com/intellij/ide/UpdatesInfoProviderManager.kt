// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@OptIn(ExperimentalCoroutinesApi::class)
@Service(Service.Level.APP)
@ApiStatus.Internal
class UpdatesInfoProviderManager(coroutineScope: CoroutineScope) {

  companion object {
    @JvmStatic
    fun getInstance(): UpdatesInfoProviderManager = service<UpdatesInfoProviderManager>()

    private val EP = ExtensionPointName.create<ExternalUpdateProvider>("com.intellij.updatesInfoProvider")
  }

  fun getUpdateActions(): List<AnAction> {
    return providers.flatMap { it.updatesState.info?.let { info -> listOf(info.action) } ?: emptyList() }
  }

  fun getUpdateIcons(): List<Icon> {
    return providers.flatMap { it.updatesState.info?.let { info -> listOf(info.icon) } ?: emptyList() }
  }

  private val providers: List<ExternalUpdateProvider>
    get() = if (Registry.`is`("station.use.station.comms.tools.management")) EP.extensionList else emptyList()
}