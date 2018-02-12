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
package com.intellij.psi.scope.processor;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.conflictResolvers.DuplicateConflictResolver;
import com.intellij.psi.util.ImportsUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author ik
 */
public class MethodCandidatesProcessor extends MethodsProcessor{
  protected boolean myHasAccessibleStaticCorrectCandidate;

  public MethodCandidatesProcessor(@NotNull PsiElement place, PsiFile placeFile, @NotNull PsiConflictResolver[] resolvers, @NotNull List<CandidateInfo> container) {
    super(resolvers, container, place, placeFile);
  }

  public MethodCandidatesProcessor(@NotNull PsiElement place, PsiFile placeFile) {
    super(new PsiConflictResolver[]{DuplicateConflictResolver.INSTANCE}, new SmartList<>(), place, placeFile);
  }

  @Override
  public void add(@NotNull PsiElement element, @NotNull PsiSubstitutor substitutor) {
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      addMethod(method, substitutor, isInStaticScope() && !method.hasModifierProperty(PsiModifier.STATIC));
    }
  }

  public void addMethod(@NotNull PsiMethod method, final PsiSubstitutor substitutor, boolean staticProblem) {
    final boolean isAccessible = JavaResolveUtil.isAccessible(method, getContainingClass(method), method.getModifierList(),
                                                              myPlace, myAccessClass, myCurrentFileContext, myPlaceFile) &&
                                 !isShadowed(method);
    if (isAccepted(method) && !(isInterfaceStaticMethodAccessibleThroughInheritance(method) && ImportsUtil.hasStaticImportOn(myPlace, method, true))) {
      if (!staticProblem && myAccessClass != null && method.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && 
            containingClass.isInterface() &&
            !(myAccessClass instanceof PsiTypeParameter) &&
            !containingClass.equals(myAccessClass)) {
          staticProblem = true;
        }
      }
      add(createCandidateInfo(method, substitutor, staticProblem, isAccessible, false));
      if (acceptVarargs() && method.isVarArgs() && PsiUtil.isLanguageLevel8OrHigher(myPlace)) {
        add(createCandidateInfo(method, substitutor, staticProblem, isAccessible, true));
      }
      myHasAccessibleStaticCorrectCandidate |= isAccessible;// && !staticProblem;
    }
  }

  private boolean isInterfaceStaticMethodAccessibleThroughInheritance(PsiMethod method) {
    if (method.hasModifierProperty(PsiModifier.STATIC) && 
        !(myCurrentFileContext instanceof PsiImportStaticStatement) && 
        myPlace instanceof PsiMethodCallExpression && 
        ((PsiMethodCallExpression)myPlace).getMethodExpression().getQualifierExpression() == null) {
      final PsiClass containingClass = method.getContainingClass();
      return containingClass != null && containingClass.isInterface();
    }
    return false;
  }

  protected PsiClass getContainingClass(PsiMethod method) {
    return method.getContainingClass();
  }

  protected boolean acceptVarargs() {
    return false;
  }

  protected MethodCandidateInfo createCandidateInfo(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor,
                                                    final boolean staticProblem, final boolean accessible, final boolean varargs) {
    final PsiExpressionList argumentList = getArgumentList();
    return new MethodCandidateInfo(method, substitutor, !accessible, staticProblem, argumentList, myCurrentFileContext,
                                   null, getTypeArguments(), getLanguageLevel()) {

      private PsiType[] myExpressionTypes;

      @Override
      public PsiType[] getArgumentTypes() {
        if (myExpressionTypes == null && argumentList != null) {
          final PsiType[] expressionTypes = getExpressionTypes(argumentList);
          if (MethodCandidateInfo.isOverloadCheck() || LambdaUtil.isLambdaParameterCheck()) {
            return expressionTypes;
          }
          myExpressionTypes = expressionTypes;
        }
        return myExpressionTypes;
      }

      @Override
      public boolean isVarargs() {
        return varargs;
      }
    };
  }

  protected static PsiType[] getExpressionTypes(PsiExpressionList argumentList) {
    return argumentList != null ? argumentList.getExpressionTypes() : null;
  }

  protected boolean isAccepted(final PsiMethod candidate) {
    if (!isConstructor()) {
      return !candidate.isConstructor() && candidate.getName().equals(getName(ResolveState.initial()));
    }
    else {
      if (!candidate.isConstructor()) return false;
      if (myAccessClass == null) return true;
      if (myAccessClass instanceof PsiAnonymousClass) {
        final PsiClass containingClass = getContainingClass(candidate);
        return containingClass != null && containingClass.equals(myAccessClass.getSuperClass());
      }
      return myAccessClass.isEquivalentTo(getContainingClass(candidate));
    }
  }

  protected boolean isShadowed(final PsiMethod candidate) {
    if (myCurrentFileContext instanceof PsiImportStaticStatement) {
      for (JavaResolveResult result : getResults()) {
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
