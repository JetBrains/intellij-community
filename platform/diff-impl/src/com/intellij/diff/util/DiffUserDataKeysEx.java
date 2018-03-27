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
package com.intellij.diff.util;

import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.merge.MergeTool;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public interface DiffUserDataKeysEx extends DiffUserDataKeys {
  //
  // DiffContent
  //

  Key<TIntFunction> LINE_NUMBER_CONVERTOR = Key.create("Diff.LineNumberConvertor"); // Document line -> line number in Editor gutter
  Key<Pair<FilePath, VcsRevisionNumber>> REVISION_INFO = Key.create("Diff.RevisionInfo");
  Key<String> FILE_NAME = Key.create("Diff.FileName");

  //
  // DiffRequest
  //
  enum ScrollToPolicy {
    FIRST_CHANGE, LAST_CHANGE;

    @Nullable
    public <T> T select(@NotNull List<T> changes) {
      if (this == FIRST_CHANGE) return ContainerUtil.getFirstItem(changes);
      if (this == LAST_CHANGE) return ContainerUtil.getLastItem(changes);
      throw new IllegalStateException();
    }
  }

  Key<ScrollToPolicy> SCROLL_TO_CHANGE = Key.create("Diff.ScrollToChange");
  Key<LogicalPosition[]> EDITORS_CARET_POSITION = Key.create("Diff.EditorsCaretPosition");

  Key<DiffNavigationContext> NAVIGATION_CONTEXT = Key.create("Diff.NavigationContext");

  interface DiffComputer {
    @NotNull
    List<LineFragment> compute(@NotNull CharSequence text1,
                               @NotNull CharSequence text2,
                               @NotNull ComparisonPolicy policy,
                               boolean innerChanges,
                               @NotNull ProgressIndicator indicator);
  }
  Key<DiffComputer> CUSTOM_DIFF_COMPUTER = Key.create("Diff.CustomDiffComputer");

  //
  // DiffContext
  //

  Key<JComponent> BOTTOM_PANEL = Key.create("Diff.BottomPanel"); // Could implement Disposable

  Key<Boolean> SHOW_READ_ONLY_LOCK = Key.create("Diff.ShowReadOnlyLock");

  //
  // MergeContext / MergeRequest
  //

  // return false if merge window should be prevented from closing and canceling resolve.
  Key<Condition<MergeTool.MergeViewer>> MERGE_CANCEL_HANDLER = Key.create("Diff.MergeCancelHandler");
  // (title, message)
  Key<Couple<String>> MERGE_CANCEL_MESSAGE = Key.create("Diff.MergeCancelMessage");
  // null -> default
  Key<Function<MergeResult, String>> MERGE_ACTION_CAPTIONS = Key.create("Diff.MergeActionCaptions");


  Key<String> VCS_DIFF_LEFT_CONTENT_TITLE = Key.create("Diff.Left.Panel.Title");
  Key<String> VCS_DIFF_RIGHT_CONTENT_TITLE = Key.create("Diff.Right.Panel.Title");
}
