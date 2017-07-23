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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

abstract class TwosideTextDiffProviderBase extends TextDiffProviderBase implements TwosideTextDiffProvider {
  protected TwosideTextDiffProviderBase(@NotNull TextDiffSettings settings,
                                        @NotNull Runnable rediff,
                                        @NotNull Disposable disposable,
                                        @NotNull IgnorePolicy[] ignorePolicies,
                                        @NotNull HighlightPolicy[] highlightPolicies) {
    super(settings, rediff, disposable, ignorePolicies, highlightPolicies);
  }

  @Nullable
  @Override
  public List<LineFragment> compare(@NotNull CharSequence text1, @NotNull CharSequence text2, @NotNull ProgressIndicator indicator) {
    IgnorePolicy ignorePolicy = getIgnorePolicy();
    HighlightPolicy highlightPolicy = getHighlightPolicy();

    if (!highlightPolicy.isShouldCompare()) return null;

    ComparisonPolicy policy = ignorePolicy.getComparisonPolicy();
    boolean innerFragments = highlightPolicy.isFineFragments();
    boolean squashFragments = highlightPolicy.isShouldSquash();
    boolean trimFragments = ignorePolicy.isShouldTrimChunks();

    indicator.checkCanceled();
    List<LineFragment> fragments = doCompare(text1, text2, ignorePolicy, innerFragments, indicator);

    indicator.checkCanceled();
    return ComparisonManager.getInstance().processBlocks(fragments, text1, text2,
                                                         policy, squashFragments, trimFragments);
  }

  @NotNull
  protected abstract List<LineFragment> doCompare(@NotNull CharSequence text1,
                                                  @NotNull CharSequence text2,
                                                  @NotNull IgnorePolicy ignorePolicy,
                                                  boolean innerFragments,
                                                  @NotNull ProgressIndicator indicator);
}
