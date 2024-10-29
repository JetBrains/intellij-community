// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.util.text;

import com.intellij.diff.comparison.ComparisonManagerImpl;
import com.intellij.diff.comparison.InnerFragmentsPolicy;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.lang.DiffIgnoredRangeProvider;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.util.Range;
import com.intellij.diff.util.Side;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import static com.intellij.diff.tools.util.base.HighlightPolicy.*;
import static com.intellij.diff.tools.util.base.IgnorePolicy.*;

@ApiStatus.Internal
public class SmartTextDiffProvider extends TwosideTextDiffProviderBase implements TwosideTextDiffProvider {
  public static final IgnorePolicy[] IGNORE_POLICIES = {DEFAULT, TRIM_WHITESPACES, IGNORE_WHITESPACES, IGNORE_WHITESPACES_CHUNKS, FORMATTING};
  public static final HighlightPolicy[] HIGHLIGHT_POLICIES = {BY_LINE, BY_WORD, BY_WORD_SPLIT, BY_CHAR, DO_NOT_HIGHLIGHT};

  @Nullable private final Project myProject;
  @NotNull private final DiffContent myContent1;
  @NotNull private final DiffContent myContent2;
  @NotNull private final DiffIgnoredRangeProvider myProvider;

  @Nullable
  public static TwosideTextDiffProvider create(@Nullable Project project,
                                               @NotNull ContentDiffRequest request,
                                               @NotNull TextDiffSettings settings,
                                               @NotNull Runnable rediff,
                                               @NotNull Disposable disposable) {
    DiffContent content1 = Side.LEFT.select(request.getContents());
    DiffContent content2 = Side.RIGHT.select(request.getContents());
    DiffIgnoredRangeProvider ignoredRangeProvider = getIgnoredRangeProvider(project, content1, content2);
    if (ignoredRangeProvider == null) return null;
    return new SmartTextDiffProvider(project, content1, content2, settings, rediff, disposable, ignoredRangeProvider);
  }

  @Nullable
  public static TwosideTextDiffProvider.NoIgnore createNoIgnore(@Nullable Project project,
                                                                @NotNull ContentDiffRequest request,
                                                                @NotNull TextDiffSettings settings,
                                                                @NotNull Runnable rediff,
                                                                @NotNull Disposable disposable) {
    DiffContent content1 = Side.LEFT.select(request.getContents());
    DiffContent content2 = Side.RIGHT.select(request.getContents());
    DiffIgnoredRangeProvider ignoredRangeProvider = getIgnoredRangeProvider(project, content1, content2);
    if (ignoredRangeProvider == null) return null;
    return new SmartTextDiffProvider.NoIgnore(project, content1, content2, settings, rediff, disposable, ignoredRangeProvider);
  }

  private SmartTextDiffProvider(@Nullable Project project,
                                @NotNull DiffContent content1,
                                @NotNull DiffContent content2,
                                @NotNull TextDiffSettings settings,
                                @NotNull Runnable rediff,
                                @NotNull Disposable disposable,
                                @NotNull DiffIgnoredRangeProvider ignoredRangeProvider) {
    this(project, content1, content2, settings, rediff, disposable, ignoredRangeProvider, IGNORE_POLICIES, HIGHLIGHT_POLICIES);
  }

  private SmartTextDiffProvider(@Nullable Project project,
                                @NotNull DiffContent content1,
                                @NotNull DiffContent content2,
                                @NotNull TextDiffSettings settings,
                                @NotNull Runnable rediff,
                                @NotNull Disposable disposable,
                                @NotNull DiffIgnoredRangeProvider ignoredRangeProvider,
                                IgnorePolicy @NotNull [] ignorePolicies,
                                HighlightPolicy @NotNull [] highlightPolicies) {
    super(settings, rediff, disposable, ignorePolicies, highlightPolicies);
    myProject = project;
    myContent1 = content1;
    myContent2 = content2;
    myProvider = ignoredRangeProvider;
  }

  @Nullable
  @Override
  protected String getText(@NotNull IgnorePolicy option) {
    if (option == FORMATTING) return myProvider.getDescription();
    return null;
  }

