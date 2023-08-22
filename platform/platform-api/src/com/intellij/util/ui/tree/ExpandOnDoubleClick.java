// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.tree;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTree;

/**
 * This class allows to change the default behaviour of expanding/collapsing tree nodes by double-clicking.
 */
@ApiStatus.Experimental
public enum ExpandOnDoubleClick {
  NEVER, ALWAYS, NAVIGATABLE, DEFAULT;

  private static final String KEY = "ide.tree.expand.on.double.click";

  /**
   * Changes the default behaviour for the given tree.
   */
  public void installOn(@NotNull JTree tree) {
    tree.putClientProperty(KEY, this);
  }

  /**
   * @return the preferable behaviour for the given tree.
   * @see EditSourceOnDoubleClickHandler#isExpandPreferable
   */
  public static @NotNull ExpandOnDoubleClick getBehavior(@NotNull JTree tree) {
    Object property = tree.getClientProperty(KEY);
    if (property instanceof ExpandOnDoubleClick) return (ExpandOnDoubleClick)property;
    String option = Registry.get(KEY).getSelectedOption();
    if (NEVER.name().equalsIgnoreCase(option)) return NEVER;
    if (ALWAYS.name().equalsIgnoreCase(option)) return ALWAYS;
    if (NAVIGATABLE.name().equalsIgnoreCase(option)) return NAVIGATABLE;
    return DEFAULT;
  }
}
