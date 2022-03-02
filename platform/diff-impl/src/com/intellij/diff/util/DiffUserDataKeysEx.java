// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util;

import com.intellij.diff.DiffEditorTitleCustomizer;
import com.intellij.diff.DiffTool;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.editor.DiffEditorTabFilesManager;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.merge.MergeTool;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsActions;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.IntUnaryOperator;

public interface DiffUserDataKeysEx extends DiffUserDataKeys {
  //
  // DiffContent
  //

  /**
   * Override line numbers in editor gutter (function "document line -> user-visible line number")
   */
  Key<IntUnaryOperator> LINE_NUMBER_CONVERTOR = Key.create("Diff.LineNumberConvertor");
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

  Key<List<DiffEditorTitleCustomizer>> EDITORS_TITLE_CUSTOMIZER = Key.create("Diff.EditorsTitleCustomizer");
  Key<Boolean> EDITORS_HIDE_TITLE = Key.create("Diff.EditorsHideTitle");

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

  Key<Boolean> DISABLE_CONTENTS_EQUALS_NOTIFICATION = Key.create("Diff.DisableContentsEqualsNotification");

  //
  // DiffContext
  //

  /**
   * Add panel to the bottom of diff window.
   * If passed panel implements Disposable, it will be disposed when window is closed.
   */
  Key<JComponent> BOTTOM_PANEL = Key.create("Diff.BottomPanel");
  /**
   * Force viewer to a single DiffTool and prohibit switching to another one.
   */
  Key<DiffTool> FORCE_DIFF_TOOL = Key.create("Diff.ForceDiffTool");
  /**
   * Show "Disable Editing" action on toolbar, that allows to prevent accidental file modifications.
   */
  Key<Boolean> SHOW_READ_ONLY_LOCK = Key.create("Diff.ShowReadOnlyLock");
  /**
   * Whether "Local Changes" are shown in this view (and {@link com.intellij.openapi.vcs.ex.LineStatusTrackerI} can be used to show diff).
   */
  Key<Boolean> LAST_REVISION_WITH_LOCAL = Key.create("Diff.LastWithLocal");

  Key<Float> TWO_SIDE_SPLITTER_PROPORTION = Key.create("Diff.TwoSideSplitterProportion");

  /**
   * Marker flag for viewers embedded into FileEditor tab.
   * Ex: such viewers might encounter conflicting shortcuts.
   *
   * @deprecated use {@link DiffEditorTabFilesManager#isEditorDiffAvailable} instead
   */
  @Deprecated(forRemoval = true)
  Key<Boolean> DIFF_IN_EDITOR = Key.create("Diff.DiffInEditor");

  /**
   * Marker flag for viewers embedded into FileEditor tab, that should not be disposed on tab close.
   */
  Key<Boolean> DIFF_IN_EDITOR_WITH_EXPLICIT_DISPOSABLE = Key.create("Diff.DiffInEditor.ExplicitDisposable");

  Key<Boolean> DIFF_NEW_TOOLBAR = Key.create("Diff.NewToolbar");

  //
  // MergeContext / MergeRequest
  //

  /**
   * @return false if merge window should be prevented from closing and canceling resolve.
   */
  Key<Condition<MergeTool.MergeViewer>> MERGE_CANCEL_HANDLER = Key.create("Diff.MergeCancelHandler");
  /**
   * Pair(title, message) for message dialog
   */
  Key<Couple<@Nls String>> MERGE_CANCEL_MESSAGE = Key.create("Diff.MergeCancelMessage");
  /**
   * Return {@code null} to use defaults.
   */
  Key<Function<MergeResult, @Nls String>> MERGE_ACTION_CAPTIONS = Key.create("Diff.MergeActionCaptions");


  Key<@Nls String> VCS_DIFF_LEFT_CONTENT_TITLE = Key.create("Diff.Left.Panel.Title");
  Key<@Nls String> VCS_DIFF_RIGHT_CONTENT_TITLE = Key.create("Diff.Right.Panel.Title");
  Key<@Nls String> VCS_DIFF_CENTER_CONTENT_TITLE = Key.create("Diff.Center.Panel.Title");

  Key<@NlsActions.ActionText String> VCS_DIFF_ACCEPT_LEFT_ACTION_TEXT = Key.create("Diff.Accept.Left.Action.Text");
  Key<@NlsActions.ActionText String> VCS_DIFF_ACCEPT_RIGHT_ACTION_TEXT = Key.create("Diff.Accept.Left.Action.Text");
  Key<@NlsActions.ActionText String> VCS_DIFF_ACCEPT_LEFT_TO_BASE_ACTION_TEXT = Key.create("Diff.Accept.Left.Base.Action.Text");
  Key<@NlsActions.ActionText String> VCS_DIFF_ACCEPT_BASE_TO_LEFT_ACTION_TEXT = Key.create("Diff.Accept.Base.Left.Action.Text");
  Key<@NlsActions.ActionText String> VCS_DIFF_ACCEPT_RIGHT_TO_BASE_ACTION_TEXT = Key.create("Diff.Accept.Right.Base.Action.Text");
  Key<@NlsActions.ActionText String> VCS_DIFF_ACCEPT_BASE_TO_RIGHT_ACTION_TEXT = Key.create("Diff.Accept.Base.Right.Action.Text");
}
