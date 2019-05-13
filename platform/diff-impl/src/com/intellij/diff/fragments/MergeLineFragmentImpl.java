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

public class MergeLineFragmentImpl implements MergeLineFragment {
  private final int myStartLine1;
  private final int myEndLine1;
  private final int myStartLine2;
  private final int myEndLine2;
  private final int myStartLine3;
  private final int myEndLine3;

  public MergeLineFragmentImpl(int startLine1,
                               int endLine1,
                               int startLine2,
                               int endLine2,
                               int startLine3,
                               int endLine3) {
    myStartLine1 = startLine1;
    myEndLine1 = endLine1;
    myStartLine2 = startLine2;
    myEndLine2 = endLine2;
    myStartLine3 = startLine3;
    myEndLine3 = endLine3;
  }

  public MergeLineFragmentImpl(@NotNull MergeLineFragment fragment) {
    this(fragment.getStartLine(ThreeSide.LEFT), fragment.getEndLine(ThreeSide.LEFT),
         fragment.getStartLine(ThreeSide.BASE), fragment.getEndLine(ThreeSide.BASE),
         fragment.getStartLine(ThreeSide.RIGHT), fragment.getEndLine(ThreeSide.RIGHT));
  }

  public MergeLineFragmentImpl(@NotNull MergeRange range) {
    this(range.start1, range.end1, range.start2, range.end2, range.start3, range.end3);
  }

  @Override
  public int getStartLine(@NotNull ThreeSide side) {
    return side.select(myStartLine1, myStartLine2, myStartLine3);
  }

  @Override
  public int getEndLine(@NotNull ThreeSide side) {
    return side.select(myEndLine1, myEndLine2, myEndLine3);
  }
}
