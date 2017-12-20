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
import com.intellij.diff.comparison.TrimUtil;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.lang.DiffIgnoredRangeProvider;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Range;
import com.intellij.diff.util.Side;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static com.intellij.diff.tools.util.base.HighlightPolicy.*;
import static com.intellij.diff.tools.util.base.IgnorePolicy.*;
import static java.util.Collections.singletonList;

public class SmartTextDiffProvider extends TwosideTextDiffProviderBase implements TwosideTextDiffProvider {
  private static final IgnorePolicy[] IGNORE_POLICIES = {DEFAULT, TRIM_WHITESPACES, IGNORE_WHITESPACES, IGNORE_WHITESPACES_CHUNKS, FORMATTING};
  private static final HighlightPolicy[] HIGHLIGHT_POLICIES = {BY_LINE, BY_WORD, BY_WORD_SPLIT, DO_NOT_HIGHLIGHT};

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
                                @NotNull IgnorePolicy[] ignorePolicies,
                                @NotNull HighlightPolicy[] highlightPolicies) {
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
                                               @NotNull List<Range> linesRanges,
                                               @NotNull IgnorePolicy ignorePolicy,
                                               boolean innerFragments,
                                               @NotNull ProgressIndicator indicator) {
    if (ignorePolicy == FORMATTING) {
      return compareIgnoreFormatting(text1, text2, lineOffsets1, lineOffsets2, linesRanges, innerFragments, indicator);
    }
    else {
      return SimpleTextDiffProvider.compareRange(SimpleTextDiffProvider.DEFAULT_COMPUTER,
                                                 text1, text2, lineOffsets1, lineOffsets2, linesRanges,
                                                 ignorePolicy, innerFragments, indicator);
    }
  }

  @NotNull
  private List<List<LineFragment>> compareIgnoreFormatting(@NotNull CharSequence text1,
                                                           @NotNull CharSequence text2,
                                                           @NotNull LineOffsets lineOffsets1,
                                                           @NotNull LineOffsets lineOffsets2,
                                                           @NotNull List<Range> linesRanges,
                                                           boolean innerFragments,
                                                           @NotNull ProgressIndicator indicator) {
    List<TextRange> ranges1 = myProvider.getIgnoredRanges(myProject, text1, myContent1);
    List<TextRange> ranges2 = myProvider.getIgnoredRanges(myProject, text2, myContent2);

    BitSet ignored1 = ComparisonManagerImpl.collectIgnoredRanges(ranges1);
    BitSet ignored2 = ComparisonManagerImpl.collectIgnoredRanges(ranges2);

    List<List<LineFragment>> result = new ArrayList<>();
    for (Range range : linesRanges) {
      TextRange offsets1 = DiffUtil.getLinesRange(lineOffsets1, range.start1, range.end1, true);
      TextRange offsets2 = DiffUtil.getLinesRange(lineOffsets2, range.start2, range.end2, true);

      if (range.start1 == range.end1 || range.start2 == range.end2) {
        boolean isEquals = TrimUtil.trimExpandText(text1, text2,
                                                   offsets1.getStartOffset(), offsets2.getStartOffset(),
                                                   offsets1.getEndOffset(), offsets2.getEndOffset(),
                                                   ignored1, ignored2).isEmpty();
        result.add(singletonList(SimpleTextDiffProvider.createSimpleFragment(range, lineOffsets1, lineOffsets2, isEquals)));
      }
      else {
        CharSequence subText1 = offsets1.subSequence(text1);
        CharSequence subText2 = offsets2.subSequence(text2);

        List<TextRange> subRanges1 = getSubRanges(ranges1, offsets1);
        List<TextRange> subRanges2 = getSubRanges(ranges2, offsets2);

        ComparisonManagerImpl comparisonManager = ComparisonManagerImpl.getInstanceImpl();
        List<LineFragment> fragments = comparisonManager.compareLinesWithIgnoredRanges(subText1, subText2, subRanges1, subRanges2,
                                                                                       innerFragments, indicator);

        int startOffset1 = offsets1.getStartOffset();
        int startOffset2 = offsets2.getStartOffset();
        result.add(ContainerUtil.map(fragments, fragment -> {
          return SimpleTextDiffProvider.transferFragment(fragment, range.start1, range.start2, startOffset1, startOffset2);
        }));
      }
    }
    return result;
  }

  @NotNull
  private static List<TextRange> getSubRanges(@NotNull List<TextRange> ignoredRanges, @NotNull TextRange offsets) {
    List<TextRange> result = new ArrayList<>();
    for (TextRange range : ignoredRanges) {
      TextRange intersection = range.intersection(offsets);
      if (intersection != null) result.add(intersection);
    }
    return result;
  }

  @Nullable
  private static DiffIgnoredRangeProvider getIgnoredRangeProvider(@Nullable Project project,
                                                                  @NotNull DiffContent content1,
                                                                  @NotNull DiffContent content2) {
    if (!Registry.is("diff.smart.ignore.enabled")) return null;
    for (DiffIgnoredRangeProvider provider : DiffIgnoredRangeProvider.EP_NAME.getExtensions()) {
      if (provider.accepts(project, content1) &&
          provider.accepts(project, content2)) {
        return provider;
      }
    }
    return null;
  }

  public static class NoIgnore extends SmartTextDiffProvider implements TwosideTextDiffProvider.NoIgnore {
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
                                            @NotNull List<Range> linesRanges,
                                            @NotNull ProgressIndicator indicator) {
      //noinspection ConstantConditions
      return super.compare(text1, text2, linesRanges, indicator);
    }
  }
}
