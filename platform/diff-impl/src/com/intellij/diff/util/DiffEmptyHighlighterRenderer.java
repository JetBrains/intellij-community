// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.CommonProcessors;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

@ApiStatus.Internal
public class DiffEmptyHighlighterRenderer implements CustomHighlighterRenderer {
  @NotNull private final TextDiffType myDiffType;

  public DiffEmptyHighlighterRenderer(@NotNull TextDiffType diffType) {
    myDiffType = diffType;
  }

  @Override
  public void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics g) {
    if (DiffUtil.isUserDataFlagSet(DiffDrawUtil.EDITOR_WITH_HIGH_PRIORITY_RENDERER, editor)) {
      MarkupModelEx markupModel = (MarkupModelEx)editor.getMarkupModel();
      CommonProcessors.FindProcessor<RangeHighlighterEx> processor = new CommonProcessors.FindProcessor<>() {
        @Override
        protected boolean accept(RangeHighlighterEx ex) {
          return ex.getLayer() > highlighter.getLayer() &&
                 ex.getCustomRenderer() instanceof DiffDrawUtil.DiffLayeredRendererMarker;
        }
      };
      markupModel.processRangeHighlightersOverlappingWith(highlighter.getStartOffset(), highlighter.getEndOffset(), processor);
      if (processor.isFound()) return; // range with higher layerPriority found
    }

    g.setColor(myDiffType.getColor(editor));
    Point point = editor.logicalPositionToXY(editor.offsetToLogicalPosition(highlighter.getStartOffset()));
    g.fillRect(point.x - JBUIScale.scale(1), point.y, JBUIScale.scale(2), editor.getLineHeight());
  }
}
