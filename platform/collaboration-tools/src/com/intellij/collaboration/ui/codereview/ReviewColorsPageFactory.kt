// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview

import com.intellij.application.options.colors.ColorAndFontOptions
import com.intellij.application.options.colors.ColorAndFontPanelFactory
import com.intellij.application.options.colors.ColorAndFontSettingsListener
import com.intellij.application.options.colors.NewColorAndFontPanel
import com.intellij.application.options.colors.OptionsPanelImpl
import com.intellij.application.options.colors.PreviewPanel
import com.intellij.application.options.colors.SchemesPanel
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.editor.ReviewInEditorUtil.REVIEW_CHANGED_LINES_COLOR
import com.intellij.collaboration.ui.codereview.editor.ReviewInEditorUtil.REVIEW_STATUS_MARKER_COLOR_SCHEME
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.diff.DefaultFlagsProvider
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil.DiffStripeTextAttributes
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterFreePainterAreaState
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.markup.ActiveGutterRenderer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorAndFontDescriptorsProvider
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.vcs.ex.Range
import com.intellij.openapi.vcs.ex.VisibleRangeMerger
import com.intellij.psi.codeStyle.DisplayPriority
import com.intellij.psi.codeStyle.DisplayPrioritySortable
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JComponent


@ApiStatus.Internal
class ReviewColorsPageFactory : ColorAndFontPanelFactory, ColorAndFontDescriptorsProvider, DisplayPrioritySortable {
  override fun createPanel(options: ColorAndFontOptions): NewColorAndFontPanel {
    val schemesPanel = SchemesPanel(options)
    val optionsPanel = OptionsPanelImpl(options, schemesPanel, getCategory())
    val previewPanel = ReviewPreviewPanel()

    schemesPanel.addListener(object : ColorAndFontSettingsListener.Abstract() {
      override fun schemeChanged(source: Any) {
        previewPanel.setColorScheme(options.selectedScheme)
        optionsPanel.updateOptionsList()
      }
    })

    return NewColorAndFontPanel(schemesPanel, optionsPanel, previewPanel, getCategory(), null, null)
  }

  override fun getAttributeDescriptors(): Array<AttributesDescriptor?> {
    return arrayOfNulls(0)
  }

  override fun getColorDescriptors(): Array<ColorDescriptor?> =
    arrayOf(ColorDescriptor(CollaborationToolsBundle.message("review.color.descriptor.review.changed.lines"),
                            REVIEW_CHANGED_LINES_COLOR,
                            ColorDescriptor.Kind.BACKGROUND))

  override fun getPanelDisplayName(): String {
    return getCategory()
  }

  override fun getDisplayName(): String {
    return getCategory()
  }

  override fun getPriority(): DisplayPriority = DisplayPriority.COMMON_SETTINGS

  private fun getCategory() = CollaborationToolsBundle.message("review.color.collaboration")


  private class ReviewPreviewPanel : PreviewPanel {
    private val myEditor: EditorEx

    init {
      val document = DocumentImpl("", true)
      myEditor = EditorFactory.getInstance().createViewer(document) as EditorEx
      myEditor.getGutterComponentEx().setRightFreePaintersAreaState(EditorGutterFreePainterAreaState.SHOW)
      myEditor.getSettings().setFoldingOutlineShown(true)
    }

    override fun disposeUIResources() = EditorFactory.getInstance().releaseEditor(myEditor)

    override fun getPanel(): JComponent = myEditor.getComponent()

    override fun blinkSelectedHighlightType(selected: Any?) = Unit

    override fun updateView() {
      val sb = StringBuilder()
      val nn = "\n\n"
      sb.append(CollaborationToolsBundle.message("review.color.collaboration.preview.panel.deleted.line.below")).append(nn)
        .append(CollaborationToolsBundle.message("review.color.collaboration.preview.panel.modified.line")).append(nn)
        .append(CollaborationToolsBundle.message("review.color.collaboration.preview.panel.added.line")).append(nn)

      myEditor.getDocument().setText(sb)
      myEditor.getMarkupModel().removeAllHighlighters()
      myEditor.getGutterComponentEx().closeAllAnnotations()

      addHighlighter(Range(1, 1, 0, 1))
      addHighlighter(Range(2, 3, 0, 1))
      addHighlighter(Range(4, 5, 0, 1))
    }

    private fun addHighlighter(range: Range) {
      val textRange = DiffUtil.getLinesRange(myEditor.getDocument(), range.line1, range.line2, false)
      val textAttributes = DiffStripeTextAttributes(range.type)

      val highlighter = myEditor.getMarkupModel().addRangeHighlighter(textRange.startOffset,
                                                                      textRange.endOffset,
                                                                      DiffDrawUtil.LST_LINE_MARKER_LAYER,
                                                                      textAttributes,
                                                                      HighlighterTargetArea.LINES_IN_RANGE)

      highlighter.setThinErrorStripeMark(true)
      highlighter.setLineMarkerRenderer(object : ActiveGutterRenderer {
        override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
          val blocks = VisibleRangeMerger.merge(myEditor, mutableListOf(range), DefaultFlagsProvider.DEFAULT, g.clipBounds)
          for (block in blocks) {
            LineStatusMarkerDrawUtil.paintChangedLines(g as Graphics2D,
                                                       myEditor,
                                                       block.changes,
                                                       REVIEW_STATUS_MARKER_COLOR_SCHEME,
                                                       0,
                                                       false)
          }
        }

        override fun canDoAction(e: MouseEvent): Boolean = LineStatusMarkerDrawUtil.isInsideMarkerArea(e)

        override fun doAction(editor: Editor, e: MouseEvent) {}
      })
    }

    override fun addListener(listener: ColorAndFontSettingsListener) {}

    fun setColorScheme(highlighterSettings: EditorColorsScheme) = myEditor.setColorsScheme(highlighterSettings)
  }

}

