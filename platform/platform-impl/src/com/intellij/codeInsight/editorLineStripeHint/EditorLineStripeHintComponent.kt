// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorLineStripeHint

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.ui.components.JBPanel
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.BoxLayout
import javax.swing.JPanel

class EditorLineStripeHintComponent(
  val editor: Editor,
  panelRenderer: () -> List<List<EditorLineStripeInlayRenderer>>,
  val stripeColor: Color,
  lifetime: Int = 4,
) : JBPanel<JBPanel<*>>(), Disposable {
  @Suppress("UseJBColor")
  val gradientStartColor: Color = run {
    // transparent
    Color(0, 0, 0, 0)
  }

  private val isInstalled = AtomicBoolean(false)
  private val lifetime: AtomicInteger = AtomicInteger(lifetime)

  private val batches: List<List<Component>>

  init {
    isOpaque = false
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    batches = panelRenderer().map { renderers ->
      renderers.map { renderer -> PhantomInlayComponent(editor, renderer) }
    }

    for (batch in batches) {
      batch.forEach { add(it) }
    }

    val editorListener = EditorListener(this)
    editor.contentComponent.addComponentListener(editorListener)
    editor.scrollingModel.addVisibleAreaListener(editorListener)

    whenDisposed {
      editor.contentComponent.removeComponentListener(editorListener)
      editor.scrollingModel.removeVisibleAreaListener(editorListener)
    }
  }

  fun reposition() {
    val width = editor.scrollingModel.visibleArea.width
    setSize(width / 2, editor.lineHeight)
    ApplicationManager.getApplication().runReadAction {
      val caretPoint = editor.visualPositionToXY(editor.caretModel.visualPosition)
      val startPositionOfEolStripe = (width / 3) * 2
      setBounds(editor.contentComponent.visibleRect.x + (width / 2), caretPoint.y, width / 2, editor.lineHeight)
      for (comp in components) {
        if (comp is PhantomInlayComponent) {
          comp.reposition()
        }
      }
      val lineEndOffset = editor.document.getLineEndOffset(editor.caretModel.logicalPosition.line)
      val caretOffset = editor.caretModel.offset
      val inlays = editor.inlayModel.getAfterLineEndElementsInRange(caretOffset, lineEndOffset)
      val meaningfulTextEnd = editor.offsetToXY(lineEndOffset).x + inlays.sumOf { it.widthInPixels }
      var remainingSpace = if (meaningfulTextEnd > startPositionOfEolStripe) {
        -1
      }
      else {
        editor.component.size.width - meaningfulTextEnd
        //size.width
      }
      for (batch in batches) {
        val compoundSize = batch.sumOf { it.maximumSize.width }
        if (compoundSize < remainingSpace) {
          batch.forEach { it.isVisible = true }
          remainingSpace -= compoundSize
        }
        else {
          batch.forEach { it.isVisible = false }
          remainingSpace -= compoundSize // yes, can be negative. In this case everything to the right is hidden
        }
      }
    }
  }

  fun isShown(): Boolean = isInstalled.get()

  fun redraw() {
    uninstall()
    val currentLifetime = lifetime.getAndDecrement()
    if (currentLifetime <= 0) {
      lifetime.set(0)
      return
    }
    reposition()
    install()
  }

  private fun install() {
    if (isInstalled.compareAndSet(false, true)) {
      editor.contentComponent.add(this)
    }
  }

  fun uninstall() {
    if (isInstalled.compareAndSet(true, false)) {
      editor.contentComponent.remove(this)
    }
  }

  override fun paintComponent(g: Graphics?) {
    super.paintComponent(g)
    val g2d = g as Graphics2D
    val width = width
    val height = height
    val gradient = GradientPaint((width.toFloat() / 3) * 2, height / 2f, gradientStartColor, width.toFloat(), height.toFloat() / 2f, stripeColor)
    g2d.paint = gradient
    g2d.fillRect(0, 0, width, height)
  }

  override fun dispose(): Unit = Unit

  class PhantomInlayComponent(val editor: Editor, val renderer: EditorLineStripeInlayRenderer) : JPanel() {
    var pixelWidth = HintRenderer.Companion.calcWidthInPixels(editor, renderer.text, renderer.widthAdjustment)

    init {
      reposition()
    }

    fun reposition() {
      pixelWidth = HintRenderer.Companion.calcWidthInPixels(editor, renderer.text, renderer.widthAdjustment)
      val delta = if (renderer is EditorLineStripeButtonRenderer) 3 else -5
      maximumSize = Dimension(pixelWidth - delta, editor.lineHeight)
      // delta is needed so that text is close to the button, but far from other text
    }

    override fun paint(g: Graphics) {
      val attrs = renderer.internalGetTextAttributes(editor)
                  ?: editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT)

      HintRenderer.Companion.paintHint(
        g,
        editor as EditorImpl,
        // todo: figure out why 0 is here
        Rectangle(0, y, pixelWidth, height),
        renderer.text,
        attrs,
        attrs,
        renderer.widthAdjustment, false)
    }
  }
}

private class EditorListener(val panel: EditorLineStripeHintComponent) : ComponentAdapter(), VisibleAreaListener {
  override fun componentResized(e: ComponentEvent) {
    panel.reposition()
  }

  override fun visibleAreaChanged(e: VisibleAreaEvent) {
    panel.reposition()
  }
}