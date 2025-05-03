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

import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.util.Range;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface TwosideTextDiffProvider extends TextDiffProvider {
  @Nullable
  List<LineFragment> compare(@NotNull CharSequence text1,
                             @NotNull CharSequence text2,
                             @NotNull ProgressIndicator indicator);

  @Nullable
  List<List<LineFragment>> compare(@NotNull CharSequence text1,
                                   @NotNull CharSequence text2,
                                   @NotNull List<? extends Range> linesRanges,
                                   @NotNull ProgressIndicator indicator);


  /**
   * Some of the diff window actions rely on the default diff algorithm, for example, commiting only the part of the file.
   * Such actions might not be suitable in cases when a custom diff algorithm is used, e.g. during semantic merge.
   *
   * @see com.intellij.openapi.vcs.changes.actions.diff.lst.SimpleLocalChangeListDiffViewer
   * @return true if such actions should be disabled for the current context, false otherwise.
   */
  default boolean areVCSBoundedActionsDisabled() {
    return false;
  }

  default boolean isHighlightingDisabled() {
    return false;
  }


  interface NoIgnore extends TwosideTextDiffProvider {
    @NotNull
    @Override
    List<LineFragment> compare(@NotNull CharSequence text1,
                               @NotNull CharSequence text2,
                               @NotNull ProgressIndicator indicator);

    @NotNull
    @Override
    List<List<LineFragment>> compare(@NotNull CharSequence text1,
                                     @NotNull CharSequence text2,
                                     @NotNull List<? extends Range> linesRanges,
                                     @NotNull ProgressIndicator indicator);
  }
}
