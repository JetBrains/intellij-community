/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.diff.tools.util.text;

import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.fragments.MergeLineFragmentImpl;
import com.intellij.diff.util.MergeConflictType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FineMergeLineFragmentImpl extends MergeLineFragmentImpl implements FineMergeLineFragment {
  @NotNull private final MergeConflictType myConflictType;
  @Nullable private final MergeInnerDifferences myInnerDifferences;

  public FineMergeLineFragmentImpl(@NotNull MergeLineFragment fragment,
                                   @NotNull MergeConflictType conflictType,
                                   @Nullable MergeInnerDifferences innerDifferences) {
    super(fragment);
    myConflictType = conflictType;
    myInnerDifferences = innerDifferences;
  }

  @NotNull
  @Override
  public MergeConflictType getConflictType() {
    return myConflictType;
  }

  @Nullable
  @Override
  public MergeInnerDifferences getInnerFragments() {
    return myInnerDifferences;
  }
}
