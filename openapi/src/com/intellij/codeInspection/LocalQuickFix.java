/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;

/**
 * @author max
 */
public interface LocalQuickFix {
  String getName();

  /**
   * Called to apply the fix.
   * @param project {@link com.intellij.openapi.project.Project}
   * @param descriptor problem reported by the tool which provided this quick fix action
   */
  void applyFix(Project project, ProblemDescriptor descriptor);
}
