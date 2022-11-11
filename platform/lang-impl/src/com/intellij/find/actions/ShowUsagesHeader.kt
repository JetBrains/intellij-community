// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.JBGaps
import com.intellij.usageView.UsageViewBundle
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JLabel

internal class ShowUsagesHeader(pinButton: JComponent, @NlsContexts.PopupTitle title: String) {

  @JvmField
  val panel: DialogPanel

  private lateinit var titleLabel: JLabel
  private lateinit var processIcon: AsyncProcessIcon
  private lateinit var statusLabel: JLabel

  init {
    panel = panel {
      customizeSpacingConfiguration(spacingConfiguration = object : IntelliJSpacingConfiguration() {
        // Remove default vertical gap around cells, so the header can be smaller
        override val verticalComponentGap: Int
          get() = 0
      }) {
        row {
          // Don't use Row.label method: it processes mnemonics and breaks symbol &
          val titleCell = cell(JLabel(XmlStringUtil.wrapInHtml("<body><nobr>$title</nobr></body>")))
            .resizableColumn()
            .gap(RightGap.SMALL)
          titleLabel = titleCell.component

          processIcon = cell(AsyncProcessIcon("xxx"))
            .gap(RightGap.SMALL)
            .component
          val statusCell = label("")
          statusLabel = statusCell.component
          val pinCell = cell(pinButton)

          if (ExperimentalUI.isNewUI()) {
            val headerInsets = JBUI.CurrentTheme.ComplexPopup.headerInsets()
            titleCell.customize(Gaps(top = headerInsets.top, bottom = headerInsets.bottom, right = JBUI.scale(12)))
            statusCell.component.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
            statusCell.customize(JBGaps(right = 8))
            // Fix vertical alignment for the pin icon
            pinCell.customize(JBGaps(top = 2))
          }
          else {
            statusCell.gap(RightGap.SMALL)
          }
        }
      }
    }
  }

  fun setStatusText(hasMore: Boolean, visibleCount: Int, totalCount: Int) {
    statusLabel.text = getStatusString(!processIcon.isDisposed, hasMore, visibleCount, totalCount)
  }

  fun disposeProcessIcon() {
    Disposer.dispose(processIcon)
    processIcon.parent?.apply {
      remove(processIcon)
      repaint()
    }
  }

  @NlsContexts.PopupTitle
  fun getTitle(): String {
    return titleLabel.text
  }

  private fun getStatusString(findUsagesInProgress: Boolean, hasMore: Boolean, visibleCount: Int, totalCount: Int): @Nls String {
    return if (findUsagesInProgress || hasMore) {
      UsageViewBundle.message("showing.0.usages", visibleCount - if (hasMore) 1 else 0)
    }
    else if (visibleCount != totalCount) {
      UsageViewBundle.message("showing.0.of.1.usages", visibleCount, totalCount)
    }
    else {
      UsageViewBundle.message("found.0.usages", totalCount)
    }
  }
}