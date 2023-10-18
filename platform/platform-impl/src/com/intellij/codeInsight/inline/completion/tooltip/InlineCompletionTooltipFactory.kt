// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import javax.swing.Icon
import javax.swing.JComponent

object InlineCompletionTooltipFactory {
  fun defaultProviderTooltip(
    @Nls name: String,
    @Nls comment: String,
    icon: Icon,
    moreInfoAction: ((ActionEvent) -> Unit),
  ): JComponent = panel {
    row {
      link(name, moreInfoAction).applyToComponent {
        setIcon(icon)
      }.gap(RightGap.SMALL)
      comment(comment)
    }
  }
}