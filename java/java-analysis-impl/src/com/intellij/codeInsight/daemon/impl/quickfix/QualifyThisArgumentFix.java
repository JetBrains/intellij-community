// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class QualifyThisArgumentFix extends QualifyThisOrSuperArgumentFix{
  public QualifyThisArgumentFix(@NotNull PsiExpression expression, @NotNull PsiClass psiClass) {
    super(expression, psiClass);
  }

  @Override
  protected String getQualifierText() {
    return JavaKeywords.THIS;
  }

  @Override
  protected PsiExpression getQualifier(PsiManager manager) {
    return RefactoringChangeUtil.createThisExpression(manager, myPsiClass);
  }

  public static void registerQuickFixAction(CandidateInfo[] candidates, PsiCall call, @NotNull Consumer<? super CommonIntentionAction> info) {
    if (candidates.length == 0) return;

    final Set<PsiClass> containingClasses = new HashSet<>();
    PsiClass parentClass = PsiTreeUtil.getParentOfType(call, PsiClass.class);
    while (parentClass != null) {
      if (parentClass.hasModifierProperty(PsiModifier.STATIC)) break;
      if (!(parentClass instanceof PsiAnonymousClass)) {
        containingClasses.add(parentClass);
      }
      parentClass = PsiTreeUtil.getParentOfType(parentClass, PsiClass.class, true);
    }
    if (containingClasses.isEmpty()) return;

    final PsiExpressionList list = call.getArgumentList();
    final PsiExpression[] expressions = list.getExpressions();
    if (expressions.length == 0) return;

    for (int i1 = 0, expressionsLength = expressions.length; i1 < expressionsLength; i1++) {
      final PsiExpression expression = expressions[i1];
      if (expression instanceof PsiThisExpression) {
        final PsiType exprType = expression.getType();
        for (CandidateInfo candidate : candidates) {
          PsiMethod method = (PsiMethod)candidate.getElement();
          PsiSubstitutor substitutor = candidate.getSubstitutor();
          PsiParameter[] parameters = method.getParameterList().getParameters();
          if (expressions.length != parameters.length) {
            continue;
          }

          PsiParameter parameter = parameters[i1];

          PsiType parameterType = substitutor.substitute(parameter.getType());
          if (exprType == null || parameterType == null) {
            continue;
          }

          if (!TypeConversionUtil.isAssignable(parameterType, exprType)) {
            final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(parameterType);
            if (psiClass != null && containingClasses.contains(psiClass)) {
              info.accept(new QualifyThisArgumentFix(expression, psiClass));
            }
          }
        }
      }
    }
  }
}
