package com.intellij.openapi.util.diff.tools.util;

import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.diff.util.Side;

public interface DiffUserDataKeys {
  /*
   * Scroll priority: SCROLL_TO_CHANGE > SCROLL_TO_LINE > NAVIGATION_CONTEXT > DEFAULT(FIRST_CHANGE)
   */

  //
  // DiffRequest
  //

  Key<Pair<Side, Integer>> SCROLL_TO_LINE = Key.create("Diff.ScrollToLine");
  Key<DiffNavigationContext> NAVIGATION_CONTEXT = Key.create("Diff.NavigationContext");

  Key<String> HELP_ID = Key.create("Diff.HelpId");
  Key<LineFragmentCache> LINE_FRAGMENT_CACHE = Key.create("Diff.LineFragmentCache");

  //
  // DiffContext
  //

  Key<Side> PREFERRED_FOCUS_SIDE = Key.create("Diff.PreferredFocusSide");

  Key<ScrollToPolicy> SCROLL_TO_CHANGE = Key.create("Diff.ScrollSoChange");

  enum ScrollToPolicy {FIRST_CHANGE, LAST_CHANGE}
}
