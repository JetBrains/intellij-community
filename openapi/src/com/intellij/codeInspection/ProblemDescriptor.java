/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

/**
 * See {@link InspectionManager#createProblemDescriptor(PsiElement, String, LocalQuickFix, ProblemHighlightType) } for method descriptions.
 */
public interface ProblemDescriptor {
  PsiElement getPsiElement();
  String getDescriptionTemplate();
  int getLineNumber();
  LocalQuickFix getFix();
  ProblemHighlightType getHighlightType();
}
