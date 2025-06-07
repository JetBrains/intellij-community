/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

class VariableAssignedFromVisitor extends JavaRecursiveElementWalkingVisitor {
  private static final Logger LOG = Logger.getInstance(VariableAssignedFromVisitor.class);

  private boolean assignedFrom = false;

  private final @NotNull PsiVariable variable;

  VariableAssignedFromVisitor(@NotNull PsiVariable variable) {
    super();
    this.variable = variable;
  }

  @Override
  public void visitFile(@NotNull PsiFile psiFile) {
    LOG.error("Unexpectedly visited PsiFile " + psiFile + " when tracing variable " + variable);
    stopWalking();
  }

  @Override
  public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment) {
    super.visitAssignmentExpression(assignment);
    final PsiExpression arg = assignment.getRExpression();
    if (VariableAccessUtils.mayEvaluateToVariable(arg, variable)) {
      assignedFrom = true;
      stopWalking();
    }
  }

  @Override
  public void visitVariable(@NotNull PsiVariable var) {
    super.visitVariable(var);
    final PsiExpression initializer = var.getInitializer();
    if (VariableAccessUtils.mayEvaluateToVariable(initializer, variable)) {
      assignedFrom = true;
      stopWalking();
    }
  }

  public boolean isAssignedFrom() {
    return assignedFrom;
  }
}