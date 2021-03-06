// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Future

@ApiStatus.Internal
data class CommandLineProcessorResult(val project: Project?, val future: Future<CliResult>) {
  companion object {
    @JvmStatic
    fun createError(@NlsContexts.DialogMessage message : String): CommandLineProcessorResult {
      return CommandLineProcessorResult(null, CliResult.error(1, message))
    }
  }

  val hasError: Boolean
    get() = future.isDone && future.get().exitCode == 1

  fun showErrorIfFailed(): Boolean {
    if (hasError) {
      Messages.showErrorDialog(future.get().message, IdeBundle.message("dialog.title.cannot.execute.command"))
      return true
    }
    return false
  }

  fun showErrorIfFailedLater() {
    if (hasError) {
      ApplicationManager.getApplication().invokeLater(Runnable {
        showErrorIfFailed()
      }, ModalityState.NON_MODAL)
    }
  }
}