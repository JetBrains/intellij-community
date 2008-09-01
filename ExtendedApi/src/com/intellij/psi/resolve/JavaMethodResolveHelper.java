/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.resolve;

import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver;
import com.intellij.psi.scope.conflictResolvers.DuplicateConflictResolver;
import com.intellij.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.util.MethodSignature;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * @author peter
 */
public class JavaMethodResolveHelper {
  private final Set<MethodSignature> myDuplicates = new THashSet<MethodSignature>();

  private final MethodCandidatesProcessor myProcessor;
  @Nullable private final PsiType[] myParameterTypes;

  public JavaMethodResolveHelper(final PsiElement argumentList, @Nullable final PsiType[] parameterTypes) {
    myParameterTypes = parameterTypes;
    final PsiConflictResolver resolver = parameterTypes == null ? DuplicateConflictResolver.INSTANCE : new JavaMethodsConflictResolver(argumentList, parameterTypes);
    myProcessor = new MethodResolverProcessor(argumentList, new PsiConflictResolver[]{resolver}) {

      protected MethodCandidateInfo createCandidateInfo(final PsiMethod method, final PsiSubstitutor substitutor,
                                                        final boolean staticProblem,
                                                        final boolean accessible) {
        return new MethodCandidateInfo(method, substitutor, !accessible, staticProblem, argumentList, myCurrentFileContext,
                                    parameterTypes, PsiType.EMPTY_ARRAY);
      }

      @Override
      protected boolean isAccepted(final PsiMethod candidate) {
        return !candidate.isConstructor();
      }
    };
  }

  public void addMethod(PsiMethod method, PsiSubstitutor substitutor, boolean staticError) {
    if (myDuplicates.add(method.getSignature(substitutor))) {
      myProcessor.addMethod(method, substitutor, staticError);
    }
  }

  @NotNull
  public ErrorType getResolveError() {
    final CandidateInfo[] candidates = myProcessor.getCandidates();
    if (candidates.length != 1) return ErrorType.RESOLVE;

    if (!candidates[0].isStaticsScopeCorrect()) return ErrorType.STATIC;

    final MethodCandidateInfo info = (MethodCandidateInfo)candidates[0];
    if (myParameterTypes == null) return ErrorType.NONE;

    if (!info.isApplicable()) {
      boolean hasNulls = false;
      final PsiParameter[] parameters = info.getElement().getParameterList().getParameters();
      if (myParameterTypes.length == parameters.length) {
        for (int i = 0; i < myParameterTypes.length; i++) {
          PsiType type = myParameterTypes[i];
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

  public static enum ErrorType {
    NONE, STATIC, RESOLVE
  }

  public Collection<PsiMethod> getMethods() {
    return ContainerUtil.mapNotNull(myProcessor.getResult(), new Function<JavaResolveResult, PsiMethod>() {
      public PsiMethod fun(final JavaResolveResult javaResolveResult) {
        return (PsiMethod)javaResolveResult.getElement();
      }
    });
  }
}
