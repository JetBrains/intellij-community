package com.intellij.debugger.engine.evaluation;

import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.project.Project;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface TextWithImports {
  public String getText();
  public PsiCodeFragment createCodeFragment(PsiElement context, Project project);
  public boolean isEmpty();

  void setText(String newText);
  boolean isExpressionText();
}
