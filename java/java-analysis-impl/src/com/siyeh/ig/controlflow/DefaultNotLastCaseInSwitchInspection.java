/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.MakeDefaultLastCaseFix;
import com.siyeh.ig.psiutils.SwitchUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DefaultNotLastCaseInSwitchInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("default.not.last.case.in.switch.problem.descriptor", infos[1]);
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    PsiSwitchLabelStatementBase lbl = (PsiSwitchLabelStatementBase)infos[0];
    if (lbl instanceof PsiSwitchLabelStatement) {
      PsiElement lastDefaultStmt = PsiTreeUtil.skipWhitespacesAndCommentsBackward(PsiTreeUtil.getNextSiblingOfType(lbl, PsiSwitchLabelStatementBase.class));
      if (!(lastDefaultStmt instanceof PsiBreakStatement || lastDefaultStmt instanceof PsiYieldStatement)) {
        return null;
      }

      PsiSwitchLabelStatementBase prevLbl = PsiTreeUtil.getPrevSiblingOfType(lbl, PsiSwitchLabelStatementBase.class);
      if (prevLbl != null) {
        PsiElement prevStat = PsiTreeUtil.skipWhitespacesAndCommentsBackward(lbl);
        if (!(prevStat instanceof PsiBreakStatement || prevStat instanceof PsiYieldStatement)) {
          return null;
        }
      }
    }
    return LocalQuickFix.from(new MakeDefaultLastCaseFix(lbl));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DefaultNotLastCaseInSwitchVisitor();
  }

  private static class DefaultNotLastCaseInSwitchVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      super.visitSwitchStatement(statement);
      visitSwitchBlock(statement);
    }

    @Override
    public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
      super.visitSwitchExpression(expression);
      visitSwitchBlock(expression);
    }

    private void visitSwitchBlock(@NotNull PsiSwitchBlock statement) {
      final PsiCodeBlock body = statement.getBody();
      if (body == null) {
        return;
      }
      final PsiStatement[] statements = body.getStatements();
      boolean labelSeen = false;
      for (int i = statements.length - 1; i >= 0; i--) {
        final PsiStatement child = statements[i];
        if (child instanceof PsiSwitchLabelStatementBase label) {
          PsiElement defaultElement = SwitchUtils.findDefaultElement(label);
          if (defaultElement != null) {
            if (labelSeen) {
              registerError(defaultElement.getFirstChild(), label, JavaElementKind.fromElement(statement).subject());
            }
            return;
          }
          else {
            labelSeen = true;
          }
        }
      }
    }
  }
}