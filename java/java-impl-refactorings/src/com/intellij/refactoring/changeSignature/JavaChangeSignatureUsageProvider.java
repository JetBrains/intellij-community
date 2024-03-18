// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public final class JavaChangeSignatureUsageProvider implements ChangeSignatureUsageProvider {
  private static final Logger LOG = Logger.getInstance(JavaChangeSignatureUsageProvider.class);

  @Override
  public @Nullable UsageInfo createOverrideUsageInfo(@NotNull ChangeInfo changeInfo,
                                                     @NotNull PsiElement overrider,
                                                     @NotNull PsiElement method,
                                                     boolean isOriginalOverrider,
                                                     boolean modifyArgs,
                                                     boolean modifyExceptions,
                                                     List<? super UsageInfo> result) {
    if (!(method instanceof PsiMethod)) {
      JavaChangeInfo javaChangeInfo = JavaChangeInfoConverters.getJavaChangeInfo(changeInfo, new UsageInfo(overrider));
      if (javaChangeInfo == null) return null;
      method = javaChangeInfo.getMethod();
    }
    if (overrider instanceof PsiFunctionalExpression) {
      return new FunctionalInterfaceChangedUsageInfo(overrider, (PsiMethod)method);
    }
    LOG.assertTrue(overrider instanceof PsiMethod);

    return new OverriderUsageInfo((PsiMethod)overrider, (PsiMethod)method, isOriginalOverrider, modifyArgs, modifyExceptions);
  }

  @Override
  public @Nullable UsageInfo createUsageInfo(@NotNull ChangeInfo changeInfo,
                                             @NotNull PsiReference reference,
                                             @NotNull PsiElement m,
                                             boolean isToModifyArgs,
                                             boolean isToThrowExceptions) {
    JavaChangeInfo javaChangeInfo = JavaChangeInfoConverters.getJavaChangeInfo(changeInfo, new UsageInfo(reference));
    if (javaChangeInfo == null) return null;

    PsiMethod method = javaChangeInfo.getMethod();
    if (m instanceof PsiMethod) {
      //in case of propagation, completely different method
      //todo if it's foreign language override, keep base method and hope it's fine
      method = (PsiMethod)m;
    }

    PsiElement element = reference.getElement();

    boolean isToCatchExceptions = isToThrowExceptions &&
                                  needToCatchExceptions(javaChangeInfo, RefactoringUtil.getEnclosingMethod(element));
    if (!isToCatchExceptions) {
      if (RefactoringUtil.isMethodUsage(element)) {
        PsiExpressionList list = RefactoringUtil.getArgumentListByMethodReference(element);
        if (list == null || !method.isVarArgs() && list.getExpressionCount() != method.getParameterList().getParametersCount()) return null;
        if (method.isVarArgs() &&
            reference instanceof PsiReferenceExpression &&
            !((PsiReferenceExpression)reference).advancedResolve(true).isValidResult()) {
          return null;
        }
      }
    }
    if (RefactoringUtil.isMethodUsage(element)) {
      return new MethodCallUsageInfo(element, isToModifyArgs, isToCatchExceptions);
    }
    else if (element instanceof PsiDocTagValue) {
      return new UsageInfo(element);
    }
    else if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
      return new DefaultConstructorImplicitUsageInfo((PsiMethod)element, ((PsiMethod)element).getContainingClass(), method);
    }
    else if (element instanceof PsiClass psiClass) {
      LOG.assertTrue(method.isConstructor());
      if (javaChangeInfo instanceof JavaChangeInfoImpl) {
        UsageInfo info = shouldPropagateToNonPhysicalMethod(method, psiClass, ((JavaChangeInfoImpl)javaChangeInfo).propagateParametersMethods);
        if (info != null) {
          return info;
        }
        info = shouldPropagateToNonPhysicalMethod(method, psiClass, ((JavaChangeInfoImpl)javaChangeInfo).propagateExceptionsMethods);
        if (info != null) {
          return info;
        }
      }
      return new NoConstructorClassUsageInfo(psiClass);
    }
    else if (reference instanceof PsiCallReference) {
      return new CallReferenceUsageInfo((PsiCallReference)reference);
    }
    else if (element instanceof PsiMethodReferenceExpression && MethodReferenceUsageInfo.needToExpand(javaChangeInfo)) {
      return new MethodReferenceUsageInfo(element, isToModifyArgs, isToCatchExceptions);
    }
    else {
      return new MoveRenameUsageInfo(element, reference, method);
    }
  }

  private static UsageInfo shouldPropagateToNonPhysicalMethod(PsiMethod method,
                                                              PsiClass containingClass,
                                                              final Set<? extends PsiMethod> propagateMethods) {
    for (PsiMethod psiMethod : propagateMethods) {
      if (!psiMethod.isPhysical() && Comparing.strEqual(psiMethod.getName(), containingClass.getName())) {
        return new DefaultConstructorImplicitUsageInfo(psiMethod, containingClass, method);
      }
    }
    return null;
  }

  private static boolean needToCatchExceptions(JavaChangeInfo changeInfo, PsiMethod caller) {
    if (changeInfo instanceof JavaChangeInfoImpl) {
      return changeInfo.isExceptionSetOrOrderChanged() &&
             !((JavaChangeInfoImpl)changeInfo).propagateExceptionsMethods.contains(caller);
    }
    else {
      return changeInfo.isExceptionSetOrOrderChanged();
    }
  }
}
