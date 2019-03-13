// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations

import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.util.ui.JBUI
import java.awt.Font
import java.beans.PropertyChangeEvent
import javax.swing.border.Border
import java.beans.PropertyChangeListener


class CustomFrameIdeMenuBar(actionManager: ActionManagerEx, dataManager: DataManager, disposable: Disposable) : IdeMenuBar(actionManager,
                                                                                                                           dataManager) {
  init {
    // ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(UISettingsListener.TOPIC, UISettingsListener { updateFontStyle() })

   // JBUI.addPropertyChangeListener(JBUI.USER_SCALE_FACTOR_PROPERTY) { updateFontStyle() }

  }

  /**
   * TODO
   */
  override fun setFont(font: Font) {
    val min = Math.min(font.size, 12)
    val scaledSize = Math.round(min / UISettings.defFontScale)
    super.setFont(Font(font.name, font.style, scaledSize))
  }

  override fun getBorder(): Border {
    return JBUI.Borders.empty()
  }
}