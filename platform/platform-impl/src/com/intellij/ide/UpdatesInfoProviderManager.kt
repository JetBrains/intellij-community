// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.launch
import java.util.function.Supplier

@OptIn(ExperimentalCoroutinesApi::class)
@Service(Service.Level.APP)
class UpdatesInfoProviderManager(coroutineScope: CoroutineScope) {

  companion object {
    @JvmStatic
    fun getInstance(): UpdatesInfoProviderManager = service<UpdatesInfoProviderManager>()

    private val EP = ExtensionPointName.create<ExternalUpdateProvider>("com.intellij.updatesInfoProvider")
  }

  init {
    //listen for restart requests
    coroutineScope.launch {
      EP.extensionList.asFlow()
        .flatMapMerge { it.updatesStateFlow }
        .filter { it == ExternalUpdateState.RestartNeeded }
        .collect { requestShutdown() }
    }
  }

  val availableUpdate: IdeUpdateInfo?
    get() = EP.extensionList.firstNotNullOfOrNull { (it.updatesState as? ExternalUpdateState.UpdateAvailable)?.info }

  val updateInProcess: Boolean
    get() = EP.extensionList.any { it.updatesState is ExternalUpdateState.Preparing }

  fun runUpdate() {
    EP.extensionList.firstOrNull { it.updatesState is ExternalUpdateState.UpdateAvailable }?.runUpdate()
  }

  fun createUpdateAction(): AnAction? {
    if (availableUpdate == null) return null
    return RunUpdateAction(this)
  }

  private fun requestShutdown() {
    TODO("Not yet implemented")
  }
}

private class RunUpdateAction(
  private val manager: UpdatesInfoProviderManager,
): AnAction(
  Supplier { ActionsBundle.message("action.UpdateIDEWithStation.text", manager.availableUpdate?.fullName) },
  AllIcons.Ide.Notification.IdeUpdate,
) {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    manager.runUpdate()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = manager.availableUpdate != null
  }
}
