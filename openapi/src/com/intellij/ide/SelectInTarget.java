/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide;

public interface SelectInTarget {
  String toString();

  /**
   * This should be called in an read action
   */
  boolean canSelect(SelectInContext context);

  void selectIn(SelectInContext context, final boolean requestFocus);

  /** Tool window this target is supposed to select in */
  String getToolWindowId();

  /** aux view id specific for tool window, e.g. Project/Packages/J2EE tab inside project View */
  String getMinorViewId();

}
