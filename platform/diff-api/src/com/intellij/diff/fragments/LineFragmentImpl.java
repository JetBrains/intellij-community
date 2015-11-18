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

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LineFragmentImpl implements LineFragment {
  private static final Logger LOG = Logger.getInstance(LineFragmentImpl.class);

  private final int myStartLine1;
  private final int myEndLine1;
  private final int myStartLine2;
  private final int myEndLine2;

  private final int myStartOffset1;
  private final int myEndOffset1;
  private final int myStartOffset2;
  private final int myEndOffset2;

  @Nullable private final List<DiffFragment> myInnerFragments;

  public LineFragmentImpl(int startLine1, int endLine1, int startLine2, int endLine2,
                          int startOffset1, int endOffset1, int startOffset2, int endOffset2) {
    this(startLine1, endLine1, startLine2, endLine2,
         startOffset1, endOffset1, startOffset2, endOffset2,
         null);
  }

  public LineFragmentImpl(@NotNull LineFragment fragment, @Nullable List<DiffFragment> fragments) {
    this(fragment.getStartLine1(), fragment.getEndLine1(), fragment.getStartLine2(), fragment.getEndLine2(),
         fragment.getStartOffset1(), fragment.getEndOffset1(), fragment.getStartOffset2(), fragment.getEndOffset2(),
         fragments);
  }

  public LineFragmentImpl(int startLine1, int endLine1, int startLine2, int endLine2,
                          int startOffset1, int endOffset1, int startOffset2, int endOffset2,
                          @Nullable List<DiffFragment> innerFragments) {
    myStartLine1 = startLine1;
    myEndLine1 = endLine1;
    myStartLine2 = startLine2;
    myEndLine2 = endLine2;
    myStartOffset1 = startOffset1;
    myEndOffset1 = endOffset1;
    myStartOffset2 = startOffset2;
    myEndOffset2 = endOffset2;

    myInnerFragments = dropWholeChangedFragments(innerFragments, endOffset1 - startOffset1, endOffset2 - startOffset2);

    if (myStartLine1 == myEndLine1 &&
        myStartLine2 == myEndLine2) {
      LOG.error("LineFragmentImpl should not be empty: " + toString());
    }
    if (myStartLine1 > myEndLine1 ||
        myStartLine2 > myEndLine2 ||
        myStartOffset1 > myEndOffset1 ||
        myStartOffset2 > myEndOffset2) {
      LOG.error("LineFragmentImpl is invalid: " + toString());
    }
  }

  @Override
  public int getStartLine1() {
    return myStartLine1;
  }

  @Override
  public int getEndLine1() {
    return myEndLine1;
  }

  @Override
  public int getStartLine2() {
    return myStartLine2;
  }

  @Override
  public int getEndLine2() {
    return myEndLine2;
  }

  @Override
  public int getStartOffset1() {
    return myStartOffset1;
  }

  @Override
  public int getEndOffset1() {
    return myEndOffset1;
  }

  @Override
  public int getStartOffset2() {
    return myStartOffset2;
  }

  @Override
  public int getEndOffset2() {
    return myEndOffset2;
  }

  @Nullable
  @Override
  public List<DiffFragment> getInnerFragments() {
    return myInnerFragments;
  }

  @Nullable
  private static List<DiffFragment> dropWholeChangedFragments(@Nullable List<DiffFragment> fragments, int length1, int length2) {
    if (fragments != null && fragments.size() == 1) {
      DiffFragment diffFragment = fragments.get(0);
      if (diffFragment.getStartOffset1() == 0 &&
          diffFragment.getStartOffset2() == 0 &&
          diffFragment.getEndOffset1() == length1 &&
          diffFragment.getEndOffset2() == length2) {
        return null;
      }
    }
    return fragments;
  }

  @Override
  public String toString() {
    return "LineFragmentImpl: Lines [" + myStartLine1 + ", " + myEndLine1 + ") - [" + myStartLine2 + ", " + myEndLine2 + "); " +
           "Offsets [" + myStartOffset1 + ", " + myEndOffset1 + ") - [" + myStartOffset2 + ", " + myEndOffset2 + "); " +
           "Inner " + (myInnerFragments != null ? myInnerFragments.size() : null);
  }
}
