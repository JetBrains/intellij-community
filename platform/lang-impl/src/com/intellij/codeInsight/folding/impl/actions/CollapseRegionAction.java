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

public final class CollapseRegionAction extends EditorAction implements ActionRemoteBehaviorSpecification.Frontend {
  public CollapseRegionAction() {
    super(new BaseFoldingHandler() {
      @Override
      public void doExecute(final @NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
        final int[] lines = editor.getCaretModel().getAllCarets().stream()
          .map(Caret::getLogicalPosition)
          .mapToInt(it -> it.line)
          .toArray();

        Runnable processor = () -> {
          for (int line : lines) {
            FoldRegion region = FoldingUtil.findFoldRegionStartingAtLine(editor, line);
            if (region != null && region.isExpanded()) {
              region.setExpanded(false);
            }
            else {
              int offset = editor.getCaretModel().getOffset();
              FoldRegion[] regions = FoldingUtil.getFoldRegionsAtOffset(editor, offset);
              for (FoldRegion region1 : regions) {
                if (region1.isExpanded()) {
                  region1.setExpanded(false);
                  break;
                }
              }
            }
          }
        };
        editor.getFoldingModel().runBatchFoldingOperation(processor);
      }
    });
  }
}
