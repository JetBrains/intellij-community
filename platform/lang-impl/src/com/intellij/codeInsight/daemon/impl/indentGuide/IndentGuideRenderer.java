// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.indentGuide;

import com.intellij.formatting.visualLayer.VirtualFormattingInlaysInfo;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.view.EditorPainter;
import com.intellij.openapi.editor.impl.view.VisualLinesIterator;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.DefaultLineMarkerRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.ObjectUtils;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;

public class IndentGuideRenderer implements CustomHighlighterRenderer {
  @Override
  public void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics g) {
    int startOffset = highlighter.getStartOffset();
    Document doc = editor.getElfDocument();
    if (startOffset >= doc.getTextLength()) return;

    int endOffset = highlighter.getEndOffset();

    int off;
    int startLine = doc.getLineNumber(startOffset);

    CharSequence chars = doc.getCharsSequence();
    do {
      int start = doc.getLineStartOffset(startLine);
      int end = doc.getLineEndOffset(startLine);
      off = CharArrayUtil.shiftForward(chars, start, end, " \t");
      startLine--;
    }
    while (startLine > 1 && off < doc.getTextLength() && chars.charAt(off) == '\n');

    VisualPosition startPosition = editor.offsetToVisualPosition(off);
    VisualPosition endPosition = editor.offsetToVisualPosition(endOffset);
    paint(editor, startPosition, endPosition, off, endOffset, doc, g);
  }

  /**
   * Existence of this method allows to abstract the line being drawn form the range of highlighter it is drawn for.
   * That can be useful for making it possible to avoid drawing the line on top of text
   * (examples of issues it can be useful for are RIDER-58398 and IDEA-263469).
   * lineStartPosition and lineEndPosition define the visual line that will be drawn,
   * while startOffset and endOffset define the entire indent guide, possibly intersecting with non-whitespace text.
   */
  protected void paint(
    @NotNull Editor editor,
    @NotNull VisualPosition lineStartPosition,
    @NotNull VisualPosition lineEndPosition,
    int startOffset,
    int endOffset,
    @NotNull Document doc,
    @NotNull Graphics g
  ) {
    int indentColumn = lineStartPosition.column;
    if (indentColumn < 0) return; // 0 is possible in Rider virtual formatting, and it is logically sound

    FoldingModel foldingModel = editor.getFoldingModel();
    FoldRegion startRegion = foldingModel.getCollapsedRegionAtOffset(startOffset);
    if (startRegion != null && doc.getLineNumber(startRegion.getEndOffset()) > doc.getLineNumber(startOffset)) return;

    FoldRegion headerRegion = foldingModel.getCollapsedRegionAtOffset(doc.getLineEndOffset(doc.getLineNumber(startOffset)));
    FoldRegion tailRegion = foldingModel.getCollapsedRegionAtOffset(doc.getLineStartOffset(doc.getLineNumber(endOffset)));

    if (tailRegion != null && tailRegion == headerRegion) return;

    final var vfmtInlaysInfo = VirtualFormattingInlaysInfo.getInstance();
    final var isVfmtEnabled = vfmtInlaysInfo.isVirtualFormattingEnabled(editor);
    if (!isVfmtEnabled && indentColumn == 0) return; // 0 is impossible outside Rider virtual formatting context
    var vfmtRightShift = VirtualFormattingInlaysInfo
      .measureVirtualFormattingInlineInlays(vfmtInlaysInfo, editor, startOffset, startOffset);

    final int lineHeight = editor.getLineHeight();
    int minY = editor.visualLineToY(lineStartPosition.line) + lineHeight;
    int maxY = editor.visualLineToY(lineEndPosition.line);
    if (lineEndPosition.line == editor.offsetToVisualLine(doc.getTextLength(), false)) {
      maxY += lineHeight;
    }

    Rectangle clip = g.getClipBounds();
    if (clip != null) {
      if (clip.y >= maxY || clip.y + clip.height <= minY) {
        return;
      }
      maxY = Math.min(maxY, clip.y + clip.height);
    }

    if (minY >= maxY) return;

    int startX = editor.visualPositionToXY(lineStartPosition).x + vfmtRightShift;
    int targetX = Math.max(0, startX + EditorPainter.getIndentGuideShift(editor));
    if (clip != null && isLineOutsideClip(g, targetX, clip)) {
      return;
    }

    final boolean selected = isSelected(editor, endOffset, startOffset, lineStartPosition.column);
    final boolean stickyPainting = editor instanceof EditorImpl editorImpl && editorImpl.isStickyLinePainting();
    Color color = getIndentColor(editor, startOffset, selected, stickyPainting);
    if (color == null) {
      return;
    }

    g.setColor(color);
    // There is a possible case that indent line intersects soft wrap-introduced text. Example:
    //     this is a long line <soft-wrap>
    // that| is soft-wrapped
    //     |
    //     | <- vertical indent
    //
    // Also it's possible that no additional intersections are added because of soft wrap:
    //     this is a long line <soft-wrap>
    //     |   that is soft-wrapped
    //     |
    //     | <- vertical indent
    // We want to use the following approach then:
    //     1. Show only active indent if it crosses soft wrap-introduced text;
    //     2. Show indent as is if it doesn't intersect with soft wrap-introduced text;
    List<? extends SoftWrap> softWraps = ((EditorEx)editor).getSoftWrapModel().getRegisteredSoftWraps();
    if (selected || softWraps.isEmpty()) {
      drawLine((Graphics2D)g, targetX, minY, targetX, maxY - 1);
    }
    else {
      int startY = minY;
      int startVisualLine = lineStartPosition.line + 1;
      if (clip != null && startY < clip.y) {
        startY = clip.y;
        startVisualLine = editor.yToVisualLine(clip.y);
      }
      VisualLinesIterator it = new VisualLinesIterator((EditorImpl)editor, startVisualLine);
      while (!it.atEnd()) {
        int currY = it.getY();
        if (currY >= startY) {
          if (currY >= maxY) break;
          if (it.startsWithSoftWrap()) {
            SoftWrap softWrap = softWraps.get(it.getStartOrPrevWrapIndex());
            if (softWrap.getIndentInColumns() < indentColumn) {
              if (startY < currY) {
                drawLine((Graphics2D)g, targetX, startY, targetX, currY - 1);
              }
              startY = currY + lineHeight;
            }
          }
        }
        it.advance();
      }
      if (startY < maxY) {
        drawLine((Graphics2D)g, targetX, startY, targetX, maxY - 1);
      }
    }
  }

  /**
   * Tells whether the line of a guide at {@code x} paints no pixel of {@code clip}. The line of {@link #drawLine} stays
   * within its stroke width and one more pixel on either side of {@code x}.
   */
  private static boolean isLineOutsideClip(@NotNull Graphics g, int x, @NotNull Rectangle clip) {
    if (!(g instanceof Graphics2D graphics) || !(graphics.getStroke() instanceof BasicStroke stroke)) {
      return false;
    }
    int reach = (int)Math.ceil(stroke.getLineWidth()) + 1;
    return x + reach <= clip.x || x - reach >= clip.x + clip.width;
  }

  /**
   * Paints the line of a guide. A subclass keeps the line within the stroke width and one more pixel on either side of
   * the x coordinate. The paint skips a guide farther from the clip.
   */
  protected void drawLine(@NotNull Graphics2D g, int x1, int y1, int x2, int y2) {
    LinePainter2D.paint(g, x1, y1, x2, y2);
  }

  private static @Nullable Color getIndentColor(Editor editor, int startOffset, boolean selected, boolean stickyPainting) {
    EditorColorsScheme scheme = editor.getColorsScheme();
    if (ExperimentalUI.isNewUI()) {
      Color markerColor = getLineMarkerColor(editor, startOffset);
      if (markerColor != null) {
        Color matched = scheme.getColor(EditorColors.MATCHED_BRACES_INDENT_GUIDE_COLOR);
        return ObjectUtils.notNull(matched, markerColor);
      }
    }
    if (!selected && stickyPainting) {
      // suppress indent vertical lines on sticky lines panel
      return null;
    }
    return scheme.getColor(selected ? EditorColors.SELECTED_INDENT_GUIDE_COLOR : EditorColors.INDENT_GUIDE_COLOR);
  }

  /**
   * Returns the color of a {@link DefaultLineMarkerRenderer} of the editor markup that starts on the visual line of
   * {@code offset}. Returns {@code null} when no such line marker has a color. Of several such line markers, the first one
   * in the order of {@link #comesBefore} wins.
   */
  private static @Nullable Color getLineMarkerColor(@NotNull Editor editor, int offset) {
    Document document = editor.getElfDocument();
    FoldingModel foldingModel = editor.getFoldingModel();
    int visualLine = editor.offsetToVisualLine(offset, false);
    int lineStart = EditorUtil.getNotFoldedLineStartOffset(document, foldingModel, offset, false);
    int lineEnd = EditorUtil.getNotFoldedLineEndOffset(document, foldingModel, offset, false);
    Ref<RangeHighlighterEx> first = new Ref<>();
    ((EditorEx)editor).getMarkupModel().processRangeHighlightersOverlappingWith(lineStart, lineEnd, highlighter -> {
      if (highlighter.getLineMarkerRenderer() instanceof DefaultLineMarkerRenderer renderer &&
          renderer.getColor() != null &&
          highlighter.getStartOffset() >= lineStart &&
          editor.offsetToVisualLine(highlighter.getStartOffset(), false) == visualLine &&
          (first.isNull() || comesBefore(highlighter, first.get()))) {
        first.set(highlighter);
      }
      return true;
    });
    return first.isNull() ? null : ((DefaultLineMarkerRenderer)first.get().getLineMarkerRenderer()).getColor();
  }

  /**
   * Tells whether {@code highlighter} comes before {@code other} in the snapshot storage of the markup. The order is by the
   * start of the affected area, then by the layer from the top, then by the id.
   */
  private static boolean comesBefore(@NotNull RangeHighlighterEx highlighter, @NotNull RangeHighlighterEx other) {
    if (highlighter.getAffectedAreaStartOffset() != other.getAffectedAreaStartOffset()) {
      return highlighter.getAffectedAreaStartOffset() < other.getAffectedAreaStartOffset();
    }
    if (highlighter.getLayer() != other.getLayer()) {
      return highlighter.getLayer() > other.getLayer();
    }
    return highlighter.getId() < other.getId();
  }

  protected boolean isSelected(@NotNull Editor editor, int endOffset, int off, int indentColumn) {
    return isCaretOnGuide(editor, endOffset, off, indentColumn) && editor.getIndentsModel().getCaretIndentGuide() != null;
  }

  protected static boolean isCaretOnGuide(@NotNull Editor editor, int endOffset, int off, int indentColumn) {
    CaretModel caretModel = editor.getCaretModel();
    if (caretModel.getLogicalPosition().column != indentColumn) {
      return false;
    }
    int caretOffset = caretModel.getOffset();
    return caretOffset >= off && caretOffset < endOffset;
  }
}
