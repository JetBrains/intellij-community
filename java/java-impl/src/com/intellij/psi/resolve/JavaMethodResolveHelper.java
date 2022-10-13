// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.resolve;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.conflictResolvers.DuplicateConflictResolver;
import com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver;
import com.intellij.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaMethodResolveHelper {
  private final Set<MethodSignature> myDuplicates = new HashSet<>();

  private final MethodCandidatesProcessor myProcessor;
  private final PsiType @Nullable [] myArgumentTypes;

  public JavaMethodResolveHelper(@NotNull final PsiElement argumentList, PsiFile containingFile, final PsiType @Nullable [] argumentTypes) {
    myArgumentTypes = argumentTypes;
    final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(argumentList);
    final PsiConflictResolver resolver = argumentTypes == null ? DuplicateConflictResolver.INSTANCE : new JavaMethodsConflictResolver(argumentList, argumentTypes,
                                                                                                                                      languageLevel, containingFile);
    myProcessor = new MethodResolverProcessor(argumentList, containingFile, new PsiConflictResolver[]{resolver}) {
      @Override
      protected @NotNull MethodCandidateInfo createCandidateInfo(@NotNull final PsiMethod method, @NotNull final PsiSubstitutor substitutor,
                                                                 final boolean staticProblem,
                                                                 final boolean accessible, final boolean varargs) {
        return JavaMethodResolveHelper.this
          .createCandidateInfo(method, substitutor, staticProblem, myCurrentFileContext, !accessible, argumentList, argumentTypes,
                               languageLevel, varargs);
      }

      @Override
      protected boolean isAccepted(@NotNull final PsiMethod candidate) {
        return !candidate.isConstructor();
      }
    };
  }

  @NotNull
  protected MethodCandidateInfo createCandidateInfo(@NotNull PsiMethod method,
                                                    PsiSubstitutor substitutor,
                                                    boolean staticProblem,
                                                    PsiElement currentFileContext,
                                                    boolean accessProblem,
                                                    PsiElement argumentList,
                                                    PsiType[] argumentTypes,
                                                    @NotNull LanguageLevel languageLevel, boolean vararg) {
    return new MethodCandidateInfo(method, substitutor, accessProblem, staticProblem, argumentList, currentFileContext, argumentTypes,
                                   PsiType.EMPTY_ARRAY, languageLevel) {
      @Override
      public boolean isVarargs() {
        return vararg;
      }
    };
  }

  public void addMethod(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor, boolean staticError) {
    if (myDuplicates.add(method.getSignature(substitutor))) {
      myProcessor.addMethod(method, substitutor, staticError);
    }
  }

  @NotNull
  public ErrorType getResolveError() {
    final List<CandidateInfo> candidates = getCandidates();
    if (candidates.size() != 1) return ErrorType.RESOLVE;

    if (!candidates.get(0).isStaticsScopeCorrect()) return ErrorType.STATIC;

    return getResolveError((MethodCandidateInfo)candidates.get(0));
  }

  protected List<CandidateInfo> getCandidates() {
    return Arrays.asList(myProcessor.getCandidates());
  }

  protected ErrorType getResolveError(MethodCandidateInfo info) {
    if (myArgumentTypes == null) return ErrorType.NONE;

    if (!info.isApplicable()) {
      boolean hasNulls = false;
      final PsiParameter[] parameters = info.getElement().getParameterList().getParameters();
      if (myArgumentTypes.length == parameters.length) {
        for (int i = 0; i < myArgumentTypes.length; i++) {
          PsiType type = myArgumentTypes[i];
          if (type == null) {
            hasNulls = true;
          }
          else if (!parameters[i].getType().isAssignableFrom(type)) {
            return ErrorType.RESOLVE;
          }
        }
      }
      return hasNulls ? ErrorType.NONE : ErrorType.RESOLVE;
    }
    return ErrorType.NONE;
  }

  public void handleEvent(final PsiScopeProcessor.Event event, final Object associated) {
    myProcessor.handleEvent(event, associated);
  }

  public enum ErrorType {
    NONE, STATIC, RESOLVE
  }

  public Collection<JavaMethodCandidateInfo> getMethods() {
    return ContainerUtil.mapNotNull(getCandidates(),
                                    (Function<JavaResolveResult, JavaMethodCandidateInfo>)javaResolveResult -> new JavaMethodCandidateInfo((PsiMethod)javaResolveResult.getElement(), javaResolveResult.getSubstitutor()));
  }
}
