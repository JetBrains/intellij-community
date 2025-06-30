// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.showcase

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.PaintingParent.Wrapper
import org.jetbrains.jewel.bridge.compose
import java.awt.Dimension
import javax.swing.JComponent

private fun createComposeShowcaseComponent(): JComponent {
  return compose {
    ComposeShowcase()
  }
}

private class ComposeShowcaseAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    ComposeShowcaseDialog(e.project, e.presentation.text).show()
  }
}

private class ComposeShowcaseDialog(project: Project?, @NlsSafe dialogTitle: String) :
  DialogWrapper(project, null, true, IdeModalityType.MODELESS, false) {

  init {
    title = dialogTitle
    init()
  }

  override fun createCenterPanel(): JComponent {
    return Wrapper(createComposeShowcaseComponent()).apply {
      minimumSize = Dimension(200, 100)
      preferredSize = Dimension(800, 600)
    }
  }
}
