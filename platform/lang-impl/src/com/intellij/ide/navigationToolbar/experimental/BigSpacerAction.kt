// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

class BigSpacerAction : DumbAwareAction(), CustomComponentAction {
  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return JPanel().apply {
      border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
      minimumSize = Dimension(8, 8)
      preferredSize = Dimension(8, 8)
      maximumSize = Dimension(8, 8)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
  }
}