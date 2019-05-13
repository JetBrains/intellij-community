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
package com.intellij.diff.fragments;

import com.intellij.diff.util.MergeRange;
import com.intellij.diff.util.ThreeSide;
import org.jetbrains.annotations.NotNull;

public class MergeWordFragmentImpl implements MergeWordFragment {
  private final int myStartOffset1;
  private final int myEndOffset1;
  private final int myStartOffset2;
  private final int myEndOffset2;
  private final int myStartOffset3;
  private final int myEndOffset3;

  public MergeWordFragmentImpl(int startOffset1,
                               int endOffset1,
                               int startOffset2,
                               int endOffset2,
                               int startOffset3,
                               int endOffset3) {
    myStartOffset1 = startOffset1;
    myEndOffset1 = endOffset1;
    myStartOffset2 = startOffset2;
    myEndOffset2 = endOffset2;
    myStartOffset3 = startOffset3;
    myEndOffset3 = endOffset3;
  }

  public MergeWordFragmentImpl(@NotNull MergeRange range) {
    this(range.start1, range.end1, range.start2, range.end2, range.start3, range.end3);
  }

  @Override
  public int getStartOffset(@NotNull ThreeSide side) {
    return side.select(myStartOffset1, myStartOffset2, myStartOffset3);
  }

  @Override
  public int getEndOffset(@NotNull ThreeSide side) {
    return side.select(myEndOffset1, myEndOffset2, myEndOffset3);
  }
}
