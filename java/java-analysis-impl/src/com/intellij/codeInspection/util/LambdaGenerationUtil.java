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
package com.intellij.codeInspection.util;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Contract;

/**
 * Utility methods which are helpful to generate new lambda expressions in quick-fixes
 *
 * @author Tagir Valeev
 */
public class LambdaGenerationUtil {
  /**
   * Tests the expression whether it could be converted to the body of lambda expression
   * mapped to functional interface which SAM does not declare any checked exceptions.
   * The following things are checked:
   *
   * <p>1. The expression should not throw checked exceptions</p>
   * <p>2. The expression should not refer any variables which are not effectively final</p>
   *
   * @param lambdaCandidate an expression to test
   * @return true if this expression can be converted to lambda
   */
  @Contract("null -> false")
  public static boolean canBeUncheckedLambda(PsiExpression lambdaCandidate) {
    if(lambdaCandidate == null) return false;
    if(!ExceptionUtil.getThrownCheckedExceptions(new PsiElement[] {lambdaCandidate}).isEmpty()) return false;
    return PsiTreeUtil.processElements(lambdaCandidate, e -> {
      if (!(e instanceof PsiReferenceExpression)) return true;
      PsiElement element = ((PsiReferenceExpression)e).resolve();
      return !(element instanceof PsiVariable) ||
             HighlightControlFlowUtil.isEffectivelyFinal((PsiVariable)element, lambdaCandidate, null);
    });
  }
}
