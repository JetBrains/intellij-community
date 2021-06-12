// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation;

import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.ide.util.PsiClassOrFunctionalExpressionListCellRenderer;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;


public class JavaGotoTargetRendererProvider implements GotoTargetRendererProvider {
  @Override
  public PsiElementListCellRenderer getRenderer(@NotNull final PsiElement element, @NotNull GotoTargetHandler.GotoData gotoData) {
    if (element instanceof PsiMethod) {
      return new MethodCellRenderer(gotoData.hasDifferentNames());
    }
    else if (element instanceof PsiClass) {
      return new PsiClassListCellRenderer();
    }
    else if (element instanceof PsiFunctionalExpression) {
      return new PsiClassOrFunctionalExpressionListCellRenderer();
    }
    return null;
  }

}
