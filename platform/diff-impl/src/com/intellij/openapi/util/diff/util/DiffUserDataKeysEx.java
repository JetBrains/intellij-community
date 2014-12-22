package com.intellij.openapi.util.diff.util;

import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.diff.tools.util.LineFragmentCache;

import javax.swing.*;

public interface DiffUserDataKeysEx extends DiffUserDataKeys {
  //
  // DiffRequest
  //

  Key<DiffNavigationContext> NAVIGATION_CONTEXT = Key.create("Diff.NavigationContext");
  Key<LineFragmentCache> LINE_FRAGMENT_CACHE = Key.create("Diff.LineFragmentCache");

  //
  // DiffContext
  //

  Key<JComponent> BOTTOM_PANEL = Key.create("Diff.BottomPanel"); // Could implement Disposable
}
