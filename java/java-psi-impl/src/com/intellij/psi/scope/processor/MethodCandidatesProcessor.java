// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.scope.processor;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.conflictResolvers.DuplicateConflictResolver;
import com.intellij.psi.util.ImportsUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
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

  protected @NotNull MethodCandidateInfo createCandidateInfo(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor,
                                                             boolean staticProblem, boolean accessible, boolean varargs) {
    PsiType[] arguments = method.hasTypeParameters() ? getTypeArguments() : PsiType.EMPTY_ARRAY;
    return new VarargsAwareMethodCandidateInfo(method, substitutor, accessible, staticProblem, getArgumentList(), myCurrentFileContext,
                                               arguments, getLanguageLevel(), varargs);
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
        PsiMethod method = ObjectUtils.tryCast(result.getElement(), PsiMethod.class);
        if (method != null && method != candidate && result.isAccessible() &&
            !(result.getCurrentFileResolveScope() instanceof PsiImportStaticStatement) &&
            isInterfaceStaticMethodAccessibleThroughInheritance(method)) return true;
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

  @Override
  public void forceAddResult(@NotNull PsiMethod method) {
    add(createCandidateInfo(method, PsiSubstitutor.EMPTY, false, true, method.isVarArgs()));
  }
}
