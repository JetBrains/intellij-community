// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.util.text;

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.MergeConflictType;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.diff.tools.util.base.HighlightPolicy.BY_LINE;
import static com.intellij.diff.tools.util.base.HighlightPolicy.BY_WORD;
import static com.intellij.diff.tools.util.base.IgnorePolicy.*;

public class SimpleThreesideTextDiffProvider extends TextDiffProviderBase {
  private static final IgnorePolicy[] IGNORE_POLICIES = {DEFAULT, TRIM_WHITESPACES, IGNORE_WHITESPACES};
  private static final HighlightPolicy[] HIGHLIGHT_POLICIES = {BY_LINE, BY_WORD};

  public SimpleThreesideTextDiffProvider(@NotNull TextDiffSettings settings,
                                         @NotNull Runnable rediff,
                                         @NotNull Disposable disposable) {
    super(settings, rediff, disposable, IGNORE_POLICIES, HIGHLIGHT_POLICIES);
  }

  @NotNull
  public List<FineMergeLineFragment> compare(@NotNull CharSequence text1,
                                             @NotNull CharSequence text2,
                                             @NotNull CharSequence text3,
                                             @NotNull ProgressIndicator indicator) {

    IgnorePolicy ignorePolicy = getIgnorePolicy();
    HighlightPolicy highlightPolicy = getHighlightPolicy();
    ComparisonPolicy comparisonPolicy = ignorePolicy.getComparisonPolicy();

    List<CharSequence> sequences = Arrays.asList(text1, text2, text3);
    List<LineOffsets> lineOffsets = ContainerUtil.map(sequences, LineOffsetsUtil::create);

    indicator.checkCanceled();
    List<MergeLineFragment> lineFragments = ComparisonManager.getInstance().compareLines(text1, text2, text3, comparisonPolicy, indicator);

    indicator.checkCanceled();
    List<FineMergeLineFragment> result = new ArrayList<>(lineFragments.size());
    for (MergeLineFragment fragment : lineFragments) {
      MergeConflictType conflictType = DiffUtil.getLineThreeWayDiffType(fragment, sequences, lineOffsets, comparisonPolicy);

      MergeInnerDifferences innerDifferences;
      if (highlightPolicy.isFineFragments()) {
        List<CharSequence> chunks = getChunks(fragment, sequences, lineOffsets, conflictType);
        innerDifferences = DiffUtil.compareThreesideInner(chunks, comparisonPolicy, indicator);
      }
      else {
        innerDifferences = null;
      }

      result.add(new FineMergeLineFragmentImpl(fragment, conflictType, innerDifferences));
    }

    return result;
  }

  @NotNull
  private static List<CharSequence> getChunks(@NotNull MergeLineFragment fragment,
                                              @NotNull List<CharSequence> sequences,
                                              @NotNull List<LineOffsets> lineOffsets,
                                              @NotNull MergeConflictType conflictType) {
    return ThreeSide.map(side -> {
      if (!conflictType.isChange(side)) return null;

      int startLine = fragment.getStartLine(side);
      int endLine = fragment.getEndLine(side);
      if (startLine == endLine) return null;

      return DiffUtil.getLinesContent(side.select(sequences), side.select(lineOffsets), startLine, endLine);
    });
  }
}
