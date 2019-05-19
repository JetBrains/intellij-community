// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class JavaConcatenationToInjectorAdapter extends ConcatenationInjectorManager.BaseConcatenation2InjectorAdapter implements MultiHostInjector {
  public JavaConcatenationToInjectorAdapter(@NotNull Project project) {
    super(project);
  }

  @Override
  public Pair<PsiElement, PsiElement[]> computeAnchorAndOperands(@NotNull PsiElement context) {
    PsiElement element = context;
    PsiElement parent = context.getParent();
    while (parent instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)parent).getOperationTokenType() == JavaTokenType.PLUS
           || parent instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)parent).getOperationTokenType() == JavaTokenType.PLUSEQ
           || parent instanceof PsiConditionalExpression && ((PsiConditionalExpression)parent).getCondition() != element
           || parent instanceof PsiTypeCastExpression
           || parent instanceof PsiParenthesizedExpression) {
      element = parent;
      parent = parent.getParent();
    }

    PsiElement[] operands;
    PsiElement anchor;
    if (element instanceof PsiPolyadicExpression) {
      operands = ((PsiPolyadicExpression)element).getOperands();
      anchor = element;
    }
    else if (element instanceof PsiAssignmentExpression) {
      PsiExpression rExpression = ((PsiAssignmentExpression)element).getRExpression();
      operands = new PsiElement[]{rExpression == null ? element : rExpression};
      anchor = element;
    }
    else {
      operands = new PsiElement[]{context};
      anchor = context;
    }

    return Pair.create(anchor, operands);
  }

  @Override
  @NotNull
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return LITERALS;
  }
  private static final List<Class<PsiLiteralExpression>> LITERALS = Collections.singletonList(PsiLiteralExpression.class);
}
