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
package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class CanBeStaticVisitor extends JavaRecursiveElementWalkingVisitor {
  private boolean canBeStatic = true;

  @Override
  public void visitElement(@NotNull PsiElement element) {
    if (canBeStatic) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitThisExpression(@NotNull PsiThisExpression expression) {
    canBeStatic = false;
    stopWalking();
  }

  @Override
  public void visitNewExpression(@NotNull PsiNewExpression expression) {
    super.visitNewExpression(expression);
    PsiJavaCodeReferenceElement classRef = expression.getClassReference();
    if (expression.getQualifier() == null &&
        classRef != null && classRef.resolve() instanceof PsiClass cls &&
        !cls.hasModifierProperty(PsiModifier.STATIC) &&
        !ClassUtil.isTopLevelClass(cls)) {
      canBeStatic = false;
      stopWalking();
    }
  }

  @Override
  public void visitReferenceExpression(@NotNull PsiReferenceExpression ref) {
    super.visitReferenceExpression(ref);
    PsiElement element = ref.resolve();

    if (element instanceof PsiModifierListOwner owner &&
        !owner.hasModifierProperty(PsiModifier.STATIC) &&
        !(owner instanceof PsiClass cls && ClassUtil.isTopLevelClass(cls))) {
      canBeStatic = false;
      stopWalking();
    }
  }

  public boolean canBeStatic() {
    return canBeStatic;
  }
}
