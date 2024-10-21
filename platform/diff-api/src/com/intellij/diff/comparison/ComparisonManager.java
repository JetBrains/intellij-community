// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.comparison;

import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.util.MergeRange;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Class for the text comparison
 * CharSequences should to have '\n' as line separator
 * <p/>
 * It's good idea not to compare String due to expensive subSequence() implementation. Use CharSequenceSubSequence.
 */
public abstract class ComparisonManager {
  @NotNull
  public static ComparisonManager getInstance() {
    return ApplicationManager.getApplication().getService(ComparisonManager.class);
  }

  public abstract @NotNull CancellationChecker createCancellationChecker(@NotNull ProgressIndicator indicator);

  /**
   * Compare two texts by-line
   */
  @NotNull
  public abstract List<LineFragment> compareLines(@NotNull CharSequence text1,
                                                  @NotNull CharSequence text2,
                                                  @NotNull ComparisonPolicy policy,
                                                  @NotNull ProgressIndicator indicator) throws DiffTooBigException;

  /**
   * Compare two texts by-line and then compare changed fragments by-word
   */
  @NotNull
  public abstract List<LineFragment> compareLinesInner(@NotNull CharSequence text1,
                                                       @NotNull CharSequence text2,
                                                       @NotNull ComparisonPolicy policy,
                                                       @NotNull ProgressIndicator indicator) throws DiffTooBigException;

  /**
   * Compare three texts by-line (LEFT - BASE - RIGHT)
   */
  @NotNull
  public abstract List<MergeLineFragment> compareLines(@NotNull CharSequence text1,
                                                       @NotNull CharSequence text2,
                                                       @NotNull CharSequence text3,
                                                       @NotNull ComparisonPolicy policy,
                                                       @NotNull ProgressIndicator indicator) throws DiffTooBigException;

  /**
   * Compare three texts by-line (LEFT - BASE - RIGHT)
   * Do not actually skip "ignored" changes, but keep them from forming merge conflicts.
   */
  public abstract List<MergeLineFragment> mergeLines(@NotNull CharSequence text1,
                                                     @NotNull CharSequence text2,
                                                     @NotNull CharSequence text3,
                                                     @NotNull ComparisonPolicy policy,
                                                     @NotNull ProgressIndicator indicator) throws DiffTooBigException;

  /**
   * Compare three texts by-line (LEFT - BASE - RIGHT)
   * Do not actually skip "ignored" changes, but keep them from forming merge conflicts.
   */
  public abstract List<MergeLineFragment> mergeLinesWithinRange(@NotNull CharSequence text1,
                                                                @NotNull CharSequence text2,
                                                                @NotNull CharSequence text3,
                                                                @NotNull ComparisonPolicy policy,
                                                                @NotNull MergeRange boundaryRange,
                                                                @NotNull ProgressIndicator indicator) throws DiffTooBigException;

  /**
   * Return the common parts of the two files, that can be used as an ad-hoc merge base content.
   */
  public abstract String mergeLinesAdditions(@NotNull CharSequence text1,
                                             @NotNull CharSequence text3,
                                             @NotNull ComparisonPolicy policy,
                                             @NotNull ProgressIndicator indicator) throws DiffTooBigException;


  /**
   * Compare two texts by-word
   */
  @NotNull
  public abstract List<DiffFragment> compareWords(@NotNull CharSequence text1,
                                                  @NotNull CharSequence text2,
                                                  @NotNull ComparisonPolicy policy,
                                                  @NotNull ProgressIndicator indicator) throws DiffTooBigException;

  /**
   * Compare two texts by-char
   */
  @NotNull
  public abstract List<DiffFragment> compareChars(@NotNull CharSequence text1,
                                                  @NotNull CharSequence text2,
                                                  @NotNull ComparisonPolicy policy,
                                                  @NotNull ProgressIndicator indicator) throws DiffTooBigException;

  /**
   * Check if two texts are equal using ComparisonPolicy
   */
  public abstract boolean isEquals(@NotNull CharSequence text1, @NotNull CharSequence text2, @NotNull ComparisonPolicy policy);

  //
  // Post process line fragments
  //

  /**
   * compareLinesInner() comparison can produce adjustment line chunks. This method allows to squash shem.
   *
   * ex: "A\nB" vs "A X\nB Y" will result to two LineFragments: [0, 1) - [0, 1) and [1, 2) - [1, 2)
   *     squash will produce a single fragment: [0, 2) - [0, 2)
   */
  @NotNull
  public abstract List<LineFragment> squash(@NotNull List<LineFragment> oldFragments);

  /**
   * @see #squash
   * @param trim - if leading/trailing LineFragments with equal contents should be skipped
   */
  @NotNull
  public abstract List<LineFragment> processBlocks(@NotNull List<LineFragment> oldFragments,
                                                   @NotNull final CharSequence text1, @NotNull final CharSequence text2,
                                                   @NotNull final ComparisonPolicy policy,
                                                   final boolean squash, final boolean trim);
}
