// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedBarActionComponent
import org.jetbrains.annotations.NotNull
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class SegmentedSearchEverywhereAction : SegmentedBarActionComponent() {
  init {
    ActionManager.getInstance().getAction("SegmentedSearchEverywhereGroup")?.let {
      if (it is ActionGroup) {
        actionGroup = it
      }
    }
  }

  override fun update(e: @NotNull AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = actionGroup != null
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return JPanel(BorderLayout()).apply {
      add(super.createCustomComponent(presentation, place), BorderLayout.CENTER)
    }
  }
}