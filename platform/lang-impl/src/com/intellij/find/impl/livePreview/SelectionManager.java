// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl.livePreview;

import com.intellij.find.EditorSearchSession;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.find.FindUtil;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class SelectionManager {
  private final @NotNull SearchResults mySearchResults;
  private final boolean myHadSelectionInitially;
  private final List<FoldRegion> myRegionsToRestore = new ArrayList<>();

  public SelectionManager(@NotNull SearchResults results) {
    mySearchResults = results;
    myHadSelectionInitially = results.getEditor().getSelectionModel().hasSelection();
  }

  public void updateSelection(boolean removePreviousSelection, boolean removeAllPreviousSelections, boolean adjustScrollPosition) {
    Editor editor = mySearchResults.getEditor();
    if (removeAllPreviousSelections) {
      editor.getCaretModel().removeSecondaryCarets();
    }
    FindModel findModel = mySearchResults.getFindModel();
    FindResult cursor = mySearchResults.getCursor();
    if (cursor == null) {
      if (removePreviousSelection && !myHadSelectionInitially && findModel.isGlobal()) {
        editor.getSelectionModel().removeSelection();
      }
      return;
    }
    if (findModel.isGlobal()) {
      if (removePreviousSelection || removeAllPreviousSelections) {
        FoldingModel foldingModel = editor.getFoldingModel();
        FoldRegion[] allRegions = editor.getFoldingModel().getAllFoldRegions();

        foldingModel.runBatchFoldingOperation(() -> {
          for (FoldRegion region : myRegionsToRestore) {
            if (region.isValid()) {
              region.setExpanded(false);
            }
          }
          myRegionsToRestore.clear();
          for (FoldRegion region : allRegions) {
            if (region.isValid() && cursor.intersects(region) && !region.isExpanded()) {
              region.setExpanded(true);
              myRegionsToRestore.add(region);
            }
          }
        });
        editor.getCaretModel().moveToOffset(cursor.getEndOffset());
        TextRange withinBounds = cursor.intersection(TextRange.from(0, editor.getDocument().getTextLength()));
        if (withinBounds == null) withinBounds = TextRange.EMPTY_RANGE;
        editor.getSelectionModel().setSelection(withinBounds.getStartOffset(), withinBounds.getEndOffset());
        EditorSearchSession.logSelectionUpdate();
      }
      else {
        FindUtil.selectSearchResultInEditor(editor, cursor, -1);
      }
      if (adjustScrollPosition) {
        editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
      }
    } else {
      if (!SearchResults.insideVisibleArea(editor, cursor) && adjustScrollPosition) {
        LogicalPosition pos = editor.offsetToLogicalPosition(cursor.getStartOffset());
        editor.getScrollingModel().scrollTo(pos, ScrollType.CENTER);
      }
    }
  }

  boolean removeCurrentSelection() {
    Editor editor = mySearchResults.getEditor();
    CaretModel caretModel = editor.getCaretModel();
    Caret primaryCaret = caretModel.getPrimaryCaret();
    if (caretModel.getCaretCount() > 1) {
      caretModel.removeCaret(primaryCaret);
      return true;
    }
    else {
      primaryCaret.moveToOffset(primaryCaret.getSelectionStart());
      primaryCaret.removeSelection();
      return false;
    }
  }

  public boolean isSelected(@NotNull FindResult result) {
    Editor editor = mySearchResults.getEditor();
    int endOffset = result.getEndOffset();
    for (Caret caret : editor.getCaretModel().getAllCarets()) {
      if (caret.getOffset() == endOffset) return true;
    }
    return false;
  }
}
