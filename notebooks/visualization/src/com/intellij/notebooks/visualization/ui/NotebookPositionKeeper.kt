// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.ScrollingModel
import com.intellij.openapi.editor.impl.EditorImpl

class NotebookPositionKeeper(val editor: EditorImpl) {

  fun getPosition(useCaretPositon: Boolean = true): Position {
    val visibleArea = editor.scrollingModel.getVisibleAreaOnScrollingFinished()

    val topLeftCornerOffset = if (useCaretPositon) {
      val caretY = editor.visualLineToY(editor.caretModel.visualPosition.line)
      if (visibleArea.height > 0 && (caretY + editor.lineHeight <= visibleArea.y || caretY >= (visibleArea.y + visibleArea.height))) {
        editor.logicalPositionToOffset(editor.xyToLogicalPosition(visibleArea.location))
      }
      else {
        editor.caretModel.offset
      }
    }
    else {
      editor.logicalPositionToOffset(editor.xyToLogicalPosition(visibleArea.location))
    }
    val viewportShift = editor.offsetToXY(topLeftCornerOffset).y - visibleArea.y
    val position = Position(topLeftCornerOffset, viewportShift)
    return position
  }

  fun restorePosition(position: Position) {
    val (topLeftCornerOffset, viewportShift) = position
    val scrollingModel = editor.scrollingModel
    val newY = editor.offsetToXY(topLeftCornerOffset).y - viewportShift
    if (editor.scrollingModel.visibleArea.y == newY)
      return
    scrollingModel.withoutAnimation {
      scrollVertically(newY)
    }
  }

  fun <T> keepScrollingPositionWhile(task: () -> T): T {
    return WriteIntentReadAction.compute<T, Nothing> {
      if (editor.isDisposed) {
        return@compute task()
      }
      val position = getPosition(false)
      val (r, newOffset) = getOffsetProvider(position).use { offsetProvider ->
        task() to offsetProvider.getOffset()
      }
      restorePosition(Position(newOffset, position.viewportShift))
      r
    }
  }

  private fun getOffsetProvider(position: Position): OffsetProvider {
    return if (editor.caretModel.offset == position.topLeftCornerOffset) {
      object : OffsetProvider {
        override fun getOffset(): Int = editor.caretModel.offset

        override fun close() {}
      }
    }
    else {
      object : OffsetProvider {
        val myTopLeftCornerMarker = editor.document.createRangeMarker(position.topLeftCornerOffset, position.topLeftCornerOffset)

        override fun getOffset(): Int = myTopLeftCornerMarker.startOffset

        override fun close() {
          myTopLeftCornerMarker.dispose()
        }
      }
    }
  }

  private interface OffsetProvider : AutoCloseable {
    fun getOffset(): Int
  }

  data class Position(val topLeftCornerOffset: Int, val viewportShift: Int)

}

fun ScrollingModel.withoutAnimation(task: ScrollingModel.() -> Unit) {
  disableAnimation()
  try {
    task()
  }
  finally {
    enableAnimation()
  }
}