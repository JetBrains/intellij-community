/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class LoopStatementsThatDontLoopInspection extends BaseInspection {

  public boolean ignoreForeach = true;

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "LoopStatementThatDoesntLoop";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("loop.statements.that.dont.loop.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreForeach", InspectionGadgetsBundle.message("ignore.enhanced.for.loop.statements")));
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LoopStatementsThatDontLoopVisitor();
  }

  private class LoopStatementsThatDontLoopVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      checkLoop(statement);
    }

    @Override
    public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      if (ignoreForeach) return;
      checkLoop(statement);
    }

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      checkLoop(statement);
    }

    @Override
    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
      super.visitDoWhileStatement(statement);
      checkLoop(statement);
    }

    private void checkLoop(@NotNull PsiLoopStatement statement) {
      final PsiStatement body = statement.getBody();
      if (body == null) {
        return;
      }
      if (ControlFlowUtils.statementMayCompleteNormally(body)) {
        return;
      }
      if (ControlFlowUtils.statementIsContinueTarget(statement)) {
        return;
      }
      registerStatementError(statement);
    }
  }
}