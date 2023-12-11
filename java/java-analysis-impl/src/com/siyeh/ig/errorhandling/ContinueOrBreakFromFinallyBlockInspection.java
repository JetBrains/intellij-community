/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.psi.PsiBreakStatement;
import com.intellij.psi.PsiContinueStatement;
import com.intellij.psi.PsiStatement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public final class ContinueOrBreakFromFinallyBlockInspection extends BaseInspection {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("continue.or.break.from.finally.block.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ContinueOrBreakFromFinallyBlockVisitor();
  }

  private static class ContinueOrBreakFromFinallyBlockVisitor extends BaseInspectionVisitor {

    @Override
    public void visitContinueStatement(@NotNull PsiContinueStatement statement) {
      super.visitContinueStatement(statement);
      final PsiStatement continuedStatement = statement.findContinuedStatement();
      if (continuedStatement == null || !ControlFlowUtils.isInFinallyBlock(statement, continuedStatement)) {
        return;
      }
      registerStatementError(statement);
    }

    @Override
    public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
      super.visitBreakStatement(statement);
      final PsiStatement exitedStatement = statement.findExitedStatement();
      if (exitedStatement == null || !ControlFlowUtils.isInFinallyBlock(statement, exitedStatement)) {
        return;
      }
      registerStatementError(statement);
    }
  }
}