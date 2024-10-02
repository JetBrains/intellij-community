// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.popup.layouter

import com.intellij.codeInsight.codeVision.ui.model.SwingScheduler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ScrollingModel
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.JBUI
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.intersect
import com.jetbrains.rd.util.reactive.IProperty
import com.jetbrains.rd.util.reactive.ISource
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.throttleLast
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Rectangle
import java.time.Duration
import javax.swing.SwingUtilities

@ApiStatus.Internal
class EditorAnchoringRect(
  lifetime: Lifetime,
  private val editor: Editor,
  documentOffset: Int,
  private val delegate: (Rectangle) -> Rectangle? = { it }
) : AnchoringRect {

  companion object {
    fun horizontalSmartClipDelegate(editor: Editor): (Rectangle) -> Rectangle? = {
      val visibleArea = editor.scrollingModel.visibleArea
      if ((visibleArea.y >= it.y + editor.lineHeight || visibleArea.y + visibleArea.height <= it.y - editor.lineHeight / 2))
        null
      else
        it.horizontalSmartClip(visibleArea)
    }

    private fun smartClipDelegate(editor: Editor): (Rectangle) -> Rectangle? = {
      val visibleArea = editor.scrollingModel.visibleArea
      it.smartClip(visibleArea)
    }

    fun create(lifetime: Lifetime, offset: Int, editor: Editor, delegate: (Rectangle) -> Rectangle? = { it }): EditorAnchoringRect {
      return EditorAnchoringRect(lifetime, editor, offset, delegate)
    }


    fun createSmartClipRect(lifetime: Lifetime, offset: Int, editor: Editor): AnchoringRect {
      return create(lifetime, offset, editor, smartClipDelegate(editor))
    }

    fun createHorizontalSmartClipRect(lifetime: Lifetime, offset: Int, editor: Editor): AnchoringRect {
      return create(lifetime, offset, editor, horizontalSmartClipDelegate(editor))
    }
  }


  private val rangeMarker: RangeMarker
  override val rectangle: IProperty<Rectangle?>


  fun ScrollingModel.visibleAreaChanged(): ISource<VisibleAreaEvent> = object : ISource<VisibleAreaEvent> {
    override fun advise(lifetime: Lifetime, handler: (VisibleAreaEvent) -> Unit) {
      val visibleAreaListener = VisibleAreaListener {
        handler(it)
      }
      this@visibleAreaChanged.addVisibleAreaListener(visibleAreaListener)
      lifetime.onTermination {
        this@visibleAreaChanged.removeVisibleAreaListener(visibleAreaListener)
      }
    }
  }

  init {
    val document = editor.document
    rangeMarker = document.createRangeMarker(documentOffset, documentOffset)
    rectangle = Property(calculateRectangle())

    val visibleAreaChanged = editor.scrollingModel.visibleAreaChanged()

    val outerLifetime = (editor as? EditorImpl)?.disposable?.createLifetime() ?: editor.project?.createLifetime()
    val lt = if (outerLifetime != null) {
      lifetime.intersect(outerLifetime)
    }
    else lifetime

    visibleAreaChanged.throttleLast(Duration.ofMillis(10), SwingScheduler).advise(lt) {
      rectangle.set(calculateRectangle())
    }
  }

  private fun calculateRectangle(): Rectangle? {
    ThreadingAssertions.assertEventDispatchThread()
    require(!editor.isDisposed)

    val range = if (rangeMarker.isValid) {
      TextRange(rangeMarker.startOffset, rangeMarker.endOffset)
    }
    else {
      return null
    }

    var rect = if (range.isEmpty) {
      val point = editor.offsetToXY(range.startOffset)
      Rectangle(point, Dimension(JBUI.scale(1), editor.lineHeight + JBUI.scale(1)))
    }
    else {
      val start = editor.offsetToXY(range.startOffset)
      val end = editor.offsetToXY(range.endOffset)
      rectangleFromLTRBNonNegative(start.x, start.y - editor.lineHeight, end.x, end.y)
    }

    rect = rect.map { it.map(delegate) } ?: return null

    val point = rect.location
    SwingUtilities.convertPointToScreen(point, editor.contentComponent)
    return Rectangle(point, Dimension(rect.width, rect.height))
  }
}

