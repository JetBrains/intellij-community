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

import com.intellij.diff.comparison.ComparisonManagerImpl;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.util.DiffUserDataKeysEx.DiffComputer;
import com.intellij.diff.util.Range;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.diff.tools.util.base.HighlightPolicy.*;
import static com.intellij.diff.tools.util.base.IgnorePolicy.*;

public class SimpleTextDiffProvider extends TwosideTextDiffProviderBase implements TwosideTextDiffProvider {
  private static final Logger LOG = Logger.getInstance(SimpleTextDiffProvider.class);

  private static final IgnorePolicy[] IGNORE_POLICIES = {DEFAULT, TRIM_WHITESPACES, IGNORE_WHITESPACES, IGNORE_WHITESPACES_CHUNKS};
  private static final HighlightPolicy[] HIGHLIGHT_POLICIES = {BY_LINE, BY_WORD, BY_WORD_SPLIT, DO_NOT_HIGHLIGHT};

  @Nullable private final DiffComputer myDiffComputer;

  public SimpleTextDiffProvider(@NotNull TextDiffSettings settings,
                                @NotNull Runnable rediff,
                                @NotNull Disposable disposable) {
    this(settings, rediff, disposable, null);
  }

  public SimpleTextDiffProvider(@NotNull TextDiffSettings settings,
                                @NotNull Runnable rediff,
                                @NotNull Disposable disposable,
                                @Nullable DiffComputer diffComputer) {
    this(settings, rediff, disposable, diffComputer, IGNORE_POLICIES, HIGHLIGHT_POLICIES);
  }

  private SimpleTextDiffProvider(@NotNull TextDiffSettings settings,
                                 @NotNull Runnable rediff,
                                 @NotNull Disposable disposable,
                                 @Nullable DiffComputer diffComputer,
                                 @NotNull IgnorePolicy[] ignorePolicies,
                                 @NotNull HighlightPolicy[] highlightPolicies) {
    super(settings, rediff, disposable, ignorePolicies, highlightPolicies);
    myDiffComputer = diffComputer;
  }

  @NotNull
  @Override
  protected List<List<LineFragment>> doCompare(@NotNull CharSequence text1,
                                               @NotNull CharSequence text2,
                                               @NotNull LineOffsets lineOffsets1,
                                               @NotNull LineOffsets lineOffsets2,
                                               @Nullable List<Range> linesRanges,
                                               @NotNull IgnorePolicy ignorePolicy,
                                               boolean innerFragments,
                                               @NotNull ProgressIndicator indicator) {
    return compareRange(myDiffComputer, text1, text2, lineOffsets1, lineOffsets2, linesRanges, ignorePolicy, innerFragments, indicator);
  }

  @NotNull
  public static List<List<LineFragment>> compareRange(@Nullable DiffComputer diffComputer,
                                                      @NotNull CharSequence text1,
                                                      @NotNull CharSequence text2,
                                                      @NotNull LineOffsets lineOffsets1,
                                                      @NotNull LineOffsets lineOffsets2,
                                                      @Nullable List<Range> linesRanges,
                                                      @NotNull IgnorePolicy ignorePolicy,
                                                      boolean innerFragments,
                                                      @NotNull ProgressIndicator indicator) {
    ComparisonPolicy policy = ignorePolicy.getComparisonPolicy();

    if (diffComputer != null && linesRanges != null) {
      LOG.error(new Throwable("Unsupported operation: ranged diff with custom DiffComputer - " + diffComputer));
    }

    ComparisonManagerImpl comparisonManager = ComparisonManagerImpl.getInstanceImpl();
    if (linesRanges == null) {
      if (diffComputer != null) {
        List<LineFragment> fragments = diffComputer.compute(text1, text2, policy, innerFragments, indicator);
        return Collections.singletonList(fragments);
      }
      else {
        List<LineFragment> fragments = comparisonManager.compareLinesInner(text1, text2, lineOffsets1, lineOffsets2,
                                                                           policy, innerFragments, indicator);
        return Collections.singletonList(fragments);
      }
    }
    else {
      List<List<LineFragment>> result = new ArrayList<>();
      for (Range range : linesRanges) {
        result.add(comparisonManager.compareLinesInner(range, text1, text2, lineOffsets1, lineOffsets2,
                                                       policy, innerFragments, indicator));
      }
      return result;
    }
  }

  public static class NoIgnore extends SimpleTextDiffProvider implements TwosideTextDiffProvider.NoIgnore {
    public NoIgnore(@NotNull TextDiffSettings settings, @NotNull Runnable rediff, @NotNull Disposable disposable) {
      this(settings, rediff, disposable, null);
    }

    public NoIgnore(@NotNull TextDiffSettings settings,
                    @NotNull Runnable rediff,
                    @NotNull Disposable disposable,
                    @Nullable DiffComputer diffComputer) {
      super(settings, rediff, disposable, diffComputer, IGNORE_POLICIES, ArrayUtil.remove(HIGHLIGHT_POLICIES, DO_NOT_HIGHLIGHT));
    }

    @NotNull
    @Override
    public List<LineFragment> compare(@NotNull CharSequence text1,
                                      @NotNull CharSequence text2,
                                      @NotNull ProgressIndicator indicator) {
      //noinspection ConstantConditions
      return super.compare(text1, text2, indicator);
    }

    @NotNull
    @Override
    public List<List<LineFragment>> compare(@NotNull CharSequence text1,
                                            @NotNull CharSequence text2,
                                            @NotNull List<Range> linesRanges,
                                            @NotNull ProgressIndicator indicator) {
      //noinspection ConstantConditions
      return super.compare(text1, text2, linesRanges, indicator);
    }
  }
}
