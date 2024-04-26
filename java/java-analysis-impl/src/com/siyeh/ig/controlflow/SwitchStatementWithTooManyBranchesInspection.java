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

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiSwitchExpression;
import com.intellij.psi.PsiSwitchStatement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SwitchUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.number;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class SwitchStatementWithTooManyBranchesInspection extends BaseInspection {

  private static final int DEFAULT_BRANCH_LIMIT = 10;

  @SuppressWarnings("PublicField")
  public int m_limit = DEFAULT_BRANCH_LIMIT;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      number("m_limit", InspectionGadgetsBundle.message(
        "if.statement.with.too.many.branches.max.option"), 0, 1000));
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final Integer branchCount = (Integer)infos[0];
    return InspectionGadgetsBundle.message(
      "if.statement.with.too.many.branches.problem.descriptor",
      branchCount);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SwitchStatementWithTooManyBranchesVisitor();
  }

  private class SwitchStatementWithTooManyBranchesVisitor extends BaseInspectionVisitor {
    @Override
    public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
      processSwitch(expression);
    }

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      processSwitch(statement);
    }

    public void processSwitch(PsiSwitchBlock expression) {
      final int branchCount = SwitchUtils.calculateBranchCount(expression);
      final int branchCountExcludingDefault = (branchCount < 0) ? -branchCount - 1 : branchCount;
      if (branchCountExcludingDefault <= m_limit) {
        return;
      }
      registerError(expression.getFirstChild(), Integer.valueOf(branchCountExcludingDefault));
    }
  }
}