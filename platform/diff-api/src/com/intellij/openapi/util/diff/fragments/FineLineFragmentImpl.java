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
package com.intellij.openapi.util.diff.fragments;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FineLineFragmentImpl extends LineFragmentImpl implements FineLineFragment {
  @Nullable protected final List<DiffFragment> myFragments;

  public FineLineFragmentImpl(@NotNull LineFragment fragment, @Nullable List<DiffFragment> fragments) {
    super(fragment);

    myFragments = dropWholeChangedFragments(fragments, myEndOffset1 - myStartOffset1, myEndOffset2 - myStartOffset2);
  }

  public FineLineFragmentImpl(int startLine1, int endLine1, int startLine2, int endLine2,
                              int startOffset1, int endOffset1, int startOffset2, int endOffset2,
                              @Nullable List<DiffFragment> fragments) {
    super(startLine1, endLine1, startLine2, endLine2, startOffset1, endOffset1, startOffset2, endOffset2);

    myFragments = dropWholeChangedFragments(fragments, endOffset1 - startOffset1, endOffset2 - startOffset2);
  }

  @Override
  @Nullable
  public List<DiffFragment> getFineFragments() {
    return myFragments;
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
}
