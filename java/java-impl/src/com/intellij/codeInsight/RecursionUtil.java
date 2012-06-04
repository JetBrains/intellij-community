/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Danila Ponomarenko
 */
public class RecursionUtil {
  public static boolean isRecursiveMethodCall(@NotNull PsiMethodCallExpression methodCall) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class);
    if (method == null) {
      return false;
    }

    final PsiMethod resolvedMethod = methodCall.resolveMethod();

    if (!Comparing.equal(method, resolvedMethod)) {
      return false;
    }
    final PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
    return qualifier == null || qualifier instanceof PsiThisExpression;
  }
}
