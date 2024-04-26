// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteUnnecessaryStatementFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class UnnecessaryBreakInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.break.problem.descriptor");
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new DeleteUnnecessaryStatementFix("break");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryBreakVisitor();
  }

  private static class UnnecessaryBreakVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
      super.visitBreakStatement(statement);
      final PsiStatement exitedStatement = statement.findExitedStatement();
      if (exitedStatement == null) {
        return;
      }
      if (exitedStatement instanceof PsiSwitchBlock) {
        if (!SwitchUtils.isRuleFormatSwitch((PsiSwitchBlock)exitedStatement) || statement.getLabelIdentifier() != null) {
          return;
        }
      }
      else if (statement.getLabelIdentifier() == null) {
        return;
      }
      if (exitedStatement instanceof PsiBlockStatement blockStatement) {
        final PsiCodeBlock block = blockStatement.getCodeBlock();
        if (ControlFlowUtils.blockCompletesWithStatement(block, statement)) {
          registerError(statement.getFirstChild());
        }
      }
      else if (ControlFlowUtils.statementCompletesWithStatement(exitedStatement, statement)) {
        registerError(statement.getFirstChild());
      }
    }
  }
}
