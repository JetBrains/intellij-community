// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuppressForClassFix extends SuppressFix {
  public SuppressForClassFix(final HighlightDisplayKey key) {
    super(key);
  }

  public SuppressForClassFix(final String id) {
   super(id);
  }

  @Override
  public @Nullable PsiJavaDocumentedElement getContainer(final PsiElement element) {
    PsiJavaDocumentedElement container = super.getContainer(element);
    if (container == null || container instanceof PsiClass){
      return null;
    }
    while (container != null ) {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(container, PsiClass.class);
      if ((parentClass == null || container.getParent() instanceof PsiDeclarationStatement || container.getParent() instanceof PsiClass) &&
          container instanceof PsiClass && !(container instanceof PsiImplicitClass)){
        return container;
      }
      container = parentClass;
    }
    return null;
  }

  @Override
  public @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("suppress.inspection.class");
  }

  @Override
  public int getPriority() {
    return 50;
  }
}
