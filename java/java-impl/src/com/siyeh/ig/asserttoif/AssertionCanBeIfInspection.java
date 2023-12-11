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
package com.siyeh.ig.asserttoif;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AssertionCanBeIfInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertToIfVisitor();
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new AssertToIfFix();
  }

  private static class AssertToIfVisitor extends BaseInspectionVisitor {
    @Override
    public void visitAssertStatement(@NotNull PsiAssertStatement assertStatement) {
      super.visitAssertStatement(assertStatement);
      if (assertStatement.getAssertCondition() == null) {
        return;
      }
      final PsiElement lastLeaf = PsiTreeUtil.getDeepestLast(assertStatement);
      if (lastLeaf instanceof PsiErrorElement ||
          PsiUtil.isJavaToken(lastLeaf, JavaTokenType.SEMICOLON) && PsiTreeUtil.prevLeaf(lastLeaf) instanceof PsiErrorElement) {
        return;
      }
      if (isVisibleHighlight(assertStatement)) {
        registerStatementError(assertStatement);
      }
      else {
        registerError(assertStatement);
      }
    }
  }

  private static class AssertToIfFix extends PsiUpdateModCommandQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("assert.can.be.if.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiAssertStatement assertStatement =
        element instanceof PsiKeyword ? (PsiAssertStatement)element.getParent() : (PsiAssertStatement)element;
      if (!(assertStatement.getParent() instanceof PsiCodeBlock)) {
        assertStatement = BlockUtils.expandSingleStatementToBlockStatement(assertStatement);
      }
      final PsiExpression condition = assertStatement.getAssertCondition();
      CommentTracker tracker = new CommentTracker();
      final String conditionText = BoolUtils.getNegatedExpressionText(condition, tracker);
      final PsiExpression description = assertStatement.getAssertDescription();
      final String errorText = description != null ? tracker.text(description) : "";
      String newStatement = "if(" + conditionText + ") throw new java.lang.AssertionError(" + errorText + ");";
      PsiReplacementUtil.replaceStatement(assertStatement, newStatement, tracker);
    }
  }
}