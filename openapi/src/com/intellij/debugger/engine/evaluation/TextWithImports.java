package com.intellij.debugger.engine.evaluation;

import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface TextWithImports {
  String getText();
  void setText(String newText);
  @NotNull String getImports();
  CodeFragmentKind getKind();
  boolean isEmpty();
}
