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
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SwitchUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.number;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class SwitchStatementDensityInspection extends BaseInspection {

  private static final int DEFAULT_DENSITY_LIMIT = 20;

  @SuppressWarnings("PublicField")
  public int m_limit = DEFAULT_DENSITY_LIMIT;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      number("m_limit", InspectionGadgetsBundle.message("switch.statement.density.min.option"), 1, 10000));
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final Integer intDensity = (Integer)infos[0];
    return InspectionGadgetsBundle.message("switch.statement.density.problem.descriptor", intDensity);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SwitchStatementDensityVisitor();
  }

  private class SwitchStatementDensityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      checkSwitchBlock(statement);
    }

    @Override
    public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
      checkSwitchBlock(expression);
    }

    private void checkSwitchBlock(PsiSwitchBlock block) {
      final PsiCodeBlock body = block.getBody();
      if (body == null) {
        return;
      }
      final int branchCount = Math.abs(SwitchUtils.calculateBranchCount(block));
      if (branchCount == 0) {
        return;
      }
      final double density = calculateDensity(body, branchCount);
      final int intDensity = (int)(density * 100.0);
      if (intDensity > m_limit) {
        return;
      }
      registerError(block.getFirstChild(), Integer.valueOf(intDensity));
    }

    private static double calculateDensity(@NotNull PsiCodeBlock body, int branchCount) {
      final StatementCountVisitor visitor = new StatementCountVisitor();
      body.accept(visitor);
      return (double)branchCount / (double)visitor.getStatementCount();
    }
  }

  private static class StatementCountVisitor extends JavaRecursiveElementWalkingVisitor {
    private int statementCount;

    @Override
    public void visitStatement(@NotNull PsiStatement statement) {
      super.visitStatement(statement);
      if (statement instanceof PsiSwitchLabelStatementBase || statement instanceof PsiBreakStatement || statement instanceof PsiYieldStatement) {
        return;
      }
      statementCount++;
    }

    int getStatementCount() {
      return statementCount;
    }
  }
}