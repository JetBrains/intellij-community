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
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

public class MethodResolverProcessor extends MethodCandidatesProcessor {
  private boolean myStopAcceptingCandidates;

  public MethodResolverProcessor(@NotNull PsiMethodCallExpression place, @NotNull PsiFile placeFile) {
    this(place, placeFile, new PsiConflictResolver[]{new JavaMethodsConflictResolver(place.getArgumentList(), null,
                                                                                     PsiUtil.getLanguageLevel(placeFile), placeFile)});
    setArgumentList(place.getArgumentList());
    obtainTypeArguments(place);
  }

  public MethodResolverProcessor(PsiClass classConstr, @NotNull PsiExpressionList argumentList, @NotNull PsiElement place, @NotNull PsiFile placeFile) {
    super(place, placeFile, new PsiConflictResolver[]{new JavaMethodsConflictResolver(argumentList, null,
                                                                                      PsiUtil.getLanguageLevel(placeFile), placeFile)},
          new SmartList<>());
    setIsConstructor(true);
    setAccessClass(classConstr);
    setArgumentList(argumentList);
  }

  public MethodResolverProcessor(@NotNull PsiElement place, @NotNull PsiFile placeFile, PsiConflictResolver @NotNull [] resolvers) {
    super(place, placeFile, resolvers, new SmartList<>());
  }

  @Override
  public void handleEvent(@NotNull Event event, Object associated) {
    if (event == JavaScopeProcessorEvent.CHANGE_LEVEL && myHasAccessibleStaticCorrectCandidate) {
      myStopAcceptingCandidates = true;
    }
    super.handleEvent(event, associated);
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    return !myStopAcceptingCandidates && super.execute(element, state);
  }

  @Override
  protected boolean acceptVarargs() {
    return true;
  }
}
