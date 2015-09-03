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
import com.intellij.openapi.editor.markup.LineMarkerRenderer;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class DiffLineMarkerRenderer implements LineMarkerRenderer {
  @NotNull private final TextDiffType myDiffType;
  private final boolean myIgnoredFoldingOutline;
  private final boolean myResolved;

  public DiffLineMarkerRenderer(@NotNull TextDiffType diffType) {
    this(diffType, false);
  }

  public DiffLineMarkerRenderer(@NotNull TextDiffType diffType, boolean ignoredFoldingOutline) {
    this(diffType, ignoredFoldingOutline, false);
  }

  public DiffLineMarkerRenderer(@NotNull TextDiffType diffType, boolean ignoredFoldingOutline, boolean resolved) {
    myDiffType = diffType;
    myIgnoredFoldingOutline = ignoredFoldingOutline;
    myResolved = resolved;
  }

  @Override
  public void paint(Editor editor, Graphics g, Rectangle range) {
    EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    Graphics2D g2 = (Graphics2D)g;
    int x1 = 0;
    int x2 = x1 + gutter.getWidth();
    int y = range.y;
    int height = range.height;

    int annotationsOffset = gutter.getAnnotationsAreaOffset();
    int annotationsWidth = gutter.getAnnotationsAreaWidth();
    if (annotationsWidth != 0) {
      drawMarker(editor, g2, x1, annotationsOffset, y, height, false);
      x1 = annotationsOffset + annotationsWidth;
    }

    if (myIgnoredFoldingOutline) {
      int xOutline = gutter.getWhitespaceSeparatorOffset();
      drawMarker(editor, g2, xOutline, x2, y, height, true);
      drawMarker(editor, g2, x1, xOutline, y, height, false);
    } else {
      drawMarker(editor, g2, x1, x2, y, height, false);
    }
  }

  private void drawMarker(Editor editor, Graphics2D g2,
                          int x1, int x2, int y, int height,
                          boolean ignoredOutline) {
    Color color = myDiffType.getColor(editor);
    if (height > 2) {
      if (ignoredOutline) {
        g2.setColor(myDiffType.getIgnoredColor(editor));
      }
      else {
        g2.setColor(color);
      }
      if (!myResolved) g2.fillRect(x1, y, x2 - x1, height);

      DiffDrawUtil.drawChunkBorderLine(g2, x1, x2, y - 1, color, false, myResolved);
      DiffDrawUtil.drawChunkBorderLine(g2, x1, x2, y + height - 1, color, false, myResolved);
    }
    else {
      // range is empty - insertion or deletion
      // Draw 2 pixel line in that case
      DiffDrawUtil.drawChunkBorderLine(g2, x1, x2, y - 1, color, true, myResolved);
    }
  }
}
