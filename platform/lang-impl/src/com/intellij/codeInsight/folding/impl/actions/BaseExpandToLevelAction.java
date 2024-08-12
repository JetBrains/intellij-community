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

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;

public abstract class BaseExpandToLevelAction extends EditorAction implements ActionRemoteBehaviorSpecification.Frontend {
  protected BaseExpandToLevelAction(final int level, final boolean expandAll) {
    super(new BaseFoldingHandler() {
      @Override
      protected void doExecute(final @NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
        if (caret == null) {
          caret = editor.getCaretModel().getPrimaryCaret();
        }
        int offset = caret.getOffset();
        FoldRegion rootRegion = null;
        if (!expandAll) {
          rootRegion = FoldingUtil.findFoldRegionStartingAtLine(editor, editor.getDocument().getLineNumber(offset));
          if (rootRegion == null) {
            FoldRegion[] regions = FoldingUtil.getFoldRegionsAtOffset(editor, offset);
            if (regions.length > 0) {
              rootRegion = regions[0];
            }
          }
          if (rootRegion == null) {
            return;
          }
        }
        final FoldRegion root = rootRegion;
        final int[] rootLevel = new int[] {root == null ? 1 : -1};

        editor.getFoldingModel().runBatchFoldingOperation(() -> {
          Iterator<FoldRegion> regionTreeIterator = FoldingUtil.createFoldTreeIterator(editor);
          Deque<FoldRegion> currentStack = new LinkedList<>();
          while (regionTreeIterator.hasNext()) {
            FoldRegion region = regionTreeIterator.next();
            while (!currentStack.isEmpty() && !isChild(currentStack.peek(), region)) {
              if (currentStack.remove() == root) {
                rootLevel[0] = -1;
              }
            }
            currentStack.push(region);
            int currentLevel = currentStack.size();

            if (region == root) {
              rootLevel[0] = currentLevel;
            }
            if (rootLevel[0] >= 0) {
              int relativeLevel = currentLevel - rootLevel[0];

              if (relativeLevel < level) {
                region.setExpanded(true);
              }
              else if (relativeLevel == level) {
                region.setExpanded(false);
              }
            }
          }
        });
      }
    });
  }

  private static boolean isChild(@NotNull FoldRegion parent, @NotNull FoldRegion child) {
    return child.getStartOffset() >= parent.getStartOffset() && child.getEndOffset() <= parent.getEndOffset();
  }
}
