/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.resolve;

import com.intellij.pom.java.LanguageLevel;
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
import com.intellij.psi.util.PsiUtil;
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
    final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(argumentList);
    final PsiConflictResolver resolver = parameterTypes == null ? DuplicateConflictResolver.INSTANCE : new JavaMethodsConflictResolver(argumentList, parameterTypes);
    myProcessor = new MethodResolverProcessor(argumentList, new PsiConflictResolver[]{resolver}) {
      protected MethodCandidateInfo createCandidateInfo(final PsiMethod method, final PsiSubstitutor substitutor,
                                                        final boolean staticProblem,
                                                        final boolean accessible) {
        return new MethodCandidateInfo(method, substitutor, !accessible, staticProblem, argumentList, myCurrentFileContext, parameterTypes,
                                       PsiType.EMPTY_ARRAY, languageLevel);
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

  public enum ErrorType {
    NONE, STATIC, RESOLVE
  }

  public Collection<JavaMethodCandidateInfo> getMethods() {
    return ContainerUtil.mapNotNull(myProcessor.getResult(), new Function<JavaResolveResult, JavaMethodCandidateInfo>() {
      public JavaMethodCandidateInfo fun(final JavaResolveResult javaResolveResult) {
        return new JavaMethodCandidateInfo((PsiMethod)javaResolveResult.getElement(), javaResolveResult.getSubstitutor());
      }
    });
  }
}
