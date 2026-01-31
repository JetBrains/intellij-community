// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MemberQualifierUtil {
  private static final Logger LOG = Logger.getInstance(MemberQualifierUtil.class);

  public static @Nullable String findObjectExpression(@NotNull PsiReferenceExpression reference,
                                                      @NotNull PsiMember member,
                                                      @NotNull PsiClass outerClass,
                                                      @NotNull PsiMethodCallExpression generatedCall,
                                                      @NotNull PsiElementFactory elementFactory) {
    if (member.hasModifierProperty(PsiModifier.STATIC)) {
      return null;
    }

    PsiExpression qualifierExpression = reference.getQualifierExpression();
    if (qualifierExpression != null) {
      PsiType expressionType = qualifierExpression.getType();
      return expressionType == null ? null : qualifierExpression.getText();
    }

    JavaResolveResult resolveResult = reference.advancedResolve(false);
    PsiElement resolveScope = resolveResult.getCurrentFileResolveScope();
    if (resolveScope instanceof PsiClass) {
      return handleThisReference(reference, (PsiClass)resolveScope, outerClass, generatedCall, elementFactory);
    }

    return null;
  }

  public static @NotNull String handleThisReference(@NotNull PsiElement reference,
                                                    @NotNull PsiClass referencedClass,
                                                    @NotNull PsiClass outerClass,
                                                    @NotNull PsiMethodCallExpression generatedCall,
                                                    @NotNull PsiElementFactory elementFactory) {
    String qualifiedName = referencedClass.getName();
    if (qualifiedName == null) {
      // TODO: handle this case as well
      LOG.warn("Anonymous and local classes are not supported yet");
    }
    else {
      if (!PsiTreeUtil.isAncestor(outerClass, referencedClass, false)) {
        PsiType accessibleType = PsiReflectionAccessUtil.nearestAccessibleType(PsiTypesUtil.getClassType(referencedClass), reference);
        PsiMethod generatedMethod = (PsiMethod)PsiTreeUtil
          .findFirstParent(reference, x -> x instanceof PsiMethod && "invoke".equals(((PsiMethod)x).getName()));
        if (generatedMethod == null) {
          // TODO: provide this generated method somehow outside!
          LOG.warn("Could not find method 'invoke' in the generated class");
          return "this";
        }
        PsiParameterList parameterList = generatedMethod.getParameterList();
        String referredObjectName = "outerContext" + parameterList.getParametersCount();
        parameterList.add(elementFactory.createParameter(referredObjectName, accessibleType));
        generatedCall.getArgumentList().add(elementFactory.createExpressionFromText(qualifiedName + ".this", null));
        return referredObjectName;
      }
    }

    return "this";
  }
}
