// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
  Propose to cast one argument to corresponding type
   in the constructor invocation
  E.g.

  User: cdr
  Date: Nov 13, 2002
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public final class ConstructorParametersFixer {
  public static void registerFixActions(@NotNull PsiConstructorCall constructorCall,
                                        @NotNull Consumer<? super CommonIntentionAction> info) {
    if (constructorCall instanceof PsiNewExpression newExpression) {
      PsiJavaCodeReferenceElement ctrRef = newExpression.getClassOrAnonymousClassReference();
      if (ctrRef == null) return;
      JavaResolveResult resolved = ctrRef.advancedResolve(false);
      PsiClass aClass = (PsiClass) resolved.getElement();
      PsiSubstitutor substitutor = resolved.getSubstitutor();
      if (aClass == null) return;
      registerFixActions(aClass, substitutor, constructorCall, info);
    } else if (constructorCall instanceof PsiEnumConstant enumConstant) {
      PsiClass containingClass = enumConstant.getContainingClass();
      if (containingClass != null) {
        registerFixActions(containingClass, PsiSubstitutor.EMPTY, constructorCall, info);
      }
    }
  }

  private static void registerFixActions(@NotNull PsiClass aClass,
                                         @NotNull PsiSubstitutor substitutor,
                                         @NotNull PsiConstructorCall constructorCall,
                                         @NotNull Consumer<? super CommonIntentionAction> info) {
    PsiMethod[] methods = aClass.getConstructors();
    CandidateInfo[] candidates = new CandidateInfo[methods.length];
    for (int i = 0; i < candidates.length; i++) {
      candidates[i] = new CandidateInfo(methods[i], substitutor);
    }
    CastMethodArgumentFix.REGISTRAR.registerCastActions(candidates, constructorCall, info);
    AddTypeArgumentsFix.REGISTRAR.registerCastActions(candidates, constructorCall, info);
    WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(candidates, constructorCall, info);
    WrapWithAdapterMethodCallFix.registerCastActions(candidates, constructorCall, info);
  }
}
