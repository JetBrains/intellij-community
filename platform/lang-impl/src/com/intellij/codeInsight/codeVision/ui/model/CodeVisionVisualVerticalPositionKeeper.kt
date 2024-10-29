package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.awt.Point

@ApiStatus.Internal
class CodeVisionVisualVerticalPositionKeeper(vararg editors: Editor) {
  private val map = HashMap<Editor, RangeMarkerWithOffset>()

  init {
    for (editor in editors) {
      map[editor] = keep(editor)
    }
  }

  private fun keep(editor: Editor): RangeMarkerWithOffset {
    val visibleArea = editor.scrollingModel.visibleAreaOnScrollingFinished
    val cursorPosition = editor.visualPositionToXY(editor.caretModel.visualPosition)
    val offset =
      if (visibleArea.height > 0 && (cursorPosition.y < visibleArea.y || cursorPosition.y > (visibleArea.y + visibleArea.height))) {
        val pos = Point(visibleArea.x + (visibleArea.width / 2), visibleArea.y + (visibleArea.height / 2))
        editor.logicalPositionToOffset(editor.xyToLogicalPosition(pos))
      }
      else {
        editor.caretModel.offset
      }

    val rangeMarker = editor.document.createRangeMarker(offset, offset)
    val offsetToXY = editor.offsetToXY(offset)
    val shift = offsetToXY.y - visibleArea.y

    return RangeMarkerWithOffset(rangeMarker, shift)
  }

  fun restoreOriginalLocation() {
    map.forEach { (editor, value) ->
      restoreOriginalLocation(editor, value)
    }
    map.clear()
  }

  private fun restoreOriginalLocation(editor: Editor, pair: RangeMarkerWithOffset) {
    val newLocation = editor.offsetToXY(pair.range.startOffset)
    editor.scrollingModel.disableAnimation()
    editor.scrollingModel.scrollVertically(newLocation.y - pair.shift)
    editor.scrollingModel.enableAnimation()
    pair.dispose()
  }

  class RangeMarkerWithOffset(val range: RangeMarker, val shift: Int) : Disposable {
    override fun dispose() {
      Disposer.dispose { range }
    }
  }
}