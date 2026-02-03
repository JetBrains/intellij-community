// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView.frontend

import com.intellij.ide.IdeBundle
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.execution.serviceView.getServiceViewRegistryFlagsState
import com.intellij.platform.execution.serviceView.isCurrentProductSupportSplitServiceView
import com.intellij.platform.execution.serviceView.setServiceViewImplementationForNextIdeRun
import com.intellij.platform.execution.serviceView.splitApi.ServiceViewRpc
import com.intellij.platform.ide.productMode.IdeProductMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SwitchServiceViewImplementationAction : DumbAwareToggleAction(), ActionRemoteBehaviorSpecification.Frontend {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = IdeProductMode.isFrontend && isCurrentProductSupportSplitServiceView()
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return Registry.`is`("services.view.split.enabled")
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    e.coroutineScope.launch {
      val app = ApplicationManagerEx.getApplicationEx()
      val restartAllowed = withContext(Dispatchers.EDT) {
        if (app == null) {
          thisLogger().warn("Application is null, abort restart")
          return@withContext false
        }

        val affectedRegistryFlagsPatch = getServiceViewRegistryFlagsState().entries
          .joinToString(prefix = "\n", separator = ",\n", postfix = "\n") { (key, value) ->
            val patchedValueToOffer = when {
              key.startsWith("xdebugger") -> value
              else -> !value
            }
            "- $key = $patchedValueToOffer"
          }
        thisLogger().warn("Service View registry flags patch to apply:\n$affectedRegistryFlagsPatch")

        val detailedChangeInfo = ActionsBundle.message("action.ServiceView.SwitchImplementation.restart.confirmation.text", affectedRegistryFlagsPatch)
        val standardRestartDialogText = IdeBundle.message(if (app.isRestartCapable()) "dialog.message.restart.ide" else "dialog.message.restart.alt")
        val answer = Messages.showYesNoDialog(
          detailedChangeInfo + standardRestartDialogText,
          IdeBundle.message("dialog.title.restart.ide"),
          IdeBundle.message(if (app.isRestartCapable()) "ide.restart.action" else "ide.shutdown.action"),
          ActionsBundle.message("action.ServiceView.SwitchImplementation.restart.cancellation.text"),
          Messages.getQuestionIcon()
        )

        return@withContext answer == Messages.YES
      }
      if (!restartAllowed) return@launch

      setServiceViewImplementationForNextIdeRun(state)
      ServiceViewRpc.getInstance().changeServiceViewImplementationForNextIdeRunAndRestart(state)
    }
  }
}