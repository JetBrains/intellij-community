// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.branch

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.InplaceButton
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NonNls
import javax.swing.BorderFactory
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkEvent

class DvcsBranchesDivergedBanner private constructor(helpId: @NonNls String) {

  private val helpButton =
    InplaceButton(IconButton(DvcsBundle.message("branch.popup.warning.branches.have.diverged.description.learn.more"),
                             AllIcons.General.ContextHelp)) { showHint(helpId) }

  companion object {
    fun create(helpId: @NonNls String,
               text: @NlsContexts.Label String = DvcsBundle.message("branch.popup.warning.branches.have.diverged")) =
      with(DvcsBranchesDivergedBanner(helpId)) {
        panel {
          val label = JBLabel(text, UIUtil.getBalloonWarningIcon(), SwingConstants.CENTER).apply { iconTextGap = JBUI.scale(6) }
          row {
            cell(OpaquePanel(HorizontalLayout(JBUI.scale(8), HorizontalLayout.FILL))
                   .apply {
                     add(label)
                     add(helpButton)
                   }).align(Align.CENTER)
          }
        }.apply {
          val color = HintUtil.getWarningColor()
          background = color
          border = BorderFactory.createLineBorder(color, 3)
        }
      }
  }

  private fun showHint(helpId: @NonNls String) {
    val hint = JBLabel(DvcsBundle.message("branch.popup.warning.branches.have.diverged.description"), SwingConstants.CENTER)
    val learnMore = HyperlinkLabel(DvcsBundle.message("branch.popup.warning.branches.have.diverged.description.learn.more"))
      .apply {
        addHyperlinkListener(object : HyperlinkAdapter() {
          override fun hyperlinkActivated(e: HyperlinkEvent) {
            HelpManager.getInstance().invokeHelp(helpId)
          }
        })
      }

    val hintPanel = panel {
      row { cell(hint) }
      row { cell(learnMore) }
    }.apply { border = JBUI.Borders.empty(10) }

    HintManager.getInstance()
      .showHint(hintPanel, RelativePoint.getSouthEastOf(helpButton), HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_TEXT_CHANGE, -1)
  }
}
