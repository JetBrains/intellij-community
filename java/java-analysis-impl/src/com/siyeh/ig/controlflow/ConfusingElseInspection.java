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

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class ConfusingElseInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @SuppressWarnings({"PublicField"})
  public boolean reportWhenNoStatementFollow = true;

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "ConfusingElseBranch";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("redundant.else.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("reportWhenNoStatementFollow", InspectionGadgetsBundle.message("confusing.else.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConfusingElseVisitor();
  }

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    return new RemoveRedundantElseFix();
  }

  private static class RemoveRedundantElseFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("redundant.else.unwrap.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement ifKeyword, @NotNull ModPsiUpdater updater) {
      final PsiIfStatement ifStatement = (PsiIfStatement)ifKeyword.getParent();
      if (ifStatement == null) {
        return;
      }
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch == null) {
        return;
      }
      PsiElement anchor = ifStatement;
      PsiElement parent = anchor.getParent();
      while (parent instanceof PsiIfStatement) {
        anchor = parent;
        parent = anchor.getParent();
      }
      if (elseBranch instanceof PsiBlockStatement elseBlock) {
        final PsiCodeBlock block = elseBlock.getCodeBlock();
        final PsiElement[] children = block.getChildren();
        if (children.length > 2) {
          parent.addRangeAfter(children[1], children[children.length - 2], anchor);
        }
      }
      else {
        parent.addAfter(elseBranch, anchor);
      }
      elseBranch.delete();
    }
  }

  private class ConfusingElseVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiStatement thenBranch = statement.getThenBranch();
      if (thenBranch == null) {
        return;
      }
      final PsiStatement elseBranch = statement.getElseBranch();
      if (elseBranch == null) {
        return;
      }
      if (ControlFlowUtils.statementMayCompleteNormally(thenBranch)) {
        return;
      }
      if (!reportWhenNoStatementFollow) {
        final PsiStatement nextStatement = getNextStatement(statement);
        if (nextStatement == null) {
          return;
        }
        if (!ControlFlowUtils.statementMayCompleteNormally(elseBranch)) {
          return;
          // protecting against an edge case where both branches return
          // and are followed by a case label
        }
      }
      final PsiElement elseToken = statement.getElseElement();
      if (elseToken == null) {
        return;
      }
      if (parentCompletesNormally(statement)) {
        return;
      }
      registerError(elseToken);
    }

    private static boolean parentCompletesNormally(PsiElement element) {
      PsiElement parent = element.getParent();
      while (parent instanceof PsiIfStatement ifStatement) {
        final PsiStatement elseBranch = ifStatement.getElseBranch();
        if (elseBranch != element) {
          return true;
        }
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        if (ControlFlowUtils.statementMayCompleteNormally(thenBranch)) {
          return true;
        }
        element = parent;
        parent = element.getParent();
      }
      return !(parent instanceof PsiCodeBlock);
    }

    @Nullable
    private static PsiStatement getNextStatement(PsiIfStatement statement) {
      while (true) {
        final PsiElement parent = statement.getParent();
        if (parent instanceof PsiIfStatement parentIfStatement) {
          final PsiStatement elseBranch = parentIfStatement.getElseBranch();
          if (elseBranch == statement) {
            statement = parentIfStatement;
            continue;
          }
        }
        return PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
      }
    }
  }
}
