// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import javax.swing.JComponent

@Service(Service.Level.APP)
class RuntimeChooserMessages {
  private fun showErrorMessage(parent: JComponent, @NlsContexts.DialogMessage message: String) {
    invokeLater(ModalityState.stateForComponent(parent)) {
      Messages.showErrorDialog(parent, message, LangBundle.message("dialog.title.choose.ide.runtime"))
    }
  }

  fun notifyCustomJdkDoesNotStart(parent: JComponent, jdkHome: String) {
    showErrorMessage(parent, LangBundle.message("notification.content.choose.ide.runtime.set.cannot.start.error", jdkHome))
  }

  fun notifyCustomJdkVersionIsTooOld(parent: JComponent, jdkHome: String, version: String, expected: String) {
    showErrorMessage(parent, LangBundle.message("notification.content.choose.ide.runtime.set.version.error", jdkHome, expected, version))
  }
}
