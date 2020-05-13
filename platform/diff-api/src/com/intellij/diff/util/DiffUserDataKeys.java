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

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;

import javax.swing.*;
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

  /**
   * Invert colors in three side conflict viewer.
   * Default: "AB - B - AB" is colored as "Addition" (Left <- Base -> Right)
   * With key: "AB - B - AB" is colored as "Deletion" (Left -> Merged <- Right)
   */
  Key<Boolean> THREESIDE_DIFF_WITH_RESULT = Key.create("Diff.ThreesideDiffWithResult");

  Key<Side> MASTER_SIDE = Key.create("Diff.MasterSide");
  Key<Side> PREFERRED_FOCUS_SIDE = Key.create("Diff.PreferredFocusSide");
  Key<ThreeSide> PREFERRED_FOCUS_THREESIDE = Key.create("Diff.PreferredFocusThreeSide");

  Key<List<JComponent>> NOTIFICATIONS = Key.create("Diff.Notifications");
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
}
