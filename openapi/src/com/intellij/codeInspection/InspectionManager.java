/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

/**
 * @author max
 */
public abstract class InspectionManager {
  public static InspectionManager getInstance(Project project) {
    return project.getComponent(InspectionManager.class);
  }

  /**
   * Factory method for ProblemDescriptor. Should be called from LocalInspectionTool.checkXXX() methods.
   * @param psiElement problem is reported against
   * @param descriptionTemplate problem message. Use <code>#ref</code> for a link to problem piece of code and <code>#loc</code> for location in source code.
   * @param fix should be null if no fix is provided.
   * @return
   */
  public abstract ProblemDescriptor createProblemDescriptor(PsiElement psiElement, String descriptionTemplate, LocalQuickFix fix, ProblemHighlightType highlightType);

  public abstract Project getProject();
}
