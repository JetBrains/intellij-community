/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

public class ConstructorParametersFixer {
  public static void registerFixActions(@NotNull PsiJavaCodeReferenceElement ctrRef, PsiConstructorCall constructorCall, HighlightInfo highlightInfo,
                                         final TextRange fixRange) {
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
