// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.text;

import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.util.Range;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface TwosideTextDiffProvider extends TextDiffProvider {
  @Nullable
  List<LineFragment> compare(@NotNull CharSequence text1,
                             @NotNull CharSequence text2,
                             @NotNull ProgressIndicator indicator);

  @Nullable
  List<List<LineFragment>> compare(@NotNull CharSequence text1,
                                   @NotNull CharSequence text2,
                                   @NotNull List<? extends Range> linesRanges,
                                   @NotNull ProgressIndicator indicator);


  /**
   * Some custom diff algorithms may not be suitable for general use.
   * <p/>
   * If true, then callers CANNOT assume that returned fragments conform to any otherwise common constraints.
   * That's it:
   * <ul>
   * <li>The 'Range' argument may be ignored</li>
   * <li>The 'ComparisonPolicy' argument may be ignored</li>
   * <li>The resulting fragments may be UNSORTABLE (with movement detection applied)</li>
   * <li>The resulting fragments may OVERLAP (with multiple-copy detection applied)</li>
   * </ul>
   *
   * @see com.intellij.openapi.vcs.changes.actions.diff.lst.SimpleLocalChangeListDiffViewer
   * @see com.intellij.diff.tools.util.base.IgnorePolicy#IGNORE_LANGUAGE_SPECIFIC_CHANGES
   */
  default boolean noFitnessForParticularPurposePromised() {
    return false;
  }

  default boolean isHighlightingDisabled() {
    return false;
  }

  interface NoIgnore extends TwosideTextDiffProvider {
    @NotNull
    @Override
    List<LineFragment> compare(@NotNull CharSequence text1,
                               @NotNull CharSequence text2,
                               @NotNull ProgressIndicator indicator);

    @NotNull
    @Override
    List<List<LineFragment>> compare(@NotNull CharSequence text1,
                                     @NotNull CharSequence text2,
                                     @NotNull List<? extends Range> linesRanges,
                                     @NotNull ProgressIndicator indicator);
  }
}
