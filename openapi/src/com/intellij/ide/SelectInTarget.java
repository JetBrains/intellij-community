/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public interface SelectInTarget {
  String toString();

  /**
   * This should be called in an read action
   */
  boolean canSelect(PsiFile file);

  void select(PsiElement element, boolean requestFocus);

  /** Tool window this target is supposed to select in */
  String getToolWindowId();

  /** aux view id specific for tool window, e.g. Project/Packages/J2EE tab inside project View */
  String getMinorViewId();
}
