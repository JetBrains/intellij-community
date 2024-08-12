// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ExpandAllRegionsAction extends EditorAction implements ActionRemoteBehaviorSpecification.Frontend {
  public ExpandAllRegionsAction() {
    super(new BaseFoldingHandler() {
      @Override
      public void doExecute(final @NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
        Project project = editor.getProject();
        assert project != null;
        FoldingModel foldingModel = editor.getFoldingModel();
        CodeFoldingManager codeFoldingManager = CodeFoldingManager.getInstance(project);

        final List<FoldRegion> regions = getFoldRegionsForSelection(editor, caret);
        List<FoldRegion> expandedRegions = new ArrayList<>();
        foldingModel.runBatchFoldingOperation(() -> {
          for (FoldRegion region : regions) {
            // try to restore to default state at first
            Boolean collapsedByDefault = codeFoldingManager.isCollapsedByDefault(region);
            if (!region.isExpanded() && !region.shouldNeverExpand() && (collapsedByDefault == null || !collapsedByDefault)) {
              region.setExpanded(true);
              expandedRegions.add(region);
            }
          }
        });

        for (FoldRegion expandedRegion : expandedRegions) {
          FoldRegion collapsedRegion = foldingModel.getCollapsedRegionAtOffset(expandedRegion.getStartOffset());
          if (collapsedRegion == null || !collapsedRegion.shouldNeverExpand()) {
            // restoring to default state produced visible change
            return;
          }
        }

        foldingModel.runBatchFoldingOperation(() -> {
          for (FoldRegion region : regions) {
            region.setExpanded(true);
          }
        });
      }
    });
  }

}
