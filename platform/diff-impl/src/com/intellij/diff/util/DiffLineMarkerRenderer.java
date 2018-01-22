/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.util;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.LineMarkerRendererEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

class DiffLineMarkerRenderer implements LineMarkerRendererEx {
  @NotNull private final RangeHighlighter myHighlighter;
  @NotNull private final TextDiffType myDiffType;
  private final boolean myIgnoredFoldingOutline;
  private final boolean myResolved;
  private final boolean mySkipped;
  private final boolean myHideWithoutLineNumbers;

  private final boolean myEmptyRange;
  private final boolean myFirstLine;
  private final boolean myLastLine;

  public DiffLineMarkerRenderer(@NotNull RangeHighlighter highlighter,
                                @NotNull TextDiffType diffType,
                                boolean ignoredFoldingOutline,
                                boolean resolved,
                                boolean skipped,
                                boolean hideWithoutLineNumbers,
                                boolean isEmptyRange,
                                boolean isFirstLine,
                                boolean isLastLine) {
    myHighlighter = highlighter;
    myDiffType = diffType;
    myIgnoredFoldingOutline = ignoredFoldingOutline;
    myResolved = resolved;
    mySkipped = skipped;
    myHideWithoutLineNumbers = hideWithoutLineNumbers;
    myEmptyRange = isEmptyRange;
    myFirstLine = isFirstLine;
    myLastLine = isLastLine;
  }

  @Override
  public void paint(Editor editor, Graphics g, Rectangle range) {
    EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    Graphics2D g2 = (Graphics2D)g;
    int x1 = 0;
    int x2 = x1 + gutter.getWidth();

    int y1, y2;
    if (myEmptyRange && myLastLine) {
      y1 = DiffDrawUtil.lineToY(editor, DiffUtil.getLineCount(editor.getDocument()));
      y2 = y1;
    }
    else {
      int startLine = editor.getDocument().getLineNumber(myHighlighter.getStartOffset());
      int endLine = editor.getDocument().getLineNumber(myHighlighter.getEndOffset()) + 1;
      y1 = DiffDrawUtil.lineToY(editor, startLine);
      y2 = myEmptyRange ? y1 : DiffDrawUtil.lineToY(editor, endLine);
    }

    if (myEmptyRange && myFirstLine) {
      y1++;
      y2++;
    }

    if (myHideWithoutLineNumbers && !editor.getSettings().isLineNumbersShown()) {
      x1 = gutter.getWhitespaceSeparatorOffset();
    }
    else {
      int annotationsOffset = gutter.getAnnotationsAreaOffset();
      int annotationsWidth = gutter.getAnnotationsAreaWidth();
      if (annotationsWidth != 0) {
        drawMarker(editor, g2, x1, annotationsOffset, y1, y2, false, false);
        x1 = annotationsOffset + annotationsWidth;
      }
    }

    if (myIgnoredFoldingOutline || mySkipped) {
      int xOutline = gutter.getWhitespaceSeparatorOffset();
      drawMarker(editor, g2, xOutline, x2, y1, y2, true, mySkipped);
      drawMarker(editor, g2, x1, xOutline, y1, y2, false, false);
    }
    else {
      drawMarker(editor, g2, x1, x2, y1, y2, false, false);
    }
  }

  private void drawMarker(Editor editor, Graphics2D g2,
                          int x1, int x2, int y1, int y2,
                          boolean useIgnoredBackgroundColor,
                          boolean paintBorderOnly) {
    if (x1 >= x2) return;

    Color color = myDiffType.getColor(editor);
    if (y2 - y1 > 2) {
      if (!myResolved && !paintBorderOnly) {
        g2.setColor(useIgnoredBackgroundColor || mySkipped ? myDiffType.getIgnoredColor(editor) : color);
        g2.fillRect(x1, y1, x2 - x1, y2 - y1);
      }
      if (myResolved || mySkipped) {
        DiffDrawUtil.drawChunkBorderLine(g2, x1, x2, y1, color, false, myResolved);
        DiffDrawUtil.drawChunkBorderLine(g2, x1, x2, y2 - 1, color, false, myResolved);
      }
    }
    else {
      // range is empty - insertion or deletion
      // Draw 2 pixel line in that case
      DiffDrawUtil.drawChunkBorderLine(g2, x1, x2, y1 - 1, color, true, myResolved);
    }
  }

  @NotNull
  @Override
  public Position getPosition() {
    return Position.CUSTOM;
  }
}
