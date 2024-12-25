/*
 * Copyright 2008-2019 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

class VariableValueUsedVisitor extends JavaRecursiveElementWalkingVisitor {

  private final @NotNull PsiVariable variable;
  private boolean read;
  private boolean written;

  VariableValueUsedVisitor(@NotNull PsiVariable variable) {
    this.variable = variable;
  }

  @Override
  public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
    if (read || written) return;
    super.visitReferenceExpression(expression);
    if (ExpressionUtils.isReferenceTo(expression, variable)) {
      if (PsiUtil.isAccessedForReading(expression)) {
        read = true;
      }
      else if (PsiUtil.isAccessedForWriting(expression)) {
        written = true;
      }
    }
  }

  boolean isVariableValueUsed() {
    return read;
  }
}
