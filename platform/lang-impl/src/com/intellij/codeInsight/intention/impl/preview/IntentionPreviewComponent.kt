// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.ide.plugins.MultiPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.ui.PopupBorder
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.ExtendableHTMLViewFactory
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JList
import javax.swing.JPanel

internal class IntentionPreviewComponent(parent: Disposable) :
  JBLoadingPanel(BorderLayout(), { panel -> IntentionPreviewLoadingDecorator(panel, parent) }) {
  private var NO_PREVIEW_LABEL = setupLabel(CodeInsightBundle.message("intention.preview.no.available.text"))
  private var LOADING_LABEL = setupLabel(CodeInsightBundle.message("intention.preview.loading.preview"))

  var editors: List<EditorEx> = emptyList()
  var html: IntentionPreviewInfo.Html? = null

  val multiPanel: MultiPanel = object : MultiPanel() {
    override fun create(key: Int): JComponent {
      return when (key) {
        NO_PREVIEW -> NO_PREVIEW_LABEL
        LOADING_PREVIEW -> LOADING_LABEL
        else -> {
          val htmlInfo = html
          if (htmlInfo != null) {
            return createHtmlPanel(htmlInfo)
          }
          if (editors.isEmpty()) return NO_PREVIEW_LABEL

          IntentionPreviewEditorsPanel(mutableListOf<EditorEx>().apply { addAll<EditorEx>(editors) })
        }
      }
    }

    private fun createHtmlPanel(htmlInfo: IntentionPreviewInfo.Html): JPanel {
      val targetSize = IntentionPreviewPopupUpdateProcessor.MIN_WIDTH * UIUtil.getLabelFont().size.coerceAtMost(24) / 12
      val editor = object : JEditorPane() {
        var prefHeight: Int? = null

        override fun repaint() {
          scrollLists(this)
          super.repaint()
        }

        override fun getPreferredSize(): Dimension {
          if (prefHeight == null) {
            val pos = modelToView2D(document.endPosition.offset.coerceAtLeast(1) - 1)
            if (pos != null) {
              prefHeight = pos.maxY.toInt() + 5
            }
          }
          return Dimension(targetSize, prefHeight ?: Integer.MAX_VALUE)
        }
      }

      val content = htmlInfo.content()
      editor.editorKit = HTMLEditorKitBuilder()
        .withViewFactoryExtensions(ExtendableHTMLViewFactory.Extensions.icons(content),
                                   ExtendableHTMLViewFactory.Extensions.WORD_WRAP)
        .build()
      editor.text = content.toString()
      editor.size = Dimension(targetSize, Integer.MAX_VALUE)
      return wrapToPanel(editor)
    }
  }

  init {
    add(multiPanel)
    border = PopupBorder.Factory.create(true, true)
    setLoadingText(CodeInsightBundle.message("intention.preview.loading.preview"))
  }

  companion object {
    const val NO_PREVIEW = -1
    const val LOADING_PREVIEW = -2

    private fun setupLabel(text: @Nls String): JComponent {
      val label = SimpleColoredComponent()
      label.append(text)
      return wrapToPanel(label)
    }

    private fun wrapToPanel(component: JComponent): JPanel {
      val panel = JPanel(BorderLayout())
      panel.background = component.background
      panel.add(component, BorderLayout.CENTER)
      panel.border = JBEmptyBorder(5)
      return panel
    }

    private fun scrollLists(container: Container) {
      if (container is JList<*>) {
        val index = container.selectedIndex
        if (index != -1) {
          container.ensureIndexIsVisible(index)
        }
      }
      container.components.filterIsInstance<Container>().forEach { scrollLists(it) }
    }
  }
}