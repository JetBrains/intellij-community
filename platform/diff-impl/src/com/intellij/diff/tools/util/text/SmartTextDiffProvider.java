// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.text;

import com.intellij.diff.comparison.ComparisonManagerImpl;
import com.intellij.diff.comparison.InnerFragmentsPolicy;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.lang.DiffIgnoredRangeProvider;
import com.intellij.diff.lang.DiffLangSpecificProvider;
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.diff.tools.util.base.HighlightPolicy.*;
import static com.intellij.diff.tools.util.base.IgnorePolicy.*;

@ApiStatus.Internal
public class SmartTextDiffProvider extends TwosideTextDiffProviderBase implements TwosideTextDiffProvider {
  public static final IgnorePolicy[] IGNORE_POLICIES = {DEFAULT, TRIM_WHITESPACES, IGNORE_WHITESPACES, IGNORE_WHITESPACES_CHUNKS, FORMATTING};
  public static final HighlightPolicy[] HIGHLIGHT_POLICIES = {BY_LINE, BY_WORD, BY_WORD_SPLIT, BY_CHAR, DO_NOT_HIGHLIGHT};

  private final @Nullable Project myProject;
  private final @NotNull DiffContent myContent1;
  private final @NotNull DiffContent myContent2;
  private final @Nullable DiffIgnoredRangeProvider myProvider;
  private final @Nullable DiffLangSpecificProvider myDiffProvider;

  public static @NotNull TwosideTextDiffProvider create(@Nullable Project project,
                                                         @NotNull ContentDiffRequest request,
                                                         @NotNull TextDiffSettings settings,
                                                         @NotNull Runnable rediff,
                                                         @NotNull Disposable disposable) {
    DiffContent content1 = Side.LEFT.select(request.getContents());
    DiffContent content2 = Side.RIGHT.select(request.getContents());
    DiffIgnoredRangeProvider ignoredRangeProvider = getIgnoredRangeProvider(project, content1, content2);
    DiffLangSpecificProvider diffProvider = DiffLangSpecificProvider.findApplicable(content1, content2);
    IgnorePolicy[] ignorePolicies = getIgnorePolicies();
    return new SmartTextDiffProvider(project, content1, content2, settings, rediff, disposable, ignoredRangeProvider, diffProvider,
                                     ignorePolicies);
  }

  public static @NotNull TwosideTextDiffProvider.NoIgnore createNoIgnore(@Nullable Project project,
                                                                          @NotNull ContentDiffRequest request,
                                                                          @NotNull TextDiffSettings settings,
                                                                          @NotNull Runnable rediff,
                                                                          @NotNull Disposable disposable) {
    DiffContent content1 = Side.LEFT.select(request.getContents());
    DiffContent content2 = Side.RIGHT.select(request.getContents());
    DiffIgnoredRangeProvider ignoredRangeProvider = getIgnoredRangeProvider(project, content1, content2);
    DiffLangSpecificProvider diffAdjuster = DiffLangSpecificProvider.findApplicable(content1, content2);
    return new SmartTextDiffProvider.NoIgnore(project, content1, content2, settings, rediff, disposable, ignoredRangeProvider, diffAdjuster);
  }

  private static IgnorePolicy @NotNull [] getIgnorePolicies() {
    if (Registry.is("diff.semantic.highlighting", false)) {
      return ArrayUtil.append(IGNORE_POLICIES, IGNORE_LANGUAGE_SPECIFIC_CHANGES);
    }
    else {
      return IGNORE_POLICIES;
    }
  }

  private SmartTextDiffProvider(@Nullable Project project,
                                @NotNull DiffContent content1,
                                @NotNull DiffContent content2,
                                @NotNull TextDiffSettings settings,
                                @NotNull Runnable rediff,
                                @NotNull Disposable disposable,
                                @Nullable DiffIgnoredRangeProvider ignoredRangeProvider,
                                @Nullable DiffLangSpecificProvider diffProvider,
                                IgnorePolicy @NotNull [] ignorePolicies) {
    this(project, content1, content2, settings, rediff, disposable, ignoredRangeProvider, diffProvider, ignorePolicies, HIGHLIGHT_POLICIES);
  }

