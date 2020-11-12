// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.scope.processor;

import com.intellij.pom.java.LanguageLevel;
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

import java.util.Arrays;
import java.util.List;

public class MethodCandidatesProcessor extends MethodsProcessor{
  boolean myHasAccessibleStaticCorrectCandidate;

  protected MethodCandidatesProcessor(@NotNull PsiElement place,
                                      PsiFile placeFile,
                                      PsiConflictResolver @NotNull [] resolvers,
                                      @NotNull List<CandidateInfo> container) {
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

  public void addMethod(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor, boolean staticProblem) {
    final boolean isAccessible = JavaResolveUtil.isAccessible(method, getContainingClass(method), method.getModifierList(),
                                                              myPlace, myAccessClass, myCurrentFileContext, myPlaceFile) &&
                                 !isShadowed(method);
    boolean problematicInterfaceStaticMethod = false;
    if (isAccepted(method) && !(isInterfaceStaticMethodAccessibleThroughInheritance(method) && ImportsUtil.hasStaticImportOn(myPlace, method, true))) {
      if (!staticProblem && method.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null &&
            containingClass.isInterface() &&
            !(myAccessClass instanceof PsiTypeParameter) &&
            !containingClass.getManager().areElementsEquivalent(myAccessClass, containingClass)) {
          if (myAccessClass != null) {
            staticProblem = true;
          }
          else {
            problematicInterfaceStaticMethod = true;
          }
        }
      }
      add(createCandidateInfo(method, substitutor, staticProblem, isAccessible, false));
      if (acceptVarargs() && method.isVarArgs() && PsiUtil.isLanguageLevel8OrHigher(myPlace)) {
        add(createCandidateInfo(method, substitutor, staticProblem, isAccessible, true));
      }
      myHasAccessibleStaticCorrectCandidate |= isAccessible && !problematicInterfaceStaticMethod;// && !staticProblem;
    }
  }

  private boolean isInterfaceStaticMethodAccessibleThroughInheritance(@NotNull PsiMethod method) {
    if (method.hasModifierProperty(PsiModifier.STATIC) &&
        !(myCurrentFileContext instanceof PsiImportStaticStatement) &&
        myPlace instanceof PsiMethodCallExpression &&
        ((PsiMethodCallExpression)myPlace).getMethodExpression().getQualifierExpression() == null) {
      final PsiClass containingClass = method.getContainingClass();
      return containingClass != null && containingClass.isInterface();
    }
    return false;
  }

  protected PsiClass getContainingClass(@NotNull PsiMethod method) {
    return method.getContainingClass();
  }

  protected boolean acceptVarargs() {
    return false;
  }

  @NotNull
  protected MethodCandidateInfo createCandidateInfo(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor,
                                                    final boolean staticProblem, final boolean accessible, final boolean varargs) {
    return new VarargsAwareMethodCandidateInfo(method, substitutor, accessible, staticProblem, getArgumentList(), myCurrentFileContext,
                                               getTypeArguments(), getLanguageLevel(), varargs);
  }

  private static PsiType @NotNull [] getExpressionTypes(@NotNull PsiExpressionList argumentList) {
    return argumentList.getExpressionTypes();
  }

  protected boolean isAccepted(@NotNull PsiMethod candidate) {
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

  private boolean isShadowed(@NotNull PsiMethod candidate) {
    if (myCurrentFileContext instanceof PsiImportStaticStatement) {
      for (JavaResolveResult result : getResults()) {
        if (result.getElement() != candidate &&
            result.isAccessible() &&
            !(result.getCurrentFileResolveScope() instanceof PsiImportStaticStatement)) return true;
      }
    }
    return false;
  }

  public CandidateInfo @NotNull [] getCandidates() {
    final JavaResolveResult[] resolveResult = getResult();
    if (resolveResult.length == 0) return CandidateInfo.EMPTY_ARRAY;
    return Arrays.copyOf(resolveResult, resolveResult.length, CandidateInfo[].class);
  }

  private static class VarargsAwareMethodCandidateInfo extends MethodCandidateInfo {
    private final PsiExpressionList myArgumentList;
    private final boolean myVarargs;
    private PsiType[] myExpressionTypes;

    VarargsAwareMethodCandidateInfo(@NotNull PsiMethod method,
                                    @NotNull PsiSubstitutor substitutor,
                                    boolean accessible,
                                    boolean staticProblem,
                                    PsiExpressionList argumentList,
                                    PsiElement context, PsiType[] arguments, @NotNull LanguageLevel level, boolean varargs) {
      super(method, substitutor, !accessible, staticProblem, argumentList, context, null, arguments, level);
      myArgumentList = argumentList;
      myVarargs = varargs;
    }

    @Override
    public PsiType[] getArgumentTypes() {
      if (myExpressionTypes == null && myArgumentList != null) {
        final PsiType[] expressionTypes = getExpressionTypes(myArgumentList);
        if (isOverloadCheck()) {
          return expressionTypes;
        }
        myExpressionTypes = expressionTypes;
      }
      return myExpressionTypes;
    }

    @Override
    public boolean isVarargs() {
      return myVarargs;
    }
  }
}
