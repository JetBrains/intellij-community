// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.arrangement.engine;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Denis Zhdanov
 */
public class RestoreFoldArrangementCallback implements ArrangementCallback {

  @NotNull private final  Editor           myEditor;

  public RestoreFoldArrangementCallback(@NotNull Editor editor) {
    myEditor = editor;
  }

  @Override
  public void afterArrangement(@NotNull final List<ArrangementMoveInfo> moveInfos) {
    // Restore state for the PSI elements not affected by arrangement.
    Project project = myEditor.getProject();
    if (project != null) {
      final FoldRegion[] regions = myEditor.getFoldingModel().getAllFoldRegions();
      final List<FoldRegionInfo> foldRegionsInfo = new ArrayList<>();
      for (FoldRegion region : regions) {
        final FoldRegionInfo info = new FoldRegionInfo(region.getStartOffset(), region.getEndOffset(), region.isExpanded());
        foldRegionsInfo.add(info);
      }

      final CodeFoldingManager foldingManager = CodeFoldingManager.getInstance(project);
      foldingManager.updateFoldRegions(myEditor);
      myEditor.getFoldingModel().runBatchFoldingOperation(() -> {
        for (FoldRegionInfo info : foldRegionsInfo) {
          final FoldRegion foldRegion = foldingManager.findFoldRegion(myEditor, info.myStart, info.myEnd);
          if (foldRegion != null) {
            foldRegion.setExpanded(info.myIsExpanded);
          }
        }
      });
    }
  }

  private static final class FoldRegionInfo {
    private final int myStart;
    private final int myEnd;
    private final boolean myIsExpanded;

    private FoldRegionInfo(int start, int end, boolean expanded) {
      myStart = start;
      myEnd = end;
      myIsExpanded = expanded;
    }
  }
}
