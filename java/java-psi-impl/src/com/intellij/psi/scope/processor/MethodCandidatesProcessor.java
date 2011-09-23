/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.scope.processor;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.conflictResolvers.DuplicateConflictResolver;
import com.intellij.util.SmartList;

/**
 * @author ik
 * Date: 31.01.2003
 */
public class MethodCandidatesProcessor extends MethodsProcessor{
  protected boolean myHasAccessibleStaticCorrectCandidate = false;

  public MethodCandidatesProcessor(PsiElement place, PsiConflictResolver[] resolvers, SmartList<CandidateInfo> container) {
    super(resolvers, container, place);
  }

  public MethodCandidatesProcessor(PsiElement place) {
    super(new PsiConflictResolver[]{DuplicateConflictResolver.INSTANCE}, new SmartList<CandidateInfo>(), place);
  }

  public void add(PsiElement element, PsiSubstitutor substitutor) {
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      addMethod(method, substitutor, isInStaticScope() && !method.hasModifierProperty(PsiModifier.STATIC));
    }
  }

  public void addMethod(final PsiMethod method, final PsiSubstitutor substitutor, final boolean staticProblem) {
    final boolean isAccessible = JavaResolveUtil.isAccessible(method, method.getContainingClass(), method.getModifierList(),
                                                              myPlace, myAccessClass, myCurrentFileContext, myPlaceFile) &&
                                 !isShadowed(method);
    if (isAccepted(method)) {
      add(createCandidateInfo(method, substitutor, staticProblem, isAccessible));
      myHasAccessibleStaticCorrectCandidate |= isAccessible && !staticProblem;
    }
  }

  protected MethodCandidateInfo createCandidateInfo(final PsiMethod method, final PsiSubstitutor substitutor,
                                                    final boolean staticProblem, final boolean accessible) {
    return new MethodCandidateInfo(method, substitutor, !accessible, staticProblem, getArgumentList(), myCurrentFileContext,
                                   getArgumentList().getExpressionTypes(), getTypeArguments(), getLanguageLevel());
  }

  protected boolean isAccepted(final PsiMethod candidate) {
    if (!isConstructor()) {
      return !candidate.isConstructor() && candidate.getName().equals(getName(ResolveState.initial()));
    }
    else {
      if (!candidate.isConstructor()) return false;
      if (myAccessClass == null) return true;
      if (myAccessClass instanceof PsiAnonymousClass) {
        final PsiClass containingClass = candidate.getContainingClass();
        return containingClass != null && containingClass.equals(myAccessClass.getSuperClass());
      }
      return myAccessClass.equals(candidate.getContainingClass());
    }
  }

  protected boolean isShadowed(final PsiMethod candidate) {
    if (myCurrentFileContext instanceof PsiImportStaticStatement) {
      for (JavaResolveResult result : getResult()) {
        if (result.getElement() != candidate &&
            result.isAccessible() &&
            !(result.getCurrentFileResolveScope() instanceof PsiImportStaticStatement)) return true;
      }
    }
    return false;
  }

  public CandidateInfo[] getCandidates() {
    final JavaResolveResult[] resolveResult = getResult();
    if (resolveResult.length == 0) return CandidateInfo.EMPTY_ARRAY;
    final CandidateInfo[] infos = new CandidateInfo[resolveResult.length];
    //noinspection SuspiciousSystemArraycopy
    System.arraycopy(resolveResult, 0, infos, 0, resolveResult.length);
    return infos;
  }
}
