// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@ApiStatus.Internal
public final class ExpandAllRegionsAction extends EditorAction implements ActionRemoteBehaviorSpecification.Frontend {
  public ExpandAllRegionsAction() {
    super(new BaseFoldingHandler() {
      @Override
      public void doExecute(final @NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
        Project project = editor.getProject();
        assert project != null;
        CodeFoldingManager codeFoldingManager = CodeFoldingManager.getInstance(project);

        final List<FoldRegion> regions = getFoldRegionsForSelection(editor, caret);
        twoStepFoldToggling(editor, regions, (region) -> expandInFirstStep(codeFoldingManager, region), true);
      }
    });
  }

  /**
   * Implements a two-step collapse/expand action behavior:
   * <ol>
   *   <li>First, collapses/expands all regions where #toggleFoldingInFirstStep returns true;</li>
   *   <li>Second, collapses/expands all regions if the first step produced no effects.</li>
   * </ol>
   */
  public static void twoStepFoldToggling(@NotNull Editor editor,
                                         @NotNull List<@NotNull FoldRegion> regions,
                                         Function<@NotNull FoldRegion, @NotNull Boolean> toggleFoldingInFirstStep,
                                         boolean expand) {
    FoldingModel foldingModel = editor.getFoldingModel();
    List<FoldRegion> expandedRegions = new ArrayList<>();
    foldingModel.runBatchFoldingOperation(() -> {
      for (FoldRegion region : regions) {
        // apply step 1: (un-)fold those AST elements according to toggleFoldingInFirstStep()
        if (toggleFoldingInFirstStep.apply(region)) {
          region.setExpanded(expand);
          expandedRegions.add(region);
        }
      }
    });

    for (FoldRegion expandedRegion : expandedRegions) {
      FoldRegion collapsedRegion = foldingModel.getCollapsedRegionAtOffset(expandedRegion.getStartOffset());
      if (collapsedRegion == null || !collapsedRegion.shouldNeverExpand()) {
        // step 1 produced visible changes
        return;
      }
    }

    // apply step 2: (un-)fold _all_ AST elements
    foldingModel.runBatchFoldingOperation(() -> {
      for (FoldRegion region : regions) {
        region.setExpanded(expand);
      }
    });
  }

  private static boolean expandInFirstStep(@NotNull CodeFoldingManager codeFoldingManager, @NotNull FoldRegion region) {
    boolean collapsedByDefault = Objects.requireNonNullElse(codeFoldingManager.isCollapsedByDefault(region), true);
    return !region.isExpanded() && !region.shouldNeverExpand() && !collapsedByDefault;
  }
}
