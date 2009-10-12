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

/*
 * User: anna
 * Date: 25-Aug-2008
 */
package com.intellij.refactoring.extractclass;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;

public class BackpointerUtil {
  private BackpointerUtil() {
  }

  public static boolean isBackpointerReference(PsiExpression expression, Condition<PsiField> value) {
    if (expression instanceof PsiParenthesizedExpression) {
      final PsiExpression contents = ((PsiParenthesizedExpression)expression).getExpression();
      return isBackpointerReference(contents, value);
    }
    if (!(expression instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression reference = (PsiReferenceExpression)expression;
    final PsiElement qualifier = reference.getQualifier();
    if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
      return false;
    }
    final PsiElement referent = reference.resolve();
    return referent instanceof PsiField && value.value((PsiField)referent);
  }
}