// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;

import java.util.List;

public interface DiffUserDataKeys {
  //
  // DiffContent
  //

  Key<Language> LANGUAGE = Key.create("Diff.Language");

  //
  // DiffRequest
  //

  /**
   * Override default caret position or text viewers.
   */
  Key<Pair<Side, Integer>> SCROLL_TO_LINE = Key.create("Diff.ScrollToLine");
  Key<Pair<ThreeSide, Integer>> SCROLL_TO_LINE_THREESIDE = Key.create("Diff.ScrollToLineThreeside");

  Key<String> HELP_ID = Key.create("Diff.HelpId");

  /**
   * Used IN ADDITION to {@link #FORCE_READ_ONLY} data key. Pass {@code true} to prohibit corresponding content editing in diff viewer.
   *
   * @see com.intellij.diff.requests.ContentDiffRequest
   */
  Key<boolean[]> FORCE_READ_ONLY_CONTENTS = Key.create("Diff.ForceReadOnlyContents");

  //
  // DiffContext
  //

  /**
   * Use {@link com.intellij.diff.tools.util.base.IgnorePolicy#DEFAULT} option by default.
   */
  Key<Boolean> DO_NOT_IGNORE_WHITESPACES = Key.create("Diff.DoNotIgnoreWhitespaces");

  /**
   * Key to store/load previous window position.
   */
  Key<String> DIALOG_GROUP_KEY = Key.create("Diff.DialogGroupKey");

  /**
   * Key is used to store 'local' diff settings (ex: highlighting options) independently.
   * Ex: to allow having different defaults in diff previews in "Commit Dialog" and "VCS Log".
   *
   * @see com.intellij.diff.util.DiffPlaces
   */
  Key<String> PLACE = Key.create("Diff.Place");

  Key<Boolean> DO_NOT_CHANGE_WINDOW_TITLE = Key.create("Diff.DoNotChangeWindowTitle");

  //
  // DiffContext / DiffRequest
  //
  // Both data from DiffContext / DiffRequest will be used. Data from DiffRequest will be used first.
  //

  enum ThreeSideDiffColors {
    /**
     * Default value, for merge conflict: "Left <- Base -> Right"
     * "A - B - C" is Conflict
     * "AB - B - AB" is Addition
     * "B - B - AB" is Addition
     * "AB - B - B" is Addition
     * "B - AB - AB" is Deletion
     */
    MERGE_CONFLICT,
    /**
     * For result of a past merge: "Left -> Merged <- Right". Same as MERGE_CONFLICT, with inverted "Insertions" and "Deletions".
     * "A - B - C" is Conflict
     * "AB - B - AB" is Deletion
     * "B - B - AB" is Deletion
     * "AB - B - B" is Deletion
     * "B - AB - AB" is Addition
     */
    MERGE_RESULT,
    /**
     * For intermediate state: "Head -> Staged -> Local"
     * "A - B - C" is Modification
     * "AB - B - AB" is Modification
     * "B - B - AB" is Addition
     * "AB - B - B" is Deletion
     * "B - AB - AB" is Addition
     */
    LEFT_TO_RIGHT
  }

  Key<ThreeSideDiffColors> THREESIDE_DIFF_COLORS_MODE = Key.create("Diff.ThreesideDiffWithResult");

  Key<Side> MASTER_SIDE = Key.create("Diff.MasterSide");
  Key<Side> PREFERRED_FOCUS_SIDE = Key.create("Diff.PreferredFocusSide");
  Key<ThreeSide> PREFERRED_FOCUS_THREESIDE = Key.create("Diff.PreferredFocusThreeSide");

  /**
   * Use {@link DiffUtil#addNotification}
   */
  Key<List<DiffNotificationProvider>> NOTIFICATION_PROVIDERS = Key.create("Diff.NotificationProviders");

  Key<List<AnAction>> CONTEXT_ACTIONS = Key.create("Diff.ContextActions");
  Key<DataProvider> DATA_PROVIDER = Key.create("Diff.DataProvider");
  Key<Boolean> GO_TO_SOURCE_DISABLE = Key.create("Diff.GoToSourceDisable");

  //
  // DiffContext / DiffRequest / DiffContent
  //

  Key<Boolean> FORCE_READ_ONLY = Key.create("Diff.ForceReadOnly");

  //
  // Editor
  //

  /**
   * Marks central <code>Editor</code> in merge view with <code>Boolean.TRUE</code>.
   *
   * @see com.intellij.openapi.editor.EditorKind#DIFF
   */
  Key<Boolean> MERGE_EDITOR_FLAG = Key.create("Diff.mergeEditor");

  /**
   * Force aligning changes in side-by-side viewer.<br/>
   * This can be used in viewers, where aligning is critical (e.g. {@link com.intellij.diff.tools.combined.CombinedDiffViewer}).
   *
   * @see com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings#isEnableAligningChangesMode
   */
  Key<Boolean> ALIGNED_TWO_SIDED_DIFF = Key.create("Diff.AlignTwoSidedDiff");

  /**
   * Controls whether the aligning view mode is available for a specific editor.
   * This can be disabled for viewers, where this feature is not implemented (e.g. {@link com.intellij.jupyter.diff.util.JupyterDiffViewer})
   *
   * @see com.intellij.diff.actions.impl.ToggleDiffAligningModeAction#update
   */
  Key<Boolean> ALIGNING_VIEW_NOT_SUPPORTED = Key.create("Diff.DisableAligningView");

  /**
   * Determines if line markers should always be visible in the editor gutter, regardless of the current folding state.
   * This can be particularly useful in specialized diff viewers (e.g., {@link com.intellij.jupyter.diff.util.JupyterDiffViewer})
   *
   * @see com.intellij.openapi.editor.impl.EditorGutterComponentImpl#isLineMarkerVisible
   */
  Key<Boolean> LINE_MARKER_ALWAYS_VISIBLE = Key.create("Diff.LineMarkerAlwaysVisible");
}
