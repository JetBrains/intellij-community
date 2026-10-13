// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.application.options.colors.highlighting.HighlightData
import com.intellij.application.options.colors.highlighting.HighlightsExtractor
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

/** The unscaled size of the code sample. */
internal const val SAMPLE_WIDTH = 360
internal const val SAMPLE_HEIGHT = 230

/** The font of the sample is smaller than the font of the editor, so that more of each line fits. */
private const val SAMPLE_FONT_SCALE = 0.9f

/**
 * Creates a read-only editor with [sample]. The editor needs no project.
 * The editor follows the global color scheme, so it changes with the theme. It is released with [parent].
 */
@Suppress("SplitModeApiUsage")
internal fun createThemeSample(sample: WelcomeRightTabContentProvider.ThemePreviewSample, parent: Disposable): JComponent {
  val highlighterFactory = EditorHighlighterFactory.getInstance()
  // This loads the language and registers the attribute keys that the tags name.
  highlighterFactory.createEditorHighlighter(EditorColorsManager.getInstance().globalScheme, sample.fileName, null)

  val tags = sample.highlightTags.mapValues { TextAttributesKey.createTextAttributesKey(it.value) }
  val declaredHighlights = mutableListOf<HighlightData>()
  val text = HighlightsExtractor(tags).extractHighlights(sample.text, declaredHighlights)

  val editorFactory = EditorFactory.getInstance()
  val editor = editorFactory.createViewer(editorFactory.createDocument(text), null, EditorKind.PREVIEW) as EditorEx
  Disposer.register(parent) { editorFactory.releaseEditor(editor) }

  editor.setCaretEnabled(false)
  editor.setHorizontalScrollbarVisible(false)
  editor.setVerticalScrollbarVisible(true)
  editor.settings.apply {
    isUseSoftWraps = true
    isPaintSoftWraps = false
    isLineNumbersShown = false
    isLineMarkerAreaShown = false
    isFoldingOutlineShown = false
    isRightMarginShown = false
    isCaretRowShown = false
    isIndentGuidesShown = false
    additionalLinesCount = 0
    additionalColumnsCount = 0
    // The zoom with the wheel would change the font size of the global scheme.
    isWheelFontChangeEnabled = false
  }

  // A viewer still lets the mouse select text.
  editor.selectionModel.addSelectionListener(object : SelectionListener {
    override fun selectionChanged(e: SelectionEvent) {
      if (editor.selectionModel.hasSelection()) {
        editor.selectionModel.removeSelection()
      }
    }
  }, parent)

  fun applyGlobalScheme() {
    val scheme = EditorColorsManager.getInstance().globalScheme
    editor.colorsScheme = scheme
    // A new scheme resets the font size. The scheme holds the size before the IDE zoom.
    editor.setFontSize(UISettingsUtils.getInstance().scaleFontSize(scheme.editorFontSize2D) * SAMPLE_FONT_SCALE)
    editor.highlighter = highlighterFactory.createEditorHighlighter(scheme, sample.fileName, null)
    editor.backgroundColor = scheme.defaultBackground
  }
  applyGlobalScheme()

  // The highlights hold scheme keys, so they follow the scheme and are added once.
  for (highlight in declaredHighlights) {
    highlight.addHighlToView(editor, EditorColorsManager.getInstance().globalScheme, emptyMap())
  }
  ApplicationManager.getApplication().messageBus.connect(parent)
    .subscribe(EditorColorsManager.TOPIC, EditorColorsListener { applyGlobalScheme() })

  // An editor computes the preferred size of its own component, so the size goes on a wrapper.
  val wrapper = object : JPanel(BorderLayout()) {
    override fun getPreferredSize(): Dimension = Dimension(JBUI.scale(SAMPLE_WIDTH), JBUI.scale(SAMPLE_HEIGHT))
  }
  wrapper.isOpaque = false
  wrapper.add(editor.component, BorderLayout.CENTER)
  return wrapper
}
