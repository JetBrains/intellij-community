// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.formatting.visualLayer.VirtualFormattingIndentGuideInfo;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.view.EditorPainter;
import com.intellij.openapi.editor.impl.view.VisualLinesIterator;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.DefaultLineMarkerRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public class IndentGuideRenderer implements CustomHighlighterRenderer {
  @Override
  public void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics g) {
    int startOffset = highlighter.getStartOffset();
    Document doc = highlighter.getDocument();
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
    if (foldingModel.isOffsetCollapsed(startOffset)) return;

    FoldRegion headerRegion = foldingModel.getCollapsedRegionAtOffset(doc.getLineEndOffset(doc.getLineNumber(startOffset)));
    FoldRegion tailRegion = foldingModel.getCollapsedRegionAtOffset(doc.getLineStartOffset(doc.getLineNumber(endOffset)));

    if (tailRegion != null && tailRegion == headerRegion) return;

    final var virtualGuideInfo = VirtualFormattingIndentGuideInfo.getInstance();
    final var isVirtualFormattingEnabled = virtualGuideInfo.isVirtualFormattingEnabled(editor);

    if (!isVirtualFormattingEnabled && indentColumn == 0) return; // 0 is impossible outside Rider virtual formatting context

    var vfmtRightShift = 0;
    if (virtualGuideInfo.isVirtualFormattingEnabled(editor)) {
      vfmtRightShift = virtualGuideInfo
        .getVisualFormattingInlineInlays(editor, startOffset, startOffset)
        .stream()
        .map(inlay -> inlay.getWidthInPixels())
        .reduce(0, Integer::sum);
    }

    final boolean selected = isSelected(editor, endOffset, startOffset, lineStartPosition.column);
    final boolean stickyPainting = editor instanceof EditorImpl editorImpl && editorImpl.isStickyLinePainting();
    Color color = getIndentColor(editor, startOffset, selected, stickyPainting);
    if (color == null) {
      return;
    }

    final int lineHeight = editor.getLineHeight();
    Point start = editor.visualPositionToXY(lineStartPosition);
    start.x += vfmtRightShift;
    start.y += lineHeight;
    Point end = editor.visualPositionToXY(lineEndPosition);
    int maxY = end.y;
    if (lineEndPosition.line == editor.offsetToVisualPosition(doc.getTextLength()).line) {
      maxY += lineHeight;
    }

    Rectangle clip = g.getClipBounds();
    if (clip != null) {
      if (clip.y >= maxY || clip.y + clip.height <= start.y) {
        return;
      }
      maxY = Math.min(maxY, clip.y + clip.height);
    }

    if (start.y >= maxY) return;

    int targetX = Math.max(0, start.x + EditorPainter.getIndentGuideShift(editor));
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
      LinePainter2D.paint((Graphics2D)g, targetX, start.y, targetX, maxY - 1);
    }
    else {
      int startY = start.y;
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
                LinePainter2D.paint((Graphics2D)g, targetX, startY, targetX, currY - 1);
              }
              startY = currY + lineHeight;
            }
          }
        }
        it.advance();
      }
      if (startY < maxY) {
        LinePainter2D.paint((Graphics2D)g, targetX, startY, targetX, maxY - 1);
      }
    }
  }

  private static @Nullable Color getIndentColor(Editor editor, int startOffset, boolean selected, boolean stickyPainting) {
    EditorColorsScheme scheme = editor.getColorsScheme();
    if (ExperimentalUI.isNewUI()) {
      List<RangeHighlighter> highlighters = ContainerUtil.filter(editor.getMarkupModel().getAllHighlighters(),
                                                                 x -> x.getLineMarkerRenderer() instanceof DefaultLineMarkerRenderer);
      for (RangeHighlighter highlighter : highlighters) {
        DefaultLineMarkerRenderer renderer = (DefaultLineMarkerRenderer)highlighter.getLineMarkerRenderer();
        assert renderer != null;
        if (editor.offsetToVisualLine(startOffset, false) == editor.offsetToVisualLine(highlighter.getStartOffset(), false)) {
          Color color = renderer.getColor();
          if (color != null) {
            Color matched = scheme.getColor(EditorColors.MATCHED_BRACES_INDENT_GUIDE_COLOR);
            return ObjectUtils.notNull(matched, color);
          }
        }
      }
    }
    if (!selected && stickyPainting) {
      // suppress indent vertical lines on sticky lines panel
      return null;
    }
    return scheme.getColor(selected ? EditorColors.SELECTED_INDENT_GUIDE_COLOR : EditorColors.INDENT_GUIDE_COLOR);
  }

  protected boolean isSelected(@NotNull Editor editor, int endOffset, int off, int indentColumn) {
    IndentGuideDescriptor guide = editor.getIndentsModel().getCaretIndentGuide();
    if (guide == null) return false;
    return isCaretOnGuide(editor, endOffset, off, indentColumn);
  }

  protected static boolean isCaretOnGuide(@NotNull Editor editor, int endOffset, int off, int indentColumn) {
    CaretModel caretModel = editor.getCaretModel();
    int caretOffset = caretModel.getOffset();
    return caretOffset >= off && caretOffset < endOffset && caretModel.getLogicalPosition().column == indentColumn;
  }
}
