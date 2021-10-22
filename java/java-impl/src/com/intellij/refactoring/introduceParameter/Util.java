// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class Util {

  public static void analyzeExpression(PsiExpression expr,
                                       List<? super UsageInfo> localVars,
                                       List<? super UsageInfo> classMemberRefs,
                                       List<? super UsageInfo> params) {

    if (expr instanceof PsiQualifiedExpression) {
      classMemberRefs.add(new ClassMemberInExprUsageInfo(expr));
    }
    else if (expr instanceof PsiReferenceExpression) {
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)expr;

      PsiElement subj = refExpr.resolve();

      if (subj instanceof PsiParameter) {
        params.add(new ParameterInExprUsageInfo(refExpr));
      }
      else if (subj instanceof PsiLocalVariable) {
        localVars.add(new LocalVariableInExprUsageInfo(refExpr));
      }
      else if (subj instanceof PsiField || subj instanceof PsiMethod) {
        classMemberRefs.add(new ClassMemberInExprUsageInfo(refExpr));
      }

    }

    PsiElement[] children = expr.getChildren();

    for (PsiElement child : children) {
      if (child instanceof PsiExpression) {
        analyzeExpression((PsiExpression)child, localVars, classMemberRefs, params);
      }
    }
  }

  @NotNull
  private static PsiElement getPhysical(@NotNull PsiElement expr) {
    PsiElement physicalElement = expr.getUserData(ElementToWorkOn.PARENT);
    if (physicalElement != null) expr = physicalElement;
    return expr;
  }

  public static PsiMethod getContainingMethod(PsiElement expr) {
    return PsiTreeUtil.getParentOfType(getPhysical(expr), PsiMethod.class);
  }
  public static boolean isAncestor(PsiElement ancestor, PsiElement element, boolean strict) {
    final TextRange exprRange = ancestor.getUserData(ElementToWorkOn.EXPR_RANGE);
    if (exprRange != null) {
      return exprRange.contains(element.getTextRange());
    }
    return PsiTreeUtil.isAncestor(getPhysical(ancestor), getPhysical(element), strict);
  }

  public static boolean anyFieldsWithGettersPresent(List<? extends UsageInfo> classMemberRefs) {
    for (UsageInfo usageInfo : classMemberRefs) {

      if (usageInfo.getElement() instanceof PsiReferenceExpression) {
        PsiElement e = ((PsiReferenceExpression)usageInfo.getElement()).resolve();

        if (e instanceof PsiField) {
          PsiField psiField = (PsiField)e;
          PsiMethod getterPrototype = GenerateMembersUtil.generateGetterPrototype(psiField);

          PsiMethod getter = psiField.getContainingClass().findMethodBySignature(getterPrototype, true);

          if (getter != null) return true;
        }
      }
    }

    return false;
  }

  // returns parameters that are used solely in specified expression
  @NotNull
  public static IntList findParametersToRemove(@NotNull PsiMethod method,
                                               @NotNull final PsiExpression expr,
                                               final PsiExpression @Nullable [] occurences) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length == 0) {
      return new IntArrayList();
    }

    IntSet suspects = new IntOpenHashSet();
    expr.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(final PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiParameter) {
          int i = ArrayUtil.find(parameters, resolved);
          if (i != -1) {
            suspects.add(i);
          }
        }
      }
    });

    removeUsed(method, expr, occurences, suspects);

    if (suspects.isEmpty()) {
      return new IntArrayList();
    }

    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      OverridingMethodsSearch.search(method).forEach(psiMethod -> {
        ReadAction.run(() -> removeUsed(psiMethod, expr, occurences, suspects));
        return !suspects.isEmpty();
      });
    }, JavaBundle.message("progress.title.search.for.overriding.methods"), true, method.getProject())) {
      return new IntArrayList();
    }

    return new IntArrayList(suspects);
  }

  private static void removeUsed(PsiMethod containingMethod, @NotNull PsiExpression expr,
                                 PsiExpression @Nullable [] occurences,
                                 IntSet suspects) {
    final IntIterator iterator = suspects.iterator();
    while (iterator.hasNext()) {
      final int paramNum = iterator.nextInt();
      PsiParameter[] psiParameters = containingMethod.getParameterList().getParameters();
      if (paramNum >= psiParameters.length) continue;
      PsiParameter parameter = psiParameters[paramNum];
      ReferencesSearch.search(parameter, parameter.getResolveScope(), false).forEach(reference -> {
        PsiElement element = reference.getElement();
        boolean stillCanBeRemoved = isAncestor(expr, element, false) || PsiUtil.isInsideJavadocComment(getPhysical(element));
        if (!stillCanBeRemoved && occurences != null) {
          for (PsiExpression occurence : occurences) {
            if (isAncestor(occurence, element, false)) {
              stillCanBeRemoved = true;
              break;
            }
          }
        }
        if (!stillCanBeRemoved) {
          iterator.remove();
          return false;
        }
        return true;
      });
    }
  }
}
