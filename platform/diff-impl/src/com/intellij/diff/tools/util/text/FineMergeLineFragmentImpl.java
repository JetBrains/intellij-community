// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.text;

import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.fragments.MergeLineFragmentImpl;
import com.intellij.diff.util.MergeConflictType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class FineMergeLineFragmentImpl extends MergeLineFragmentImpl implements FineMergeLineFragment {
  private final @NotNull MergeConflictType myConflictType;
  private final @Nullable MergeInnerDifferences myInnerDifferences;

  public FineMergeLineFragmentImpl(@NotNull MergeLineFragment fragment,
                                   @NotNull MergeConflictType conflictType,
                                   @Nullable MergeInnerDifferences innerDifferences) {
    super(fragment);
    myConflictType = conflictType;
    myInnerDifferences = innerDifferences;
  }

  @Override
  public @NotNull MergeConflictType getConflictType() {
    return myConflictType;
  }

  @Override
  public @Nullable MergeInnerDifferences getInnerFragments() {
    return myInnerDifferences;
  }
}
