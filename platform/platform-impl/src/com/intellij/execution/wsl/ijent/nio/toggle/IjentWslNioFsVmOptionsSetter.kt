// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ijent.nio.toggle

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.AppMode
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.IdeFrame
import kotlinx.coroutines.*

internal class IjentWslNioFsVmOptionsSetter : ApplicationActivationListener {
  override fun applicationActivated(ideFrame: IdeFrame) {
    val service = service<IjentWslNioFsToggler>()
    service.coroutineScope.launch {
      val changedOptions = service.ensureInVmOptions()
      when {
        changedOptions.isEmpty() -> Unit

        PluginManagerCore.isRunningFromSources() || AppMode.isDevServer() ->
          launch(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
            @Suppress("HardCodedStringLiteral")  // Not sure if Dev Mode requires i18n
            val doThat = Messages.OK == Messages.showOkCancelDialog(
              null,
              changedOptions.joinToString(
                prefix = "This message is seen only in Dev Mode/Run from sources.<br/><br/>" +
                         "The value of the registry flag for IJent FS doesn't match system properties.<br/>" +
                         "Add the following VM options to the Dev Mode Run Configuration:<br/><pre>",
                separator = "<br/>",
                postfix = "</pre>",
              ) { (k, v) ->
                "$k${v.orEmpty()}"
              },
              "IJent VM Options",
              if (ApplicationManager.getApplication().isRestartCapable) "Restart" else "Shutdown",
              "Cancel",
              AllIcons.General.Warning,
            )
            if (doThat) {
              ApplicationManagerEx.getApplicationEx().restart(true)
            }
          }

        else -> launch(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
          val doThat = Messages.OK == Messages.showOkCancelDialog(
            null,
            IdeBundle.message("ijent.wsl.fs.dialog.message"),
            IdeBundle.message("ijent.wsl.fs.dialog.title"),
            IdeBundle.message(if (ApplicationManager.getApplication().isRestartCapable) "ide.restart.action" else "ide.shutdown.action"),
            IdeBundle.message("dialog.action.restart.cancel"),
            AllIcons.General.Warning,
          )
          if (doThat) {
            ApplicationManagerEx.getApplicationEx().restart(true)
          }
        }
      }
    }
  }
}
