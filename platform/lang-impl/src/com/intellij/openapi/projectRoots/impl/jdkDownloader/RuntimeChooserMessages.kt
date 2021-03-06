// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts.DialogMessage
import javax.swing.JComponent

object RuntimeChooserMessages {
  private val title get() = LangBundle.message("dialog.title.choose.ide.runtime")

  fun showErrorMessage(
    @DialogMessage message: String,
    parent: JComponent? = null,
  ) {
    invokeLater {
      Messages.showErrorDialog(parent, message, title)
    }
  }

  fun showRestartMessage(
    @DialogMessage runtimeDescription: String,
  ) {
    invokeLater {
      val app = ApplicationManager.getApplication()
      val result = MessageDialogBuilder.okCancel(
        title = title,
        message = LangBundle.message("dialog.message.choose.ide.runtime.is.set.to", runtimeDescription),
      ).yesText(
        when {
          app.isRestartCapable -> LangBundle.message("dialog.action.choose.ide.runtime.restart")
          else -> LangBundle.message("dialog.action.choose.ide.runtime.close")
        }
      ).guessWindowAndAsk()

      if (!result) return@invokeLater
      if (app.isRestartCapable) {
        app.exit(false, true, true)
      }
      else {
        app.exit(false, true, false)
      }
    }
  }
}
