// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont

/**
 * @author Konstantin Bulenkov
 */
internal class LabelSizeDemoAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val panel = panel {
      label("H0", JBFont.h0().asBold())
      label("H1", JBFont.h1().asBold())
      label("H2", JBFont.h2())
      label("H2 Bold", JBFont.h2().asBold())
      label("H3", JBFont.h3())
      label("H3 Bold", JBFont.h3().asBold())
      label("H4", JBFont.h4().asBold())
      label("Default", JBFont.regular())
      label("Default Bold", JBFont.regular().asBold())
      label("Medium", JBFont.medium())
      label("Medium Bold", JBFont.medium().asBold())
      label("Small", JBFont.small())
    }
    dialog("Test Label Sizes", panel).show()
  }

  private fun Panel.label(label: String, font: JBFont) {
    row(label) {
      label("Lorem ipsum 1234567890").applyToComponent { this.font = font }
    }
  }
}
