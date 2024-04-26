/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiSwitchExpression;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.util.JavaElementKind;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public final class NestedSwitchStatementInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "nested.switch.statement.problem.descriptor",
      JavaElementKind.fromElement((PsiElement)infos[0]).subject());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NestedSwitchStatementVisitor();
  }

  private static class NestedSwitchStatementVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      super.visitSwitchStatement(statement);
      if (ControlFlowUtils.isNestedElement(statement, PsiSwitchBlock.class)) {
        registerStatementError(statement, statement);
      }
    }

    @Override
    public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
      super.visitSwitchExpression(expression);
      if (ControlFlowUtils.isNestedElement(expression, PsiSwitchBlock.class)) {
        registerError(expression.getFirstChild(), expression);
      }
    }
  }
}