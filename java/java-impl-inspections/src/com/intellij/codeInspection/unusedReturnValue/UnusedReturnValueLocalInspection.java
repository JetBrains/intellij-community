// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.unusedReturnValue;

import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.reference.RefUtil;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.VisibilityUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("InspectionDescriptionNotFoundInspection") // via UnusedReturnValue
public class UnusedReturnValueLocalInspection extends AbstractBaseJavaLocalInspectionTool {
  private final UnusedReturnValue myGlobal;

  public UnusedReturnValueLocalInspection(UnusedReturnValue global) {myGlobal = global;}

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return myGlobal.getGroupDisplayName();
  }

  @Override
  @NotNull
  public String getShortName() {
    return myGlobal.getShortName();
  }

  @Override
  public ProblemDescriptor @Nullable [] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (method.isConstructor() ||
        PsiTypes.voidType().equals(method.getReturnType()) ||
        VisibilityUtil.compare(VisibilityUtil.getVisibilityModifier(method.getModifierList()), myGlobal.highestModifier) < 0 ||
        myGlobal.IGNORE_BUILDER_PATTERN && PropertyUtilBase.isSimplePropertySetter(method) ||
        method.hasModifierProperty(PsiModifier.NATIVE) ||
        MethodUtils.hasSuper(method) ||
        RefUtil.isImplicitRead(method) ||
        MethodUtils.hasCanIgnoreReturnValueAnnotation(method, method.getContainingFile()) ||
        UnusedDeclarationInspectionBase.isDeclaredAsEntryPoint(method)) return null;

    final boolean[] atLeastOneUsageExists = new boolean[]{false};
    if (UnusedSymbolUtil.processUsages(manager.getProject(), method.getContainingFile(), method, new EmptyProgressIndicator(), null, u -> {
      if (!atLeastOneUsageExists[0]) atLeastOneUsageExists[0] = true;
      PsiElement element = u.getElement();
      if (element instanceof PsiReferenceExpression) {
        PsiElement parent = element.getParent();
        if (parent instanceof PsiMethodCallExpression) {
          return ExpressionUtils.isVoidContext((PsiExpression)parent);
        }
      }
      return element instanceof PsiMethodReferenceExpression &&
             PsiTypes.voidType().equals(LambdaUtil.getFunctionalInterfaceReturnType((PsiFunctionalExpression)element));
    })) {
      if (atLeastOneUsageExists[0]) {
        return new ProblemDescriptor[]{UnusedReturnValue.createProblemDescriptor(method, manager, null, false, isOnTheFly)};
      }
    }
    return null;
  }
}
