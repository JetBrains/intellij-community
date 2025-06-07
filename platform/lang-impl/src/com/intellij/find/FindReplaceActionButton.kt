// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find

import com.intellij.ide.ui.UISettings.Companion.getInstance
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonPainter
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.ExperimentalUI
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Insets
import javax.swing.JButton

internal class FindReplaceActionButton(
  @NlsActions.ActionText text: String,
  myMnemonic: Char,
) : JButton(text) {

  init {
    if (!getInstance().disableMnemonicsInControls) {
      setMnemonic(myMnemonic)
    }
    isContentAreaFilled = !ExperimentalUI.isNewUI()
  }

  override fun updateUI() {
    super.updateUI()
    border = object : DarculaButtonPainter() {
      override fun getBorderInsets(c: Component): Insets {
        return JBUI.insets(1)
      }
    }
  }

}
