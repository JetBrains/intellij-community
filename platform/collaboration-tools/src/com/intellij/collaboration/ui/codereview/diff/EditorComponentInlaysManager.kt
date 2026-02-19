// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager
import com.intellij.openapi.editor.impl.EditorEmbeddedComponentManager.Properties.RendererFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import kotlin.math.max
import kotlin.math.min

@Deprecated("Deprecated in favour of using inlays directly - com.intellij.collaboration.ui.codereview.editor.EditorComponentInlaysUtilKt.insertComponentAfter")
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
  fun insertAfter(lineIndex: Int, component: JComponent, priority: Int = 0, rendererFactory: RendererFactory? = null): Inlay<*>? {
    if (Disposer.isDisposed(this)) return null

    // TODO: rework diff mode with aligned changes.
    //  This mode uses Inlays and some comments may look bad with this mode enabled because of the positioning
    val wrappedComponent = ComponentWrapper(component)
    val offset = editor.document.getLineEndOffset(lineIndex)

    return EditorEmbeddedComponentManager.getInstance()
      .addComponent(editor, wrappedComponent,
                    EditorEmbeddedComponentManager.Properties(EditorEmbeddedComponentManager.ResizePolicy.none(),
                                                              rendererFactory,
                                                              false,
                                                              false,
                                                              priority,
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

    private val verticalScrollbarFlipped: Boolean

    init {
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
      return min(max(visibleEditorTextWidth, 0), JBUI.scale(PREFERRED_INLAY_WIDTH))
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
    private val PREFERRED_INLAY_WIDTH = CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH + 52
  }
}
