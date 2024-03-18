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
package com.siyeh.ig.style;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class UnnecessaryBlockStatementInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreSwitchBranches = false;

  @Override
  @NotNull
  public String getID() {
    return "UnnecessaryCodeBlock";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.block.statement.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreSwitchBranches", InspectionGadgetsBundle.message("ignore.branches.of.switch.statements")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryBlockStatementVisitor();
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new UnnecessaryBlockFix();
  }

  private static class UnnecessaryBlockFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.code.block.unwrap.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement leftBrace, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = leftBrace.getParent();
      if (!(parent instanceof PsiCodeBlock block)) {
        return;
      }
      final PsiElement firstBodyElement = block.getFirstBodyElement();
      final PsiElement lastBodyElement = block.getLastBodyElement();
      final PsiBlockStatement blockStatement = (PsiBlockStatement)block.getParent();
      if (firstBodyElement != null && lastBodyElement != null) {
        final PsiElement element = blockStatement.getParent();
        element.addRangeBefore(firstBodyElement, lastBodyElement, blockStatement);
      }
      blockStatement.delete();
    }
  }

  private class UnnecessaryBlockStatementVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBlockStatement(@NotNull PsiBlockStatement blockStatement) {
      super.visitBlockStatement(blockStatement);
      if (ignoreSwitchBranches) {
        final PsiElement prevStatement = PsiTreeUtil.skipWhitespacesBackward(blockStatement);
        if (prevStatement instanceof PsiSwitchLabelStatement) {
          return;
        }
      }
      final PsiElement parent = blockStatement.getParent();
      if (!(parent instanceof PsiCodeBlock parentBlock)) {
        return;
      }
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      final PsiJavaToken brace = codeBlock.getLBrace();
      if (brace == null) {
        return;
      }
      if (parentBlock.getStatementCount() > 1 &&
          BlockUtils.containsConflictingDeclarations(codeBlock, parentBlock)) {
        return;
      }
      registerError(brace);
    }
  }
}