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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.JavaSuppressionUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public final class FallthruInSwitchStatementInspection extends BaseInspection {

  @Override
  public @NotNull String getID() {
    return "fallthrough";
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("fallthru.in.switch.statement.problem.descriptor");
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    return (Boolean)infos[0] ? new FallthruInSwitchStatementFix((String) infos[1]) : null;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FallthroughInSwitchStatementVisitor();
  }

  private static class FallthruInSwitchStatementFix extends PsiUpdateModCommandQuickFix {

    private final String myKeyword;

    private FallthruInSwitchStatementFix(String keyword) {
      myKeyword = keyword;
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("fallthru.in.switch.statement.quickfix", JavaKeywords.BREAK);
    }

    @Override
    public @NotNull String getName() {
      return InspectionGadgetsBundle.message("fallthru.in.switch.statement.quickfix", myKeyword);
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiSwitchLabelStatement labelStatement = (PsiSwitchLabelStatement)startElement;
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = psiFacade.getElementFactory();
      PsiSwitchBlock switchBlock = labelStatement.getEnclosingSwitchBlock();
      String stmt = switchBlock instanceof PsiSwitchExpression 
                    ? "yield " + PsiTypesUtil.getDefaultValueOfType(((PsiSwitchExpression)switchBlock).getType()) + ";" 
                    : "break;";
      final PsiStatement breakStatement = factory.createStatementFromText(stmt, labelStatement);
      final PsiElement parent = labelStatement.getParent();
      parent.addBefore(breakStatement, labelStatement);
    }
  }

  private static class FallthroughInSwitchStatementVisitor extends BaseInspectionVisitor {

    private static final Pattern commentPattern = Pattern.compile("(?i)falls?\\s*-?thro?u");

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement switchStatement) {
      super.visitSwitchStatement(switchStatement);
      doCheckSwitchBlock(switchStatement);
    }

    @Override
    public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
      super.visitSwitchExpression(expression);
      doCheckSwitchBlock(expression);
    }

    private void doCheckSwitchBlock(@NotNull PsiSwitchBlock switchBlock) {
      final PsiCodeBlock body = switchBlock.getBody();
      if (body == null) {
        return;
      }
      final PsiStatement[] statements = body.getStatements();
      for (int i = 1; i < statements.length; i++) {
        final PsiStatement statement = statements[i];
        if (!(statement instanceof PsiSwitchLabelStatement)) {
          continue;
        }
        //enhanced switch statements forbid fallthrough implicitly
        if (statement instanceof PsiSwitchLabeledRuleStatement) {
          return;
        }
        final PsiElement previousSibling = PsiTreeUtil.skipWhitespacesBackward(statement);
        if (previousSibling instanceof PsiComment comment) {
          final String commentText = comment.getText();
          if (commentPattern.matcher(commentText).find() && JavaSuppressionUtil.getSuppressedInspectionIdsIn(comment) == null) {
            continue;
          }
        }
        final PsiStatement previousStatement = PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class);
        if (previousStatement instanceof PsiSwitchLabelStatement) {
          // don't warn if there are no regular statements after the switch label
          continue;
        }
        if (ControlFlowUtils.statementMayCompleteNormally(previousStatement)) {
          registerError(statement, switchBlock instanceof PsiSwitchStatement || isOnTheFly(), switchBlock instanceof PsiSwitchExpression ? JavaKeywords.YIELD : JavaKeywords.BREAK);
        }
      }
    }
  }
}