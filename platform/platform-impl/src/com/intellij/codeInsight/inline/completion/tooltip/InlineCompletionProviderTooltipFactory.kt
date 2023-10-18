// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.HyperlinkListener

object InlineCompletionProviderTooltipFactory {
  fun defaultProviderTooltip(
    @Nls name: String,
    providerIcon: Icon? = null,
    moreInfoAction: HyperlinkListener? = null,
  ): JComponent = when (moreInfoAction) {
    null -> JBLabel(name).apply { icon = providerIcon }
    else -> HyperlinkLabel(name).apply {
      icon = providerIcon
      addHyperlinkListener(moreInfoAction)
    }
  }
}