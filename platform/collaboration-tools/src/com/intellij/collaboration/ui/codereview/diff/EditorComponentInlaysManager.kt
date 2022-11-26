// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.diff.tools.simple.SimpleAlignedDiffModel.Companion.ALIGNED_CHANGE_INLAY_PRIORITY
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class EditorComponentInlaysManager(val editor: EditorImpl) : Disposable {

  private val managedInlays = mutableMapOf<ComponentWrapper, Disposable>()
  private val editorWidthWatcher = EditorTextWidthWatcher()

  init {
    editor.scrollPane.viewport.addComponentListener(editorWidthWatcher)
    Disposer.register(this, Disposable {
      editor.scrollPane.viewport.removeComponentListener(editorWidthWatcher)
    })

    EditorUtil.disposeWithEditor(editor, this)
  }


  /**
   * @param priority impacts the visual order in which inlays are displayed. Components with higher priority will be shown higher
   */
  @RequiresEdt
  fun insertAfter(lineIndex: Int, component: JComponent, priority: Int = 0): Disposable? {
    if (Disposer.isDisposed(this)) return null

    // Inlays added inside diff with aligned changes mode on, should conform the following rules to not break changes aligning:
    // 1. Inlays should be added "above" line, except the last line.
    // 2. The priority should be greater than ALIGNED_CHANGE_INLAY_PRIORITY or less in case of last line.
    val wrappedComponent = ComponentWrapper(component)
    val isLastLine = lineIndex == editor.document.lineCount - 1
    val offset = editor.document.getLineEndOffset(if (isLastLine) lineIndex else lineIndex + 1)
    val inlayPriority = if (isLastLine) ALIGNED_CHANGE_INLAY_PRIORITY + 1 + priority else ALIGNED_CHANGE_INLAY_PRIORITY - 1 - priority

    return EditorEmbeddedComponentManager.getInstance()
      .addComponent(editor, wrappedComponent,
                    EditorEmbeddedComponentManager.Properties(EditorEmbeddedComponentManager.ResizePolicy.none(),
                                                              null,
                                                              isLastLine,
                                                              !isLastLine,
                                                              inlayPriority,
                                                              offset))?.also {
        managedInlays[wrappedComponent] = it
        Disposer.register(it, Disposable { managedInlays.remove(wrappedComponent) })
      }
  }

  private inner class ComponentWrapper(private val component: JComponent) : BorderLayoutPanel() {
    init {
      isOpaque = false

      border = JBUI.Borders.empty()
      addToCenter(component)

      component.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) = dispatchEvent(ComponentEvent(component, ComponentEvent.COMPONENT_RESIZED))
      })
    }

    override fun getPreferredSize(): Dimension {
      return Dimension(editorWidthWatcher.editorTextWidth, component.preferredSize.height)
    }
  }

  override fun dispose() {
    managedInlays.values.forEach(Disposer::dispose)
  }

  private inner class EditorTextWidthWatcher : ComponentAdapter() {

    var editorTextWidth: Int = 0

    private val maximumEditorTextWidth: Int
    private val verticalScrollbarFlipped: Boolean

    init {
      val metrics = editor.getFontMetrics(Font.PLAIN)
      val spaceWidth = FontLayoutService.getInstance().charWidth2D(metrics, ' '.code)
      // -4 to create some space
      maximumEditorTextWidth = ceil(spaceWidth * (editor.settings.getRightMargin(editor.project)) - 4).toInt()

      val scrollbarFlip = editor.scrollPane.getClientProperty(JBScrollPane.Flip::class.java)
      verticalScrollbarFlipped = scrollbarFlip == JBScrollPane.Flip.HORIZONTAL || scrollbarFlip == JBScrollPane.Flip.BOTH

      // calculate initial [editorTextWidth] if editor is already shown
      updateWidthForAllInlays()
    }

    override fun componentResized(e: ComponentEvent) = updateWidthForAllInlays()
    override fun componentHidden(e: ComponentEvent) = updateWidthForAllInlays()
    override fun componentShown(e: ComponentEvent) = updateWidthForAllInlays()

    private fun updateWidthForAllInlays() {
      val newWidth = calcWidth()
      if (editorTextWidth == newWidth) return
      editorTextWidth = newWidth

      managedInlays.keys.forEach {
        it.dispatchEvent(ComponentEvent(it, ComponentEvent.COMPONENT_RESIZED))
        it.invalidate()
      }
    }

    private fun calcWidth(): Int {
      val visibleEditorTextWidth = editor.scrollPane.viewport.width - getVerticalScrollbarWidth() - getGutterTextGap()
      return min(max(visibleEditorTextWidth, 0), max(maximumEditorTextWidth, MINIMAL_TEXT_WIDTH))
    }

    private fun getVerticalScrollbarWidth(): Int {
      val width = editor.scrollPane.verticalScrollBar.width
      return if (!verticalScrollbarFlipped) width * 2 else width
    }

    private fun getGutterTextGap(): Int {
      return if (verticalScrollbarFlipped) {
        val gutter = (editor as EditorEx).gutterComponentEx
        gutter.width - gutter.whitespaceSeparatorOffset
      }
      else 0
    }
  }

  companion object {
    private const val MINIMAL_TEXT_WIDTH = 300
  }
}
