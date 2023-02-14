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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

abstract class TwosideTextDiffProviderBase extends TextDiffProviderBase implements TwosideTextDiffProvider {
  protected TwosideTextDiffProviderBase(@NotNull TextDiffSettings settings,
                                        @NotNull Runnable rediff,
                                        @NotNull Disposable disposable,
                                        IgnorePolicy @NotNull [] ignorePolicies,
                                        HighlightPolicy @NotNull [] highlightPolicies) {
    super(settings, rediff, disposable, ignorePolicies, highlightPolicies);
  }

  @Nullable
  @Override
  public List<LineFragment> compare(@NotNull CharSequence text1,
                                    @NotNull CharSequence text2,
                                    @NotNull ProgressIndicator indicator) {
    LineOffsets lineOffsets1 = LineOffsetsUtil.create(text1);
    LineOffsets lineOffsets2 = LineOffsetsUtil.create(text2);

    List<List<LineFragment>> fragments = doCompare(text1, text2, lineOffsets1, lineOffsets2, null, indicator);

    if (fragments == null) return null;

    assert fragments.size() == 1;
    return fragments.get(0);
  }

  @Nullable
  @Override
  public List<List<LineFragment>> compare(@NotNull CharSequence text1,
                                          @NotNull CharSequence text2,
                                          @NotNull List<? extends Range> linesRanges,
                                          @NotNull ProgressIndicator indicator) {
    LineOffsets lineOffsets1 = LineOffsetsUtil.create(text1);
    LineOffsets lineOffsets2 = LineOffsetsUtil.create(text2);
    return doCompare(text1, text2, lineOffsets1, lineOffsets2, linesRanges, indicator);
  }

  @Nullable
  private List<List<LineFragment>> doCompare(@NotNull CharSequence text1,
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
    assert fragments.size() == (linesRanges != null ? linesRanges.size() : 1);

    ComparisonPolicy policy = ignorePolicy.getComparisonPolicy();
    boolean squashFragments = highlightPolicy.isShouldSquash();
    boolean trimFragments = ignorePolicy.isShouldTrimChunks();

    indicator.checkCanceled();
    return ContainerUtil.map(fragments, rangeFragments -> ComparisonManager.getInstance().processBlocks(rangeFragments, text1, text2,
                                                                                                      policy, squashFragments, trimFragments));
  }

  @NotNull
  protected abstract List<List<LineFragment>> doCompare(@NotNull CharSequence text1,
                                                        @NotNull CharSequence text2,
                                                        @NotNull LineOffsets lineOffsets1,
                                                        @NotNull LineOffsets lineOffsets2,
                                                        @Nullable List<? extends Range> linesRanges,
                                                        @NotNull IgnorePolicy ignorePolicy,
                                                        @NotNull HighlightPolicy highlightPolicy,
                                                        @NotNull ProgressIndicator indicator);
}
