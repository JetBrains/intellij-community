package com.intellij.openapi.util.diff.tools.util;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.diff.util.Side;
import com.intellij.openapi.util.diff.util.ThreeSide;

import javax.swing.*;
import java.util.List;

public interface DiffUserDataKeys {
  //
  // DiffRequest
  //

  enum ScrollToPolicy {FIRST_CHANGE, LAST_CHANGE}

  Key<ScrollToPolicy> SCROLL_TO_CHANGE = Key.create("Diff.ScrollToChange");
  Key<LogicalPosition[]> EDITORS_CARET_POSITION = Key.create("Diff.EditorsCaretPosition");
  Key<DiffNavigationContext> NAVIGATION_CONTEXT = Key.create("Diff.NavigationContext");

  Key<String> HELP_ID = Key.create("Diff.HelpId");
  Key<LineFragmentCache> LINE_FRAGMENT_CACHE = Key.create("Diff.LineFragmentCache");

  //
  // DiffContext
  //

  Key<Side> PREFERRED_FOCUS_SIDE = Key.create("Diff.PreferredFocusSide");
  Key<ThreeSide> PREFERRED_FOCUS_THREESIDE = Key.create("Diff.PreferredFocusThreeSide");

  //
  // DiffChain
  //

  Key<JComponent> BOTTOM_PANEL = Key.create("Diff.BottomPanel"); // Could implement Disposable

  //
  // DiffContext / DiffRequest
  //

  Key<List<AnAction>> CONTEXT_ACTIONS = Key.create("Diff.ContextActions");
  Key<DataProvider> DATA_PROVIDER = Key.create("Diff.DataProvider");
}
