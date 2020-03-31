/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @NotNull private final LineRange myDeletedRange;
  @NotNull private final LineRange myInsertedRange;
  @NotNull private final LineFragment myLineFragment;

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
  @NotNull
  public LineFragment getLineFragment() {
    return myLineFragment;
  }

  @NotNull
  public LineRange getDeletedRange() {
    return myDeletedRange;
  }

  @NotNull
  public LineRange getInsertedRange() {
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
