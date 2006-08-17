/**
 * Propose to cast one argument to corresponding type
 *  in the constructor invocation
 * E.g.
 *
 * User: cdr
 * Date: Nov 13, 2002
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import org.jetbrains.annotations.NotNull;

public class CastConstructorParametersFix {
  public static void registerCastActions(@NotNull PsiJavaCodeReferenceElement ctrRef, PsiConstructorCall constructorCall, HighlightInfo highlightInfo) {
    JavaResolveResult resolved = ctrRef.advancedResolve(false);
    PsiClass aClass = (PsiClass) resolved.getElement();
    if (aClass == null) return;
    PsiMethod[] methods = aClass.getConstructors();
    CandidateInfo[] candidates = new CandidateInfo[methods.length];
    for (int i = 0; i < candidates.length; i++) {
      candidates[i] = new CandidateInfo(methods[i], resolved.getSubstitutor());
    }
    CastMethodArgumentFix.REGISTRAR.registerCastActions(candidates, constructorCall, highlightInfo);
  }
}
