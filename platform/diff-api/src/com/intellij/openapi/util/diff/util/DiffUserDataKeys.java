package com.intellij.openapi.util.diff.util;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Key;

import java.util.List;

public interface DiffUserDataKeys {
  //
  // DiffRequest
  //

  enum ScrollToPolicy {FIRST_CHANGE, LAST_CHANGE}
  Key<ScrollToPolicy> SCROLL_TO_CHANGE = Key.create("Diff.ScrollToChange");
  Key<LogicalPosition[]> EDITORS_CARET_POSITION = Key.create("Diff.EditorsCaretPosition");
  Key<String> HELP_ID = Key.create("Diff.HelpId");

  //
  // DiffContext
  //
  // User data from DiffRequestChain is passed to DiffContext
  //

  Key<Side> PREFERRED_FOCUS_SIDE = Key.create("Diff.PreferredFocusSide");
  Key<ThreeSide> PREFERRED_FOCUS_THREESIDE = Key.create("Diff.PreferredFocusThreeSide");

  Key<Object> DO_NOT_IGNORE_WHITESPACES = Key.create("Diff.DoNotIgnoreWhitespaces");
  Key<String> DIALOG_GROUP_KEY = Key.create("Diff.DialogGroupKey");

  //
  // DiffContext / DiffRequest
  //
  // Both data from DiffContext / DiffRequest will be used. Data from DiffRequest will be used first.
  //

  Key<List<AnAction>> CONTEXT_ACTIONS = Key.create("Diff.ContextActions");
  Key<DataProvider> DATA_PROVIDER = Key.create("Diff.DataProvider");
}
