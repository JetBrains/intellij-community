// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.codeInsight.hint.LineTooltipRenderer
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.tools.intentions.CreatedPreviewUI
import com.intellij.diff.tools.intentions.IntentionDiffUtil
import com.intellij.lang.LangBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.CombinedPopupLayout
import com.intellij.openapi.editor.CombinedPopupPanel
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.HintHint
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.SeparatorOrientation
import com.intellij.ui.WidthBasedLayout
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.panels.ListLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.initOnShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class WrapperPanel(content: JComponent) : JPanel(BorderLayout()), WidthBasedLayout {
  init {
    setBorder(null)
    setContent(content)
  }

  fun setContent(content: JComponent) {
    removeAll()
    add(content, BorderLayout.CENTER)
  }

  private val component: JComponent?
    get() = getComponent(0) as JComponent?

  override fun getPreferredWidth(): Int {
    return WidthBasedLayout.getPreferredWidth(this.component)
  }

  override fun getPreferredHeight(width: Int): Int {
    return WidthBasedLayout.getPreferredHeight(this.component, width)
  }
}

internal class ModernDiffPreviewPanel(
  private val project: Project,
  private val modernDiff: IntentionPreviewInfo.ModernDiff,
  private val popup: JBPopup,
) : JBPanelWithEmptyText(BorderLayout()), Disposable {

  init {
    Disposer.register(popup, this)
    
    if (modernDiff.diffs.isEmpty()) {
      emptyText.text = "No preview available"
    } else {
      setupDiffViewer()
    }
  }

  private fun createTopCaptionComponent(@NlsSafe highlightingHtml: String?): JPanel? {
    val layeredPane = popup.content.rootPane.layeredPane
    val hintHint = HintHint().setAwtTooltip(true).setStatus(HintHint.Status.Info)

    val editorPane = LineTooltipRenderer.createEditorPane(
      if (!highlightingHtml.isNullOrBlank())
        highlightingHtml
      else
        LangBundle.message("modern.diff.dummy.preview.text"),
      hintHint, layeredPane, false
    )

    val scrollPane = LineTooltipRenderer.createScrollPane(editorPane, hintHint)
    val layoutingPanel = LineTooltipRenderer.createLayoutingPanel(hintHint, scrollPane, editorPane, true, false)

    return WrapperPanel(layoutingPanel).apply {
      background = hintHint.textBackground
      isOpaque = true
    }
  }

  private fun configureUiPanel(panel: JPanel, omitTopGap: Boolean = true, omitBottomGap: Boolean = false,): JPanel {
    return panel.apply {
      border = JBUI.Borders.empty(if (omitTopGap) 0 else 3, 5, if (omitBottomGap) 0 else 5, 5)
    }
  }

  private fun createSingleDiffPanel(ui: CreatedPreviewUI): JPanel {
    return if (ui.isFileNameTrivial)
      configureUiPanel(ui.panel, omitTopGap = false, omitBottomGap = false)
    else
      createVerticalFlowingContainerWithCaptionsForUis(listOf(ui))
  }

  private fun createVerticalFlowingContainerWithCaptionsForUis(diffUis: List<CreatedPreviewUI>): JPanel {
    val flowingContainerPanel = JPanel(ListLayout.vertical(0))
    for ((i, ui) in diffUis.withIndex()) {
      val separatorColor = if (ExperimentalUI.isNewUI())
        ui.unifiedEditor.colorsScheme.getColor(EditorColors.INDENT_GUIDE_COLOR)
      else
        ui.unifiedEditor.colorsScheme.getColor(EditorColors.TEARLINE_COLOR)

      (ui.unifiedEditor as? EditorImpl)?.shouldIgnoreViewportInsets = true
      if (i != diffUis.lastIndex) {
        (ui.unifiedEditor as? EditorImpl)?.additionalSizeForMeasure = 10
      }

      val label = JBLabel().apply {
        text = ui.fileNameForDisplay
        foreground = JBUI.CurrentTheme.Label.disabledForeground(true)
        icon = ui.fileTypeIcon
        border = JBUI.Borders.empty(UIUtil.getListCellVPadding() + 5, UIUtil.getListCellHPadding() + 5)
      }

      if (i != 0) {
        val separatorComponent = SeparatorComponent(separatorColor, SeparatorOrientation.HORIZONTAL)
        flowingContainerPanel.add(separatorComponent)
      }

      flowingContainerPanel.add(
        BorderLayoutPanel().apply {
          add(label, BorderLayout.NORTH)
          add(configureUiPanel(ui.panel, omitBottomGap = i != diffUis.lastIndex), BorderLayout.SOUTH)
        }
      )
    }
    return flowingContainerPanel
  }

  private fun combineWithTopComponentOrBareContent(top: JPanel?, content: JPanel): JPanel {
    return if (top != null) {
      CombinedPopupPanel(CombinedPopupLayout(top, content)).apply {
        add(top)
        add(content)
      }
    } else content
  }

  private fun setupDiffViewer() {
    // When there are multiple warnings at the same offset, this will return the HighlightInfo
    // containing all of them, not just the first one as found by findInfo()

    //val combinedPopupPanel = CombinedPopupPanel(CombinedPopupLayout())

    val diffUis = mutableListOf<CreatedPreviewUI>()
    for (diffInfo in modernDiff.diffs) {
      val contentFactory = DiffContentFactory.getInstance()
      val editorFactory = EditorFactory.getInstance()

      // Create documents for original and modified text
      val originalDocument = editorFactory.createDocument(diffInfo.originalText)
      val modifiedDocument = editorFactory.createDocument(diffInfo.modifiedText)

      // Create diff contents
      val content1 = contentFactory.create(project, originalDocument, diffInfo.fileType)
      val content2 = contentFactory.create(project, modifiedDocument, diffInfo.fileType)

      val markup1 = DocumentMarkupModel.forDocument(content1.document, project, true)
      for ((range, attributesKey) in diffInfo.originalHighlighters) {
        markup1.addRangeHighlighter(attributesKey, range.startOffset, range.endOffset, 0, HighlighterTargetArea.EXACT_RANGE)
      }

      val markup2 = DocumentMarkupModel.forDocument(content2.document, project, true)
      for ((range, attributesKey) in diffInfo.modifiedHighlighters) {
        markup2.addRangeHighlighter(attributesKey, range.startOffset, range.endOffset, 0, HighlighterTargetArea.EXACT_RANGE)
      }

      val ui = IntentionDiffUtil.createPreviewUi(project, this, diffInfo.fileName!!, diffInfo.fileType.icon!!, modernDiff.currentContextDisplayName)

      diffUis.add(ui)

      ui.panel.initOnShow("Apply diff to UI") {
        ui.unifiedEditor.gutterComponentEx.pinLayoutsTogetherOnMaximalWidth(diffUis.map { it.unifiedEditor.gutterComponentEx })

        withContext(Dispatchers.Default) {
          IntentionDiffUtil.applyDiffToUi(project, this@ModernDiffPreviewPanel, content1, content2, ui)
        }
        popup.pack(true, true)
      }
    }

    val setupPanel = combineWithTopComponentOrBareContent(
      createTopCaptionComponent(modernDiff.highlightingHtml),
      if (diffUis.size == 1)
        createSingleDiffPanel(diffUis.first())
      else
        createVerticalFlowingContainerWithCaptionsForUis(diffUis)
    )

    add(setupPanel, BorderLayout.CENTER)
    border = JBUI.Borders.empty()
    popup.pack(true, true)
  }

  override fun dispose() {
    // Cleanup will be handled by Disposer
  }
}
