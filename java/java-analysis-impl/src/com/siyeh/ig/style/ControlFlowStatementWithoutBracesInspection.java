/*
 * Copyright 2003-2025 Dave Griffith, Bas Leijdekkers
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
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ControlFlowStatementWithoutBracesInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("control.flow.statement.without.braces.problem.descriptor", infos);
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new ControlFlowStatementFix((String)infos[0]);
  }

  private static class ControlFlowStatementFix extends PsiUpdateModCommandQuickFix {
    private final String myKeywordText;

    ControlFlowStatementFix(String keywordText) {
      myKeywordText = keywordText;
    }

    @Override
    public @NotNull String getName() {
      return InspectionGadgetsBundle.message("control.flow.statement.without.braces.message", myKeywordText);
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("control.flow.statement.without.braces.add.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class, false);
      final PsiStatement statementWithoutBraces;
      if (statement instanceof PsiLoopStatement loopStatement) {
        statementWithoutBraces = loopStatement.getBody();
      }
      else if (statement instanceof PsiIfStatement ifStatement) {
        statementWithoutBraces = myKeywordText.equals(JavaKeywords.ELSE) ? ifStatement.getElseBranch() : ifStatement.getThenBranch();
      }
      else {
        return;
      }
      if (statementWithoutBraces == null) {
        return;
      }
      BlockUtils.expandSingleStatementToBlockStatement(statementWithoutBraces);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ControlFlowStatementVisitor();
  }

  private static class ControlFlowStatementVisitor extends ControlFlowStatementVisitorBase {

    @Contract("null->false")
    @Override
    protected boolean isApplicable(PsiStatement body) {
      if (body instanceof PsiIfStatement
          && isVisibleHighlight(body)
          && body.getParent() instanceof PsiIfStatement ifStatement
          && ifStatement.getElseBranch() == body) {
        return false;
      }
      return body != null && !(body instanceof PsiBlockStatement);
    }

    @Override
    protected @Nullable Pair<PsiElement, PsiElement> getOmittedBodyBounds(PsiStatement body) {
      if ((body instanceof PsiLoopStatement || body instanceof PsiIfStatement) && body.textContains('\n')) {
        return new Pair<>(body, body);
      }
      return null;
    }
  }
}