  @NotNull
  @Override
  protected List<List<LineFragment>> doCompare(@NotNull CharSequence text1,
                                               @NotNull CharSequence text2,
                                               @NotNull LineOffsets lineOffsets1,
                                               @NotNull LineOffsets lineOffsets2,
                                               @Nullable List<? extends Range> linesRanges,
                                               @NotNull IgnorePolicy ignorePolicy,
                                               @NotNull HighlightPolicy highlightPolicy,
                                               @NotNull ProgressIndicator indicator) {
    if (ignorePolicy == FORMATTING) {
      return compareIgnoreFormatting(text1, text2, lineOffsets1, lineOffsets2, linesRanges, highlightPolicy, indicator);
    }
    else {
      return SimpleTextDiffProvider.compareRange(null, text1, text2, lineOffsets1, lineOffsets2, linesRanges,
                                                 ignorePolicy, highlightPolicy, indicator);
    }
  }

  @NotNull
  private List<List<LineFragment>> compareIgnoreFormatting(@NotNull CharSequence text1,
                                                           @NotNull CharSequence text2,
                                                           @NotNull LineOffsets lineOffsets1,
                                                           @NotNull LineOffsets lineOffsets2,
                                                           @Nullable List<? extends Range> linesRanges,
                                                           @NotNull HighlightPolicy highlightPolicy,
                                                           @NotNull ProgressIndicator indicator) {
    InnerFragmentsPolicy fragmentsPolicy = highlightPolicy.getFragmentsPolicy();

    List<TextRange> ignoredRanges1 = myProvider.getIgnoredRanges(myProject, text1, myContent1);
    List<TextRange> ignoredRanges2 = myProvider.getIgnoredRanges(myProject, text2, myContent2);

    BitSet ignored1 = ComparisonManagerImpl.collectIgnoredRanges(ignoredRanges1);
    BitSet ignored2 = ComparisonManagerImpl.collectIgnoredRanges(ignoredRanges2);

    ComparisonManagerImpl comparisonManager = ComparisonManagerImpl.getInstanceImpl();
    if (linesRanges == null) {
      List<LineFragment> fragments = comparisonManager.compareLinesWithIgnoredRanges(text1, text2, lineOffsets1, lineOffsets2,
                                                                                     ignored1, ignored2, fragmentsPolicy, indicator);
      return Collections.singletonList(fragments);
    }
    else {
      List<List<LineFragment>> result = new ArrayList<>();
      for (Range range : linesRanges) {
        result.add(comparisonManager.compareLinesWithIgnoredRanges(range, text1, text2, lineOffsets1, lineOffsets2,
                                                                   ignored1, ignored2, fragmentsPolicy, indicator));
      }
      return result;
    }
  }

  @Nullable
  private static DiffIgnoredRangeProvider getIgnoredRangeProvider(@Nullable Project project,
                                                                  @NotNull DiffContent content1,
                                                                  @NotNull DiffContent content2) {
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-339105, EA-832803")) {
      for (DiffIgnoredRangeProvider provider : DiffIgnoredRangeProvider.EP_NAME.getExtensionList()) {
        if (provider.accepts(project, content1) &&
            provider.accepts(project, content2)) {
          return provider;
        }
      }
    }
    return null;
  }

  public static final class NoIgnore extends SmartTextDiffProvider implements TwosideTextDiffProvider.NoIgnore {
    private NoIgnore(@Nullable Project project,
                     @NotNull DiffContent content1,
                     @NotNull DiffContent content2,
                     @NotNull TextDiffSettings settings,
                     @NotNull Runnable rediff,
                     @NotNull Disposable disposable,
                     @NotNull DiffIgnoredRangeProvider ignoredRangeProvider) {
      super(project, content1, content2, settings, rediff, disposable, ignoredRangeProvider,
            IGNORE_POLICIES, ArrayUtil.remove(HIGHLIGHT_POLICIES, DO_NOT_HIGHLIGHT));
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
                                            @NotNull List<? extends Range> linesRanges,
                                            @NotNull ProgressIndicator indicator) {
      //noinspection ConstantConditions
      return super.compare(text1, text2, linesRanges, indicator);
    }
  }
}
