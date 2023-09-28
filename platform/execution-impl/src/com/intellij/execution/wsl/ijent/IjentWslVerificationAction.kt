// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent

import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.progress.ModalTaskOwner
import com.intellij.openapi.progress.TaskCancellation
import com.intellij.openapi.progress.withModalProgress
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentMissingBinary
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import java.io.ByteArrayOutputStream

@Suppress("DialogTitleCapitalization", "HardCodedStringLiteral")
class IjentWslVerificationAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = ApplicationManager.getApplication().isInternal
  }

  @OptIn(DelicateCoroutinesApi::class)  // Doesn't matter for a trivial test utility.
  override fun actionPerformed(e: AnActionEvent) {
    val modalTaskOwner =
      e.project?.let(ModalTaskOwner::project)
      ?: PlatformDataKeys.CONTEXT_COMPONENT.getData(e.dataContext)?.let(ModalTaskOwner::component)
      ?: error("No ModalTaskOwner")
    GlobalScope.launch {
      LOG.runAndLogException {
        try {
          withModalProgress(modalTaskOwner, e.presentation.text, TaskCancellation.cancellable()) {
            val wslDistribution = WslDistributionManager.getInstance().installedDistributions.first()

            coroutineScope {
              val ijent = deployAndLaunchIjent(
                ijentCoroutineScope = childScope(),
                project = null,
                wslDistribution = wslDistribution,
              )
              val process = when (val p = ijent.executeProcess("uname", "-a")) {
                is IjentApi.ExecuteProcessResult.Failure -> error(p)
                is IjentApi.ExecuteProcessResult.Success -> p.process
              }
              val stdout = ByteArrayOutputStream()
              process.stdout.consumeEach(stdout::write)
              withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                Messages.showInfoMessage(stdout.toString(), "IJent on $wslDistribution: uname -a")
              }
              coroutineContext.cancelChildren()
            }
          }
        }
        catch (err: IjentMissingBinary) {
          GlobalScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            Messages.showErrorDialog(err.localizedMessage, "IJent error")
          }
        }
      }
    }
  }

  companion object {
    private val LOG = logger<IjentWslVerificationAction>()
  }
}