  private SmartTextDiffProvider(@Nullable Project project,
                                @NotNull DiffContent content1,
                                @NotNull DiffContent content2,
                                @NotNull TextDiffSettings settings,
                                @NotNull Runnable rediff,
                                @NotNull Disposable disposable,
                                @Nullable DiffIgnoredRangeProvider ignoredRangeProvider,
                                @Nullable DiffLangSpecificProvider diffProvider,
                                IgnorePolicy @NotNull [] ignorePolicies,
                                HighlightPolicy @NotNull [] highlightPolicies) {
    super(settings, rediff, disposable, ignorePolicies, highlightPolicies);
    myProject = project;
    myContent1 = content1;
    myContent2 = content2;
    myProvider = ignoredRangeProvider;
    myDiffProvider = diffProvider;
  }

  @Override
  protected @Nullable String getText(@NotNull IgnorePolicy option) {
    if (isFormattingPolicyApplicable(option)) return Objects.requireNonNull(myProvider).getDescription();
    if (isLanguageSpecificPolicyApplicable(option)) return Objects.requireNonNull(myDiffProvider).getDescription();
    return null;
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
    if (isFormattingPolicyApplicable(ignorePolicy)) {
      return compareIgnoreFormatting(text1, text2, lineOffsets1, lineOffsets2, linesRanges, highlightPolicy, indicator);
    }
    else if (isLanguageSpecificPolicyApplicable(ignorePolicy)) {
      return compareIgnoreLanguageSpecificChanges(text1, text2, lineOffsets1, lineOffsets2, linesRanges, ignorePolicy, highlightPolicy, indicator);
    }
    else {
      return SimpleTextDiffProvider.compareRange(null, text1, text2, lineOffsets1, lineOffsets2, linesRanges,
                                                 ignorePolicy, highlightPolicy, indicator);
    }
  }

  private @NotNull List<List<LineFragment>> compareIgnoreLanguageSpecificChanges(@NotNull CharSequence text1,
                                                                                 @NotNull CharSequence text2,
                                                                                 @NotNull LineOffsets lineOffsets1,
                                                                                 @NotNull LineOffsets lineOffsets2,
                                                                                 @Nullable List<? extends Range> linesRanges,
                                                                                 @NotNull IgnorePolicy ignorePolicy,
                                                                                 @NotNull HighlightPolicy highlightPolicy,
                                                                                 @NotNull ProgressIndicator indicator) {
    DiffLangSpecificProvider diffProvider = Objects.requireNonNull(myDiffProvider);
    if (diffProvider.getShouldPrecalculateLineFragments()) {
      List<List<LineFragment>> fragmentList =
        SimpleTextDiffProvider.compareRange(null, text1, text2, lineOffsets1, lineOffsets2, linesRanges,
                                            ignorePolicy, BY_LINE, indicator);
      return diffProvider.getPatchedLineFragments(myProject, fragmentList, text1, text2, ignorePolicy, highlightPolicy, indicator);
    } else {
      return diffProvider.getLineFragments(myProject, text1, text2, ignorePolicy, highlightPolicy, indicator);
    }
  }

  private @NotNull List<List<LineFragment>> compareIgnoreFormatting(@NotNull CharSequence text1,
                                                                    @NotNull CharSequence text2,
                                                                    @NotNull LineOffsets lineOffsets1,
                                                                    @NotNull LineOffsets lineOffsets2,
                                                                    @Nullable List<? extends Range> linesRanges,
                                                                    @NotNull HighlightPolicy highlightPolicy,
                                                                    @NotNull ProgressIndicator indicator) {
    InnerFragmentsPolicy fragmentsPolicy = highlightPolicy.getFragmentsPolicy();

    List<TextRange> ignoredRanges1 = Objects.requireNonNull(myProvider).getIgnoredRanges(myProject, text1, myContent1);
    List<TextRange> ignoredRanges2 = Objects.requireNonNull(myProvider).getIgnoredRanges(myProject, text2, myContent2);

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

  private boolean isFormattingPolicyApplicable(@NotNull IgnorePolicy ignorePolicy) {
    return ignorePolicy == FORMATTING && myProvider != null;
  }

  private boolean isLanguageSpecificPolicyApplicable(@NotNull IgnorePolicy ignorePolicy) {
    return ignorePolicy == IGNORE_LANGUAGE_SPECIFIC_CHANGES && myDiffProvider != null;
  }

  private static @Nullable DiffIgnoredRangeProvider getIgnoredRangeProvider(@Nullable Project project,
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
                     @Nullable DiffIgnoredRangeProvider ignoredRangeProvider,
                     @Nullable DiffLangSpecificProvider diffProvider) {
      super(project, content1, content2, settings, rediff, disposable, ignoredRangeProvider, diffProvider,
            IGNORE_POLICIES, ArrayUtil.remove(HIGHLIGHT_POLICIES, DO_NOT_HIGHLIGHT));
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
