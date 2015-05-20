/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.comparison;

import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Class for the text comparison
 * CharSequences should to have '\n' as line separator
 * <p/>
 * It's good idea not to compare String due to expensive subSequence() implementation. Try to wrap into CharSequenceSubSequence.
 */
public abstract class ComparisonManager {
  @NotNull
  public static ComparisonManager getInstance() {
    return ServiceManager.getService(ComparisonManager.class);
  }

  @NotNull
  public abstract List<LineFragment> compareLines(@NotNull CharSequence text1,
                                                  @NotNull CharSequence text2,
                                                  @NotNull ComparisonPolicy policy,
                                                  @NotNull ProgressIndicator indicator) throws DiffTooBigException;

  @NotNull
  public abstract List<LineFragment> compareLinesInner(@NotNull CharSequence text1,
                                                       @NotNull CharSequence text2,
                                                       @NotNull ComparisonPolicy policy,
                                                       @NotNull ProgressIndicator indicator) throws DiffTooBigException;

  @NotNull
  public abstract List<LineFragment> compareLinesInner(@NotNull CharSequence text1,
                                                       @NotNull CharSequence text2,
                                                       @NotNull List<LineFragment> lineFragments,
                                                       @NotNull ComparisonPolicy policy,
                                                       @NotNull ProgressIndicator indicator) throws DiffTooBigException;

  @NotNull
  public abstract List<DiffFragment> compareWords(@NotNull CharSequence text1,
                                                  @NotNull CharSequence text2,
                                                  @NotNull ComparisonPolicy policy,
                                                  @NotNull ProgressIndicator indicator) throws DiffTooBigException;

  @NotNull
  public abstract List<DiffFragment> compareChars(@NotNull CharSequence text1,
                                                  @NotNull CharSequence text2,
                                                  @NotNull ComparisonPolicy policy,
                                                  @NotNull ProgressIndicator indicator) throws DiffTooBigException;

  public abstract boolean isEquals(@NotNull CharSequence text1, @NotNull CharSequence text2, @NotNull ComparisonPolicy policy);

  //
  // Post process line fragments
  //

  @NotNull
  public abstract List<LineFragment> squash(@NotNull List<LineFragment> oldFragments);

  @NotNull
  public abstract List<LineFragment> processBlocks(@NotNull List<LineFragment> oldFragments,
                                                   @NotNull final CharSequence text1, @NotNull final CharSequence text2,
                                                   @NotNull final ComparisonPolicy policy,
                                                   final boolean squash, final boolean trim);
}
