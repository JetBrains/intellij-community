package com.intellij.codeInsight.codeVision.ui.renderers.painters

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData
import com.intellij.codeInsight.codeVision.ui.model.ProjectCodeVisionModel
import com.intellij.codeInsight.codeVision.ui.model.RangeCodeVisionModel
import com.intellij.codeInsight.codeVision.ui.renderers.providers.painter
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle

@ApiStatus.Internal
class CodeVisionListPainter(
  private val delimiterPainter: ICodeVisionGraphicPainter = DelimiterPainter(),
  private val theme: CodeVisionTheme
) : ICodeVisionEntryBasePainter<CodeVisionListData?> {


  private var loadingPainter: CodeVisionStringPainter = CodeVisionStringPainter("Loading...")

  private fun getRelativeBounds(
    editor: Editor,
    state: RangeCodeVisionModel.InlayState,
    value: CodeVisionListData?
  ): Map<CodeVisionEntry, Rectangle> {
    val map = HashMap<CodeVisionEntry, Rectangle>()
    value ?: return map

    var x = theme.left
    val y = 0
    val delimiterWidth = delimiterPainter.size(editor, state).width

    for ((index, it) in value.visibleLens.withIndex()) {
      val painter = it.painter()

      val size = painter.size(editor, state, it)
      map[it] = Rectangle(x, y, size.width, size.height)

      x += size.width

      if (index < value.visibleLens.size - 1) {
        x += delimiterWidth
      }
    }
    val moreEntry = value.projectModel.moreEntry

    x += delimiterWidth
    val size = moreEntry.painter().size(editor, state, moreEntry)
    map[moreEntry] = Rectangle(x, y, size.width, size.height)

    return map
  }

  override fun paint(
    editor: Editor,
    textAttributes: TextAttributes,
    g: Graphics,
    value: CodeVisionListData?,
    point: Point,
    state: RangeCodeVisionModel.InlayState,
    hovered: Boolean,
    hoveredEntry: CodeVisionEntry?
  ) {

    var x = point.x + theme.left
    val y = point.y + theme.top + (editor as EditorImpl).ascent

    if (value == null || value.visibleLens.isEmpty()) {
      loadingPainter.paint(editor, textAttributes, g, Point(x, y), state, hovered)
      return
    }

    val relativeBounds = getRelativeBounds(editor, state, value)

    val delimiterWidth = delimiterPainter.size(editor, state).width
    for ((index, it) in value.visibleLens.withIndex()) {
      val painter = it.painter()

      val size = relativeBounds[it] ?: continue

      painter.paint(editor, textAttributes, g, it, Point(x, y), state, it == hoveredEntry, hoveredEntry)
      x += size.width

      if (index < value.visibleLens.size - 1 || hovered) {
        delimiterPainter.paint(editor, textAttributes, g, Point(x, y), state, false)
        x += delimiterWidth
      }
    }

    if (hovered) {
      val moreEntry = value.projectModel.moreEntry
      moreEntry.painter().paint(
        editor,
        textAttributes,
        g,
        moreEntry,
        Point(x, y),
        state,
        moreEntry == hoveredEntry,
        hoveredEntry
      )
    }

  }

  override fun size(
    editor: Editor,
    state: RangeCodeVisionModel.InlayState,
    value: CodeVisionListData?
  ): Dimension {
    if (value == null) {
      return loadingSize(editor, state)
    }
    val moreEntry = value.projectModel.moreEntry
    val settingsWidth = moreEntry.painter().size(editor, state, moreEntry).width
    val list = value.visibleLens.map { it.painter().size(editor, state, it).width }
    return if (value.visibleLens.isEmpty()) {
      loadingSize(editor, state)
    }
    else {
      val delimiterWidth = delimiterPainter.size(editor, state).width
      Dimension(
        list.sum() + (delimiterWidth * list.size - 1) + theme.left + theme.right + settingsWidth,
        editor.lineHeight + theme.top + theme.bottom
      )
    }
  }

  private fun loadingSize(editor: Editor, state: RangeCodeVisionModel.InlayState) =
    Dimension(
      loadingPainter.size(editor, state).width + theme.left + theme.right,
      editor.lineHeight + theme.top + theme.bottom
    )

  private fun isHovered(x: Int, y: Int, size: Rectangle): Boolean {
    return x >= size.x && x <= (size.x + size.width)
  }

  fun hoveredEntry(editor: Editor, state: RangeCodeVisionModel.InlayState, value: CodeVisionListData?, x: Int, y: Int): CodeVisionEntry? {
    val relativeBounds = getRelativeBounds(editor, state, value)
    for (entry in relativeBounds) {

      if (isHovered(x, y, entry.value)) {
        return if (entry.key.providerId == ProjectCodeVisionModel.MORE_PROVIDER_ID) {
          value?.let {
            if (it.isMoreLensActive())
              entry.key
            else {
              null
            }
          }
        }
        else entry.key
      }
    }
    return null
  }

  fun hoveredEntryBounds(
    editor: Editor,
    state: RangeCodeVisionModel.InlayState,
    value: CodeVisionListData?,
    element: CodeVisionEntry
  ): Rectangle? = getRelativeBounds(editor, state, value)[element]
}