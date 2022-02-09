// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MemberQualifierUtil {
  private static final Logger LOG = Logger.getInstance(MemberQualifierUtil.class);

  @Nullable
  public static String findObjectExpression(@NotNull PsiReferenceExpression reference,
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

  @NotNull
  public static String handleThisReference(@NotNull PsiElement reference,
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
        PsiType accessibleType = PsiReflectionAccessUtil.nearestAccessibleType(PsiTypesUtil.getClassType(referencedClass));
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
