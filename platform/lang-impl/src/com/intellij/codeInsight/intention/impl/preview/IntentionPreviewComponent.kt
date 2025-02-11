// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.CodeInsightBundle.message
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.ide.plugins.MultiPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.system.OS
import com.intellij.util.ui.*
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JList
import javax.swing.JPanel

internal class IntentionPreviewComponent(parent: Disposable) :
  JBLoadingPanel(BorderLayout(), { panel -> IntentionPreviewLoadingDecorator(panel, parent) }) {
  var previewComponent: JComponent? = null

  val multiPanel: MultiPanel = object : MultiPanel() {
    override fun create(key: Int): JComponent {
      return when (key) {
        LOADING_PREVIEW -> createHtmlPanel(IntentionPreviewInfo.Html(message("intention.preview.loading.preview")))
        else -> previewComponent!! // It's set in IntentionPreviewPopupUpdateProcessor#select 
      }
    }
  }

  init {
    add(multiPanel)
    setLoadingText(CodeInsightBundle.message("intention.preview.loading.preview"))
  }

  companion object {
    const val NO_PREVIEW: Int = -1
    const val LOADING_PREVIEW: Int = -2
    private val BORDER: JBEmptyBorder = JBUI.Borders.empty(6, 10)

    internal fun createNoPreviewPanel(): JPanel {
      val panel = createHtmlPanel(IntentionPreviewInfo.Html(message("intention.preview.no.available.text")))
      panel.putClientProperty("NO_PREVIEW", true)
      return panel
    }

    internal fun JComponent.isNoPreviewPanel(): Boolean = this.getClientProperty("NO_PREVIEW") != null

    internal fun createHtmlPanel(htmlInfo: IntentionPreviewInfo.Html): JPanel {
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
              prefHeight = pos.maxY.toInt() + when (OS.CURRENT) {
                OS.Windows -> 5
                else -> 0
              }
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
      editor.background = when (htmlInfo.infoKind()) {
        IntentionPreviewInfo.InfoKind.INFORMATION -> JBUI.CurrentTheme.Popup.BACKGROUND
        IntentionPreviewInfo.InfoKind.ERROR -> JBUI.CurrentTheme.Notification.Error.BACKGROUND
      }
      return wrapToPanel(editor)
    }

    private fun wrapToPanel(component: JComponent): JPanel {
      val panel = JPanel(BorderLayout())
      panel.background = component.background
      panel.add(component, BorderLayout.CENTER)
      panel.border = BORDER
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