// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.text;

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.util.Range;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public abstract class TwosideTextDiffProviderBase extends TextDiffProviderBase implements TwosideTextDiffProvider {
  protected TwosideTextDiffProviderBase(@NotNull TextDiffSettings settings,
                                        @NotNull Runnable rediff,
                                        @NotNull Disposable disposable,
                                        IgnorePolicy @NotNull [] ignorePolicies,
                                        HighlightPolicy @NotNull [] highlightPolicies) {
    super(settings, rediff, disposable, ignorePolicies, highlightPolicies);
  }

  @Override
  public boolean areVCSBoundedActionsDisabled() {
    return getIgnorePolicy() == IgnorePolicy.IGNORE_LANGUAGE_SPECIFIC_CHANGES;
  }

  @Override
  public @Nullable List<LineFragment> compare(@NotNull CharSequence text1,
                                              @NotNull CharSequence text2,
                                              @NotNull ProgressIndicator indicator) {
    LineOffsets lineOffsets1 = LineOffsetsUtil.create(text1);
    LineOffsets lineOffsets2 = LineOffsetsUtil.create(text2);

    List<List<LineFragment>> fragments = doCompare(text1, text2, lineOffsets1, lineOffsets2, null, indicator);

    if (fragments == null || fragments.isEmpty()) return null;

    return fragments.get(0);
  }

  @Override
  public @Nullable List<List<LineFragment>> compare(@NotNull CharSequence text1,
                                                    @NotNull CharSequence text2,
                                                    @NotNull List<? extends Range> linesRanges,
                                                    @NotNull ProgressIndicator indicator) {
    LineOffsets lineOffsets1 = LineOffsetsUtil.create(text1);
    LineOffsets lineOffsets2 = LineOffsetsUtil.create(text2);
    return doCompare(text1, text2, lineOffsets1, lineOffsets2, linesRanges, indicator);
  }

  private @Nullable List<List<LineFragment>> doCompare(@NotNull CharSequence text1,
                                                       @NotNull CharSequence text2,
                                                       @NotNull LineOffsets lineOffsets1,
                                                       @NotNull LineOffsets lineOffsets2,
                                                       @Nullable List<? extends Range> linesRanges,
                                                       @NotNull ProgressIndicator indicator) {
    IgnorePolicy ignorePolicy = getIgnorePolicy();
    HighlightPolicy highlightPolicy = getHighlightPolicy();

    if (!highlightPolicy.isShouldCompare()) return null;

    indicator.checkCanceled();
    List<List<LineFragment>> fragments = doCompare(text1, text2, lineOffsets1, lineOffsets2, linesRanges,
                                                   ignorePolicy, highlightPolicy, indicator);

    ComparisonPolicy policy = ignorePolicy.getComparisonPolicy();
    boolean squashFragments = highlightPolicy.isShouldSquash() && ignorePolicy.isShouldSquash();
    boolean trimFragments = ignorePolicy.isShouldTrimChunks();

    indicator.checkCanceled();

    return ContainerUtil.map(fragments, rangeFragments -> ComparisonManager.getInstance().processBlocks(rangeFragments, text1, text2,
                                                                                                        policy, squashFragments, trimFragments));
  }

  protected abstract @NotNull List<List<LineFragment>> doCompare(@NotNull CharSequence text1,
                                                                 @NotNull CharSequence text2,
                                                                 @NotNull LineOffsets lineOffsets1,
                                                                 @NotNull LineOffsets lineOffsets2,
                                                                 @Nullable List<? extends Range> linesRanges,
                                                                 @NotNull IgnorePolicy ignorePolicy,
                                                                 @NotNull HighlightPolicy highlightPolicy,
                                                                 @NotNull ProgressIndicator indicator);
}
