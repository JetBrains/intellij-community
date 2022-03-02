// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.util.Side;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UnifiedDiffModel {
  @NotNull private final UnifiedDiffViewer myViewer;

  @Nullable private ChangedBlockData myData = null;
  @NotNull private ThreeState myIsContentsEqual = ThreeState.UNSURE;

  @NotNull private final List<UnifiedDiffChangeUi> myPresentations = new ArrayList<>();
  @NotNull private final List<RangeMarker> myGuardedRangeBlocks = new ArrayList<>();

  public UnifiedDiffModel(@NotNull UnifiedDiffViewer viewer) {
    myViewer = viewer;
  }

  public boolean isValid() {
    return myData != null;
  }

  @NotNull
  public ThreeState isContentsEqual() {
    return myIsContentsEqual;
  }

  @Nullable
  public ChangedBlockData getData() {
    return myData;
  }

  @Nullable
  public List<UnifiedDiffChange> getDiffChanges() {
    ChangedBlockData data = myData;
    return data != null ? data.getDiffChanges() : null;
  }

  @Nullable
  public LineNumberConvertor getLineNumberConvertor(@NotNull Side side) {
    ChangedBlockData data = myData;
    return data != null ? data.getLineNumberConvertor(side) : null;
  }

  public void setChanges(@NotNull List<UnifiedDiffChange> changes,
                         boolean isContentsEqual,
                         @NotNull List<RangeMarker> guardedBlocks,
                         @NotNull LineNumberConvertor convertor1,
                         @NotNull LineNumberConvertor convertor2,
                         @NotNull List<HighlightRange> ranges) {
    assert myPresentations.isEmpty() && myGuardedRangeBlocks.isEmpty() && myData == null;

    for (UnifiedDiffChange change : changes) {
      UnifiedDiffChangeUi changeUi = myViewer.createUi(change);
      changeUi.installHighlighter();
      myPresentations.add(changeUi);
    }

    myGuardedRangeBlocks.addAll(guardedBlocks);

    myData = new ChangedBlockData(changes, convertor1, convertor2, ranges);
    myIsContentsEqual = ThreeState.fromBoolean(isContentsEqual);
  }

  public void clear() {
    for (UnifiedDiffChangeUi changeUi : myPresentations) {
      changeUi.destroyHighlighter();
    }
    myPresentations.clear();

    DocumentEx document = myViewer.getEditor().getDocument();
    for (RangeMarker block : myGuardedRangeBlocks) {
      document.removeGuardedBlock(block);
    }
    myGuardedRangeBlocks.clear();

    myData = null;
    myIsContentsEqual = ThreeState.UNSURE;
  }

  public void updateGutterActions() {
    for (UnifiedDiffChangeUi changeUi : myPresentations) {
      changeUi.updateGutterActions();
    }
  }

  public static class ChangedBlockData {
    @NotNull private final List<UnifiedDiffChange> myDiffChanges;
    @NotNull private final LineNumberConvertor myLineNumberConvertor1;
    @NotNull private final LineNumberConvertor myLineNumberConvertor2;
    @NotNull private final List<HighlightRange> myRanges;

    ChangedBlockData(@NotNull List<UnifiedDiffChange> diffChanges,
                     @NotNull LineNumberConvertor lineNumberConvertor1,
                     @NotNull LineNumberConvertor lineNumberConvertor2,
                     @NotNull List<HighlightRange> ranges) {
      myDiffChanges = Collections.unmodifiableList(diffChanges);
      myLineNumberConvertor1 = lineNumberConvertor1;
      myLineNumberConvertor2 = lineNumberConvertor2;
      myRanges = Collections.unmodifiableList(ranges);
    }

    @NotNull
    public List<UnifiedDiffChange> getDiffChanges() {
      return myDiffChanges;
    }

    @NotNull
    public LineNumberConvertor getLineNumberConvertor(@NotNull Side side) {
      return side.select(myLineNumberConvertor1, myLineNumberConvertor2);
    }

    @NotNull
    public List<HighlightRange> getRanges() {
      return myRanges;
    }
  }
}
