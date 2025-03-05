// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.text;

import com.intellij.diff.comparison.ComparisonManagerImpl;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.InnerFragmentsPolicy;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.diff.tools.util.base.HighlightPolicy.*;
import static com.intellij.diff.tools.util.base.IgnorePolicy.*;

@ApiStatus.Internal
public class SimpleTextDiffProvider extends TwosideTextDiffProviderBase implements TwosideTextDiffProvider {
  private static final Logger LOG = Logger.getInstance(SimpleTextDiffProvider.class);

  private static final IgnorePolicy[] IGNORE_POLICIES = {DEFAULT, TRIM_WHITESPACES, IGNORE_WHITESPACES, IGNORE_WHITESPACES_CHUNKS};
  private static final HighlightPolicy[] HIGHLIGHT_POLICIES = {BY_LINE, BY_WORD, BY_WORD_SPLIT, BY_CHAR, DO_NOT_HIGHLIGHT};
  private static final HighlightPolicy[] CUSTOM_COMPUTER_HIGHLIGHT_POLICIES = {BY_LINE, BY_WORD, BY_WORD_SPLIT, DO_NOT_HIGHLIGHT};

  private final @Nullable DiffComputer myDiffComputer;

  public SimpleTextDiffProvider(@NotNull TextDiffSettings settings,
                                @NotNull Runnable rediff,
                                @NotNull Disposable disposable) {
    this(settings, rediff, disposable, null);
  }

  public SimpleTextDiffProvider(@NotNull TextDiffSettings settings,
                                @NotNull Runnable rediff,
                                @NotNull Disposable disposable,
                                @Nullable DiffComputer diffComputer) {
    this(settings, rediff, disposable, diffComputer, IGNORE_POLICIES,
         diffComputer != null ? CUSTOM_COMPUTER_HIGHLIGHT_POLICIES : HIGHLIGHT_POLICIES);
  }

  private SimpleTextDiffProvider(@NotNull TextDiffSettings settings,
                                 @NotNull Runnable rediff,
                                 @NotNull Disposable disposable,
                                 @Nullable DiffComputer diffComputer,
                                 IgnorePolicy @NotNull [] ignorePolicies,
                                 HighlightPolicy @NotNull [] highlightPolicies) {
    super(settings, rediff, disposable, ignorePolicies, highlightPolicies);
    myDiffComputer = diffComputer;
  }

  @Override
  protected @NotNull List<List<LineFragment>> doCompare(@NotNull CharSequence text1,
                                                        @NotNull CharSequence text2,
                                                        @NotNull LineOffsets lineOffsets1,
                                                        @NotNull LineOffsets lineOffsets2,
                                                        @Nullable List<? extends Range> linesRanges,
                                                        @NotNull IgnorePolicy ignorePolicy,
                                                        @NotNull HighlightPolicy highlightPolicy,
                                                        @NotNull ProgressIndicator indicator) {
    return compareRange(myDiffComputer, text1, text2, lineOffsets1, lineOffsets2, linesRanges, ignorePolicy, highlightPolicy, indicator);
  }

  public static @NotNull List<List<LineFragment>> compareRange(@Nullable DiffComputer diffComputer,
                                                               @NotNull CharSequence text1,
                                                               @NotNull CharSequence text2,
                                                               @NotNull LineOffsets lineOffsets1,
                                                               @NotNull LineOffsets lineOffsets2,
                                                               @Nullable List<? extends Range> linesRanges,
                                                               @NotNull IgnorePolicy ignorePolicy,
                                                               @NotNull HighlightPolicy highlightPolicy,
                                                               @NotNull ProgressIndicator indicator) {
    ComparisonPolicy policy = ignorePolicy.getComparisonPolicy();
    InnerFragmentsPolicy fragmentsPolicy = highlightPolicy.getFragmentsPolicy();

    if (diffComputer != null && linesRanges != null) {
      LOG.error(new Throwable("Unsupported operation: ranged diff with custom DiffComputer - " + diffComputer));
    }

    ComparisonManagerImpl comparisonManager = ComparisonManagerImpl.getInstanceImpl();
    if (linesRanges == null) {
      if (diffComputer != null) {
        List<LineFragment> fragments = diffComputer.compute(text1, text2, policy, fragmentsPolicy != InnerFragmentsPolicy.NONE, indicator);
        return Collections.singletonList(fragments);
      }
      else {
        List<LineFragment> fragments = comparisonManager.compareLinesInner(text1, text2, lineOffsets1, lineOffsets2,
                                                                           policy, fragmentsPolicy, indicator);
        return Collections.singletonList(fragments);
      }
    }
    else {
      List<List<LineFragment>> result = new ArrayList<>();
      for (Range range : linesRanges) {
        result.add(comparisonManager.compareLinesInner(range, text1, text2, lineOffsets1, lineOffsets2,
                                                       policy, fragmentsPolicy, indicator));
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

    @Override
    public @NotNull List<LineFragment> compare(@NotNull CharSequence text1,
                                               @NotNull CharSequence text2,
                                               @NotNull ProgressIndicator indicator) {
      //noinspection ConstantConditions
      return super.compare(text1, text2, indicator);
    }

    @Override
    public @NotNull List<List<LineFragment>> compare(@NotNull CharSequence text1,
                                                     @NotNull CharSequence text2,
                                                     @NotNull List<? extends Range> linesRanges,
                                                     @NotNull ProgressIndicator indicator) {
      //noinspection ConstantConditions
      return super.compare(text1, text2, linesRanges, indicator);
    }
  }
}
