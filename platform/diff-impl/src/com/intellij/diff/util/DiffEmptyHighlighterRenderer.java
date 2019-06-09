// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.ui.scale.JBUIScale;
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
    g.fillRect(point.x - JBUIScale.scale(1), point.y, JBUIScale.scale(2), editor.getLineHeight());
  }
}
