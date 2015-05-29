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
    Color color = myDiffType.getColor(editor);

    EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    Graphics2D g2 = (Graphics2D)g;
    int x1 = 0;
    int x2 = x1 + gutter.getWidth();
    int y = range.y;
    int height = range.height;

    if (height > 2) {
      if (!myResolved) {
        if (myIgnoredFoldingOutline) {
          int xOutline = gutter.getWhitespaceSeparatorOffset();

          g.setColor(myDiffType.getIgnoredColor(editor));
          g.fillRect(xOutline, y, x2 - xOutline, height);

          g.setColor(color);
          g.fillRect(x1, y, xOutline - x1, height);
        }
        else {
          g.setColor(color);
          g.fillRect(x1, y, x2 - x1, height);
        }
      }
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
