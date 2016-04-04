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

  Key<Pair<Side, Integer>> SCROLL_TO_LINE = Key.create("Diff.ScrollToLine");
  Key<Pair<ThreeSide, Integer>> SCROLL_TO_LINE_THREESIDE = Key.create("Diff.ScrollToLineThreeside");

  Key<String> HELP_ID = Key.create("Diff.HelpId");
  Key<boolean[]> FORCE_READ_ONLY_CONTENTS = Key.create("Diff.ForceReadOnlyContents");

  //
  // DiffContext
  //

  Key<Boolean> DO_NOT_IGNORE_WHITESPACES = Key.create("Diff.DoNotIgnoreWhitespaces");
  Key<String> DIALOG_GROUP_KEY = Key.create("Diff.DialogGroupKey");
  Key<String> PLACE = Key.create("Diff.Place");

  Key<Boolean> DO_NOT_CHANGE_WINDOW_TITLE = Key.create("Diff.DoNotChangeWindowTitle");

  //
  // DiffContext / DiffRequest
  //
  // Both data from DiffContext / DiffRequest will be used. Data from DiffRequest will be used first.
  //

  Key<Side> MASTER_SIDE = Key.create("Diff.MasterSide");
  Key<Side> PREFERRED_FOCUS_SIDE = Key.create("Diff.PreferredFocusSide");
  Key<ThreeSide> PREFERRED_FOCUS_THREESIDE = Key.create("Diff.PreferredFocusThreeSide");

  Key<List<JComponent>> NOTIFICATIONS = Key.create("Diff.Notifications");
  Key<List<AnAction>> CONTEXT_ACTIONS = Key.create("Diff.ContextActions");
  Key<DataProvider> DATA_PROVIDER = Key.create("Diff.DataProvider");
  Key<Boolean> GO_TO_SOURCE_DISABLE = Key.create("Diff.GoToSourceDisable");
  Key<Boolean> FORCE_READ_ONLY = Key.create("Diff.ForceReadOnly");
}
