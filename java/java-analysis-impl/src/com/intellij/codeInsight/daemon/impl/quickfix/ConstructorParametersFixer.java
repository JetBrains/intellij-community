// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
  Propose to cast one argument to corresponding type
   in the constructor invocation
  E.g.

  User: cdr
  Date: Nov 13, 2002
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import org.jetbrains.annotations.NotNull;

public final class ConstructorParametersFixer {
  public static void registerFixActions(@NotNull PsiJavaCodeReferenceElement ctrRef,
                                        @NotNull PsiConstructorCall constructorCall,
                                        @NotNull HighlightInfo highlightInfo,
                                        @NotNull TextRange fixRange) {
    JavaResolveResult resolved = ctrRef.advancedResolve(false);
    PsiClass aClass = (PsiClass) resolved.getElement();
    if (aClass == null) return;
    PsiMethod[] methods = aClass.getConstructors();
    CandidateInfo[] candidates = new CandidateInfo[methods.length];
    for (int i = 0; i < candidates.length; i++) {
      candidates[i] = new CandidateInfo(methods[i], resolved.getSubstitutor());
    }
    CastMethodArgumentFix.REGISTRAR.registerCastActions(candidates, constructorCall, highlightInfo, fixRange);
    AddTypeArgumentsFix.REGISTRAR.registerCastActions(candidates, constructorCall, highlightInfo, fixRange);
    WrapObjectWithOptionalOfNullableFix.REGISTAR.registerCastActions(candidates, constructorCall, highlightInfo, fixRange);
    WrapWithAdapterMethodCallFix.registerCastActions(candidates, constructorCall, highlightInfo, fixRange);
  }
}
