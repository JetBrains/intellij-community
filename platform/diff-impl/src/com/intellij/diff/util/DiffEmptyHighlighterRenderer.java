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
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class DiffEmptyHighlighterRenderer implements CustomHighlighterRenderer {
  @NotNull private final TextDiffType myDiffType;

  public DiffEmptyHighlighterRenderer(@NotNull TextDiffType diffType) {
    myDiffType = diffType;
  }

  @Override
  public void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics g) {
    g.setColor(myDiffType.getColor(editor));
    Point point = editor.logicalPositionToXY(editor.offsetToLogicalPosition(highlighter.getStartOffset()));
    int endy = point.y + editor.getLineHeight() - 1;
    g.drawLine(point.x, point.y, point.x, endy);
    g.drawLine(point.x - 1, point.y, point.x - 1, endy);
  }
}
