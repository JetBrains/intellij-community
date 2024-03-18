/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ipp.initialization;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NotNull;

class SplitDeclarationAndInitializationPredicate
  implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(@NotNull PsiElement element) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiField field)) {
      return false;
    }
    if (element instanceof PsiComment &&
        element == parent.getFirstChild()) {
      return false;
    }
    final PsiExpression initializer = field.getInitializer();
    if (initializer == null) {
      return false;
    }
    final PsiClass containingClass = field.getContainingClass();
    if (containingClass == null || containingClass.isInterface() || containingClass instanceof PsiImplicitClass) {
      return false;
    }
    if (containingClass.isRecord() && !field.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    return !ErrorUtil.containsError(field);
  }
}
