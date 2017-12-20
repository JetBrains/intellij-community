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
import com.intellij.diff.comparison.ComparisonUtil;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.fragments.LineFragmentImpl;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.util.DiffUserDataKeysEx.DiffComputer;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Range;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.diff.tools.util.base.HighlightPolicy.*;
import static com.intellij.diff.tools.util.base.IgnorePolicy.*;
import static java.util.Collections.singletonList;

public class SimpleTextDiffProvider extends TwosideTextDiffProviderBase implements TwosideTextDiffProvider {
  static final DiffComputer DEFAULT_COMPUTER = (text1, text2, policy, innerChanges, indicator) -> {
    if (innerChanges) {
      return ComparisonManager.getInstance().compareLinesInner(text1, text2, policy, indicator);
    }
    else {
      return ComparisonManager.getInstance().compareLines(text1, text2, policy, indicator);
    }
  };

  private static final IgnorePolicy[] IGNORE_POLICIES = {DEFAULT, TRIM_WHITESPACES, IGNORE_WHITESPACES, IGNORE_WHITESPACES_CHUNKS};
  private static final HighlightPolicy[] HIGHLIGHT_POLICIES = {BY_LINE, BY_WORD, BY_WORD_SPLIT, DO_NOT_HIGHLIGHT};

  @NotNull private final DiffComputer myDiffComputer;

  public SimpleTextDiffProvider(@NotNull TextDiffSettings settings,
                                @NotNull Runnable rediff,
                                @NotNull Disposable disposable) {
    this(settings, rediff, disposable, DEFAULT_COMPUTER);
  }

  public SimpleTextDiffProvider(@NotNull TextDiffSettings settings,
                                @NotNull Runnable rediff,
                                @NotNull Disposable disposable,
                                @NotNull DiffComputer diffComputer) {
    this(settings, rediff, disposable, diffComputer, IGNORE_POLICIES, HIGHLIGHT_POLICIES);
  }

  private SimpleTextDiffProvider(@NotNull TextDiffSettings settings,
                                 @NotNull Runnable rediff,
                                 @NotNull Disposable disposable,
                                 @NotNull DiffComputer diffComputer,
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
                                               @NotNull List<Range> linesRanges,
                                               @NotNull IgnorePolicy ignorePolicy,
                                               boolean innerFragments,
                                               @NotNull ProgressIndicator indicator) {
    return compareRange(myDiffComputer, text1, text2, lineOffsets1, lineOffsets2, linesRanges, ignorePolicy, innerFragments, indicator);
  }

  @NotNull
  public static List<List<LineFragment>> compareRange(@NotNull DiffComputer diffComputer,
                                                      @NotNull CharSequence text1,
                                                      @NotNull CharSequence text2,
                                                      @NotNull LineOffsets lineOffsets1,
                                                      @NotNull LineOffsets lineOffsets2,
                                                      @NotNull List<Range> linesRanges,
                                                      @NotNull IgnorePolicy ignorePolicy,
                                                      boolean innerFragments,
                                                      @NotNull ProgressIndicator indicator) {
    ComparisonPolicy policy = ignorePolicy.getComparisonPolicy();

    List<List<LineFragment>> result = new ArrayList<>();
    for (Range range : linesRanges) {
      CharSequence content1 = DiffUtil.getLinesContent(text1, lineOffsets1, range.start1, range.end1, true);
      CharSequence content2 = DiffUtil.getLinesContent(text2, lineOffsets2, range.start2, range.end2, true);

      if (range.start1 == range.end1 || range.start2 == range.end2) {
        boolean isEquals = ComparisonUtil.isEquals(content1, content2, policy);
        result.add(singletonList(createSimpleFragment(range, lineOffsets1, lineOffsets2, isEquals)));
      }
      else {
        List<LineFragment> fragments = diffComputer.compute(content1, content2, policy, innerFragments, indicator);

        int startOffset1 = lineOffsets1.getLineStart(range.start1);
        int startOffset2 = lineOffsets2.getLineStart(range.start2);
        result.add(ContainerUtil.map(fragments, fragment -> {
          return transferFragment(fragment, range.start1, range.start2, startOffset1, startOffset2);
        }));
      }
    }

    return result;
  }

  @NotNull
  public static LineFragment createSimpleFragment(@NotNull Range linesRange,
                                                  @NotNull LineOffsets lineOffsets1,
                                                  @NotNull LineOffsets lineOffsets2,
                                                  boolean isEquals) {
    TextRange textRange1 = DiffUtil.getLinesRange(lineOffsets1, linesRange.start1, linesRange.end1, true);
    TextRange textRange2 = DiffUtil.getLinesRange(lineOffsets2, linesRange.start2, linesRange.end2, true);

    return new LineFragmentImpl(linesRange.start1, linesRange.end1,
                                linesRange.start2, linesRange.end2,
                                textRange1.getStartOffset(), textRange1.getEndOffset(),
                                textRange2.getStartOffset(), textRange2.getEndOffset(),
                                isEquals ? Collections.emptyList() : null);
  }

  @NotNull
  public static LineFragment transferFragment(@NotNull LineFragment fragment,
                                              int startLine1, int startLine2,
                                              int startOffset1, int startOffset2) {
    if (startLine1 == 0 && startLine2 == 0 && startOffset1 == 0 && startOffset2 == 0) return fragment;
    return new LineFragmentImpl(fragment.getStartLine1() + startLine1, fragment.getEndLine1() + startLine1,
                                fragment.getStartLine2() + startLine2, fragment.getEndLine2() + startLine2,
                                fragment.getStartOffset1() + startOffset1, fragment.getEndOffset1() + startOffset1,
                                fragment.getStartOffset2() + startOffset2, fragment.getEndOffset2() + startOffset2,
                                fragment.getInnerFragments());
  }

  public static class NoIgnore extends SimpleTextDiffProvider implements TwosideTextDiffProvider.NoIgnore {
    public NoIgnore(@NotNull TextDiffSettings settings, @NotNull Runnable rediff, @NotNull Disposable disposable) {
      this(settings, rediff, disposable, DEFAULT_COMPUTER);
    }

    public NoIgnore(@NotNull TextDiffSettings settings, @NotNull Runnable rediff, @NotNull Disposable disposable, @NotNull DiffComputer diffComputer) {
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
