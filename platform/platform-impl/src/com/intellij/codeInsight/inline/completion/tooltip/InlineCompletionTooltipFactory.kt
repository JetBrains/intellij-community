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
      icon(icon).gap(RightGap.SMALL)
      link(name, moreInfoAction).gap(RightGap.SMALL)
      comment(comment)
    }
  }
}