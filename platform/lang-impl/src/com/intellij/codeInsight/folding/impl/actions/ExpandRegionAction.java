// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.codeInsight.folding.impl.FoldingUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExpandRegionAction extends EditorAction implements ActionRemoteBehaviorSpecification.Frontend {
  public ExpandRegionAction() {
    super(new BaseFoldingHandler() {
      @Override
      public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
        expandRegionAtCaret(editor);
      }
    });
  }

  private static void expandRegionAtCaret(final @Nullable Editor editor) {
    if (editor == null) return;

    final int[] offsets = editor.getCaretModel().getAllCarets().stream()
      .mapToInt(Caret::getOffset)
      .toArray();
    expandRegionAtOffsets(editor, offsets);
  }

  public static void expandRegionAtOffset(final @NotNull Editor editor, final int offset) {
    expandRegionAtOffsets(editor, new int[]{offset});
  }

  public static void expandRegionAtOffsets(final @NotNull Editor editor, final int[] offsets) {
    Runnable processor = () -> {
      for (int offset : offsets) {
        final int line = editor.getDocument().getLineNumber(offset);
        FoldRegion region = FoldingUtil.findFoldRegionStartingAtLine(editor, line);
        if (region != null && !region.isExpanded()) {
          region.setExpanded(true);
        }
        else {
          FoldRegion[] regions = FoldingUtil.getFoldRegionsAtOffset(editor, offset);
          for (int i = regions.length - 1; i >= 0; i--) {
            region = regions[i];
            if (!region.isExpanded()) {
              region.setExpanded(true);
              break;
            }
          }
        }
      }
    };
    editor.getFoldingModel().runBatchFoldingOperation(processor);
  }
}
