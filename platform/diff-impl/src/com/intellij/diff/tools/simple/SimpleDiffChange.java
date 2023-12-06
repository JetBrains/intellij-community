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
package com.intellij.diff.tools.simple;

import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.TextDiffType;
import org.jetbrains.annotations.NotNull;

public class SimpleDiffChange implements AlignableChange {
  private final int myIndex;

  private final @NotNull LineFragment myFragment;
  private final boolean myIsExcluded;
  private final boolean myIsSkipped;

  private boolean myIsValid = true;
  private boolean myIsDestroyed;
  private final int[] myLineStartShifts = new int[2];
  private final int[] myLineEndShifts = new int[2];

  public SimpleDiffChange(int index,
                          @NotNull LineFragment fragment) {
    this(index, fragment, false, false);
  }

  public SimpleDiffChange(int index,
                          @NotNull LineFragment fragment,
                          boolean isExcluded,
                          boolean isSkipped) {
    myIndex = index;

    myFragment = fragment;
    myIsExcluded = isExcluded;
    myIsSkipped = isSkipped;
  }

  public int getIndex() {
    return myIndex;
  }

  @Override
  public int getStartLine(@NotNull Side side) {
    return side.getStartLine(myFragment) + side.select(myLineStartShifts);
  }

  @Override
  public int getEndLine(@NotNull Side side) {
    return side.getEndLine(myFragment) + side.select(myLineEndShifts);
  }

  @Override
  public @NotNull TextDiffType getDiffType() {
    return DiffUtil.getLineDiffType(myFragment);
  }

  public boolean isExcluded() {
    return myIsExcluded;
  }

  public boolean isSkipped() {
    return myIsSkipped;
  }

  public boolean isValid() {
    return myIsValid;
  }

  public boolean isDestroyed() {
    return myIsDestroyed;
  }

  public @NotNull LineFragment getFragment() {
    return myFragment;
  }

  public boolean processDocumentChange(int oldLine1, int oldLine2, int shift, @NotNull Side side) {
    int line1 = getStartLine(side);
    int line2 = getEndLine(side);
    int sideIndex = side.getIndex();

    DiffUtil.UpdatedLineRange newRange = DiffUtil.updateRangeOnModification(line1, line2, oldLine1, oldLine2, shift);
    myLineStartShifts[sideIndex] += newRange.startLine - line1;
    myLineEndShifts[sideIndex] += newRange.endLine - line2;

    if (newRange.damaged) {
      myIsValid = false;
    }

    return newRange.damaged;
  }

  public void markDestroyed() {
    myIsDestroyed = true;
  }
}
