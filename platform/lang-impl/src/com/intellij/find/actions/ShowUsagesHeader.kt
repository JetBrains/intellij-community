// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.Gray
import com.intellij.ui.RowIcon
import com.intellij.ui.TextIcon
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.usageView.UsageViewBundle
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel

internal class ShowUsagesHeader(pinButton: JComponent, @NlsContexts.PopupTitle title: String) {
  @JvmField
  val panel: DialogPanel

  private lateinit var titleLabel: JLabel
  private lateinit var searchProcessIcon: AsyncProcessIcon
  private lateinit var statusLabel: JLabel
  private lateinit var analyzingProgressLabel: JLabel


  private val analyzingText = TextIcon(
    IdeBundle.message("dumb.mode.results.might.be.incomplete"),
    JBUI.CurrentTheme.BigPopup.searchFieldGrayForeground(),
    Gray.TRANSPARENT,
    0
  )

  private val analyzingIcon: Icon = RowIcon(2, com.intellij.ui.icons.RowIcon.Alignment.CENTER).apply {
    setIcon(AnimatedIcon.Default.INSTANCE, 0)
    setIcon(analyzingText, 1)
  }

  init {
    panel = panel {
      customizeSpacingConfiguration(spacingConfiguration = object : IntelliJSpacingConfiguration() {
        // remove a default vertical gap around cells, so the header can be smaller
        override val verticalComponentGap: Int
          get() = 0
      }) {
        row {
          // Don't use Row.label method: it processes mnemonics and breaks symbol &
          val titleCell = cell(JLabel(XmlStringUtil.wrapInHtml("<body><nobr>$title</nobr></body>")))
            .applyToComponent { minimumSize = JBUI.emptySize() } // Allow shrinking the title
            .resizableColumn()
            .gap(RightGap.SMALL)
          titleLabel = titleCell.component

          searchProcessIcon = cell(AsyncProcessIcon("xxx"))
            .gap(RightGap.SMALL)
            .component
          val statusCell = label("")
          statusLabel = statusCell.component

          analyzingText.setFont(statusLabel.font)
          analyzingText.fontTransform = FontInfo.getFontRenderContext(statusLabel).transform
          analyzingText.setInsets(scale(3), scale(1), 0, 0)

          val projectAnalyzingCell = icon(analyzingIcon)
          analyzingProgressLabel = projectAnalyzingCell.component.apply {
            toolTipText = IdeBundle.message("dumb.mode.results.might.be.incomplete.during.project.analysis")
            isVisible = false
          }

          val pinCell = cell(pinButton)

          if (ExperimentalUI.isNewUI()) {
            val headerInsets = JBUI.CurrentTheme.ComplexPopup.headerInsets().unscaled
            titleCell.customize(UnscaledGaps(top = headerInsets.top, bottom = headerInsets.bottom, right = 12))
            statusCell.component.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
            statusCell.customize(UnscaledGaps(right = 8))
            projectAnalyzingCell.customize(UnscaledGaps(right = 8))
            projectAnalyzingCell.component.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
            // Fix vertical alignment for the pin icon
            pinCell.customize(UnscaledGaps(top = 2))
          }
          else {
            statusCell.gap(RightGap.SMALL)
          }
        }
      }
    }
  }

  fun setStatusText(hasMore: Boolean, visibleCount: Int, totalCount: Int) {
    statusLabel.text = getStatusString(!searchProcessIcon.isDisposed, hasMore, visibleCount, totalCount)
  }

  fun disposeSearchProcessIcon() {
    Disposer.dispose(searchProcessIcon)
    searchProcessIcon.parent?.apply {
      remove(searchProcessIcon)
      repaint()
    }
  }

  fun disposeAnalyzingIcon() {
    if (!Registry.`is`("ide.usages.popup.analyzing.indicator.enable")) return

    analyzingProgressLabel.removeAll()
    if (!searchProcessIcon.isDisposed) {
      searchProcessIcon.isVisible = true
    }
    analyzingProgressLabel.parent?.apply {
      remove(analyzingProgressLabel)
      repaint()
    }
  }

  fun showAnalyzingIcon() {
    if (!Registry.`is`("ide.usages.popup.analyzing.indicator.enable")) return

    analyzingProgressLabel.isVisible = true
    searchProcessIcon.isVisible = false
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