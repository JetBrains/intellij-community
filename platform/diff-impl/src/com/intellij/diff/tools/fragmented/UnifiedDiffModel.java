// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.util.Side;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class UnifiedDiffModel {
  private final @NotNull UnifiedDiffViewer myViewer;

  private @Nullable ChangedBlockData myData = null;
  private @NotNull ThreeState myIsContentsEqual = ThreeState.UNSURE;

  private final @NotNull List<UnifiedDiffChangeUi> myPresentations = new ArrayList<>();
  private final @NotNull List<RangeMarker> myGuardedRangeBlocks = new ArrayList<>();

  public UnifiedDiffModel(@NotNull UnifiedDiffViewer viewer) {
    myViewer = viewer;
  }

  public boolean isValid() {
    return myData != null;
  }

  public @NotNull ThreeState isContentsEqual() {
    return myIsContentsEqual;
  }

  public @Nullable ChangedBlockData getData() {
    return myData;
  }

  public @Nullable List<UnifiedDiffChange> getDiffChanges() {
    ChangedBlockData data = myData;
    return data != null ? data.getDiffChanges() : null;
  }

  public @Nullable LineNumberConvertor getLineNumberConvertor(@NotNull Side side) {
    ChangedBlockData data = myData;
    return data != null ? data.getLineNumberConvertor(side) : null;
  }

  public void setChanges(@NotNull List<UnifiedDiffChange> changes,
                         boolean isContentsEqual,
                         @NotNull List<? extends RangeMarker> guardedBlocks,
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

  public static final class ChangedBlockData {
    private final @NotNull List<UnifiedDiffChange> myDiffChanges;
    private final @NotNull LineNumberConvertor myLineNumberConvertor1;
    private final @NotNull LineNumberConvertor myLineNumberConvertor2;
    private final @NotNull List<HighlightRange> myRanges;

    ChangedBlockData(@NotNull List<UnifiedDiffChange> diffChanges,
                     @NotNull LineNumberConvertor lineNumberConvertor1,
                     @NotNull LineNumberConvertor lineNumberConvertor2,
                     @NotNull List<HighlightRange> ranges) {
      myDiffChanges = Collections.unmodifiableList(diffChanges);
      myLineNumberConvertor1 = lineNumberConvertor1;
      myLineNumberConvertor2 = lineNumberConvertor2;
      myRanges = Collections.unmodifiableList(ranges);
    }

    public @NotNull List<UnifiedDiffChange> getDiffChanges() {
      return myDiffChanges;
    }

    public @NotNull LineNumberConvertor getLineNumberConvertor(@NotNull Side side) {
      return side.select(myLineNumberConvertor1, myLineNumberConvertor2);
    }

    public @NotNull List<HighlightRange> getRanges() {
      return myRanges;
    }
  }
}
