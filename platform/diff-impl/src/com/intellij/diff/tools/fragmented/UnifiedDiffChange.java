// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.DiffUtil.UpdatedLineRange;
import com.intellij.diff.util.LineRange;
import org.jetbrains.annotations.NotNull;

public class UnifiedDiffChange {
  // Boundaries of this change in myEditor. If current state is out-of-date - approximate value.
  private int myLine1;
  private int myLine2;

  private final @NotNull LineRange myDeletedRange;
  private final @NotNull LineRange myInsertedRange;
  private final @NotNull LineFragment myLineFragment;

  private final boolean myIsExcluded;
  private final boolean myIsSkipped;

  public UnifiedDiffChange(int blockStart,
                           int insertedStart,
                           int blockEnd,
                           @NotNull LineFragment lineFragment) {
    this(blockStart, insertedStart, blockEnd, lineFragment, false, false);
  }

  public UnifiedDiffChange(int blockStart,
                           int insertedStart,
                           int blockEnd,
                           @NotNull LineFragment lineFragment,
                           boolean isExcluded,
                           boolean isSkipped) {
    myLine1 = blockStart;
    myLine2 = blockEnd;
    myDeletedRange = new LineRange(blockStart, insertedStart);
    myInsertedRange = new LineRange(insertedStart, blockEnd);
    myLineFragment = lineFragment;
    myIsExcluded = isExcluded;
    myIsSkipped = isSkipped;
  }

  public int getLine1() {
    return myLine1;
  }

  public int getLine2() {
    return myLine2;
  }

  /*
   * Warning: It does not updated on document change. Check myViewer.isStateInconsistent() before use.
   */
  public @NotNull LineFragment getLineFragment() {
    return myLineFragment;
  }

  public @NotNull LineRange getDeletedRange() {
    return myDeletedRange;
  }

  public @NotNull LineRange getInsertedRange() {
    return myInsertedRange;
  }

  public boolean isExcluded() {
    return myIsExcluded;
  }

  public boolean isSkipped() {
    return myIsSkipped;
  }

  public void processChange(int oldLine1, int oldLine2, int shift) {
    UpdatedLineRange newRange = DiffUtil.updateRangeOnModification(myLine1, myLine2, oldLine1, oldLine2, shift);
    myLine1 = newRange.startLine;
    myLine2 = newRange.endLine;
  }
}
