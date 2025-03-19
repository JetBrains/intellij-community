package com.intellij.codeInsight.codeVision.ui.renderers.painters

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.RangeCodeVisionModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import javax.swing.Icon

@ApiStatus.Internal
open class DefaultCodeVisionPainter<T>(
  private val iconProvider: (Project, T, RangeCodeVisionModel.InlayState) -> Icon?,
  private val textPainter: ICodeVisionEntryBasePainter<T>,
  theme: CodeVisionTheme? = null
) : ICodeVisionEntryBasePainter<T> {
  val theme: CodeVisionTheme = theme ?: CodeVisionTheme()

  private val iconPainter = CodeVisionScaledIconPainter()

  override fun paint(
    editor: Editor,
    textAttributes: TextAttributes,
    g: Graphics,
    value: T,
    point: Point,
    state: RangeCodeVisionModel.InlayState,
    hovered: Boolean,
    hoveredEntry: CodeVisionEntry?
  ) {
    val pureSize = pureSize(editor, state, value)

    var x = point.x + theme.left
    val y = point.y + theme.top

    val project = editor.project

    if (project != null) {
      val icon = iconProvider.invoke(project, value, state)
      if (icon != null) {
        val scaleFactor = iconPainter.scaleFactor(icon.iconHeight, pureSize.height)

        iconPainter.paint(editor, g, icon, Point(x, y), scaleFactor)
        x += iconPainter.width(icon, scaleFactor) + theme.iconGap
      }
    }

    textPainter.paint(editor, textAttributes, g, value, Point(x, y), state, hovered, hoveredEntry)
  }

  private fun pureSize(editor: Editor, state: RangeCodeVisionModel.InlayState, value: T): Dimension {
    val size = textPainter.size(editor, state, value)

    val project = editor.project

    val width = if (project != null) {
      val icon = iconProvider.invoke(project, value, state)
      if (icon != null) {
        val scaleFactor = iconPainter.scaleFactor(icon.iconHeight, size.height)
        theme.iconGap + iconPainter.width(icon, scaleFactor)
      }
      else 0
    }
    else {
      0
    }

    return Dimension(
      size.width + width,
      size.height
    )
  }

  override fun size(
    editor: Editor,
    state: RangeCodeVisionModel.InlayState,
    value: T
  ): Dimension {
    val size = pureSize(editor, state, value)
    val editorMetrics = editor.component.getFontMetrics(CodeVisionTheme.editorFont(editor))

    return Dimension(
      size.width + theme.left + theme.right,
      size.height + theme.top + theme.bottom + (editorMetrics.height - size.height)
    )
  }
}