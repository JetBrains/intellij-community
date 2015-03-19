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

  public DiffLineMarkerRenderer(@NotNull TextDiffType diffType) {
    myDiffType = diffType;
  }

  @Override
  public void paint(Editor editor, Graphics g, Rectangle range) {
    Color color = myDiffType.getColor(editor);

    EditorGutterComponentEx gutter = ((EditorEx)editor).getGutterComponentEx();
    Graphics2D g2 = (Graphics2D)g;
    int x = 0;
    int y = range.y;
    int width = gutter.getWidth();
    int height = range.height;

    if (height > 2) {
      g.setColor(color);
      g.fillRect(x, y, width, height);
      DiffDrawUtil.drawChunkBorderLine(g2, x, x + width, y - 1, color);
      DiffDrawUtil.drawChunkBorderLine(g2, x, x + width, y + height - 1, color);
    }
    else {
      // range is empty - insertion or deletion
      // Draw 2 pixel line in that case
      DiffDrawUtil.drawDoubleChunkBorderLine(g2, x, x + width, y - 1, color);
    }
  }
}
