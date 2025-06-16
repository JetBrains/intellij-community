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

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.openapi.util.Predicates.nonNull;

public final class ForLoopReplaceableByWhileInspection extends BaseInspection implements CleanupLocalInspectionTool {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreLoopsWithoutConditions = true;

  @Pattern(VALID_ID_PATTERN)
  @Override
  public @NotNull String getID() {
    return "ForLoopReplaceableByWhile";
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "for.loop.replaceable.by.while.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreLoopsWithoutConditions", InspectionGadgetsBundle.message(
        "for.loop.replaceable.by.while.ignore.option")));
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new ReplaceForByWhileFix();
  }

  private static class ReplaceForByWhileFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", JavaKeywords.WHILE);
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiForStatement forStatement = ObjectUtils.tryCast(element.getParent(), PsiForStatement.class);
      if (forStatement == null) return;
      CommentTracker commentTracker = new CommentTracker();
      PsiStatement initialization = forStatement.getInitialization();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
      final PsiWhileStatement whileStatement = (PsiWhileStatement)factory.createStatementFromText("while(true) {}", element);
      final PsiExpression forCondition = forStatement.getCondition();
      final PsiExpression whileCondition = whileStatement.getCondition();
      if (forCondition != null) {
        assert whileCondition != null;
        commentTracker.replace(whileCondition, forCondition);
      }
      final PsiBlockStatement blockStatement = (PsiBlockStatement)whileStatement.getBody();
      if (blockStatement == null) {
        return;
      }
      final PsiStatement forStatementBody = forStatement.getBody();
      final PsiElement loopBody;
      if (forStatementBody instanceof PsiBlockStatement) {
        final PsiBlockStatement newWhileBody = (PsiBlockStatement)blockStatement.replace(commentTracker.markUnchanged(forStatementBody));
        loopBody = newWhileBody.getCodeBlock();
      }
      else {
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        if (forStatementBody != null && !(forStatementBody instanceof PsiEmptyStatement)) {
          codeBlock.add(commentTracker.markUnchanged(forStatementBody));
        }
        loopBody = codeBlock;
      }
      final PsiStatement update = forStatement.getUpdate();
      if (update != null) {
        final PsiStatement[] updateStatements;
        if (update instanceof PsiExpressionListStatement expressionListStatement) {
          final PsiExpressionList expressionList = expressionListStatement.getExpressionList();
          final PsiExpression[] expressions = expressionList.getExpressions();
          updateStatements = new PsiStatement[expressions.length];
          for (int i = 0; i < expressions.length; i++) {
            updateStatements[i] = factory.createStatementFromText(commentTracker.text(expressions[i]) + ';', element);
          }
        }
        else {
          final PsiStatement updateStatement = factory.createStatementFromText(commentTracker.markUnchanged(update).getText() + ';', element);
          updateStatements = new PsiStatement[]{updateStatement};
        }
        final Collection<PsiContinueStatement> continueStatements = PsiTreeUtil.findChildrenOfType(loopBody, PsiContinueStatement.class);
        for (PsiContinueStatement continueStatement : continueStatements) {
          BlockUtils.addBefore(continueStatement, updateStatements);
        }
        for (PsiStatement updateStatement : updateStatements) {
          loopBody.addBefore(updateStatement, loopBody.getLastChild());
        }
      }
      if (initialization == null || initialization instanceof PsiEmptyStatement) {
        commentTracker.replaceAndRestoreComments(forStatement, whileStatement);
      }
      else {
        initialization = (PsiStatement)commentTracker.markUnchanged(initialization).copy();
        PsiStatement newStatement = (PsiStatement)commentTracker.replaceAndRestoreComments(forStatement, whileStatement);
        if (initialization instanceof PsiDeclarationStatement) {
          JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(newStatement.getProject());
          if (hasConflictingName((PsiDeclarationStatement)initialization, newStatement, manager)) {
            PsiBlockStatement emptyBlockStatement = (PsiBlockStatement)factory.createStatementFromText("{}", newStatement);
            emptyBlockStatement.getCodeBlock().add(newStatement);
            newStatement = ((PsiBlockStatement)newStatement.replace(emptyBlockStatement)).getCodeBlock().getStatements()[0];
          }
        }
        BlockUtils.addBefore(newStatement, initialization);
      }
    }

    private static boolean hasConflictingName(PsiDeclarationStatement initialization,
                                              PsiStatement newStatement,
                                              JavaCodeStyleManager manager) {
      return StreamEx.of(initialization.getDeclaredElements())
        .select(PsiNamedElement.class)
        .map(namedElement -> namedElement.getName())
        .filter(nonNull())
        .anyMatch(name -> !name.equals(manager.suggestUniqueVariableName(name, newStatement, true)));
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ForLoopReplaceableByWhileVisitor();
  }

  private class ForLoopReplaceableByWhileVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      if (PsiUtilCore.hasErrorElementChild(statement)){
        return;
      }

      ProblemHighlightType highlightType;
      if (highlightLoop(statement)) {
        highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
      }
      else if (!isOnTheFly()) {
        return;
      }
      else {
        highlightType = ProblemHighlightType.INFORMATION;
      }
      registerError(statement.getFirstChild(), highlightType);
    }

    private boolean highlightLoop(@NotNull PsiForStatement statement) {
      final PsiStatement initialization = statement.getInitialization();
      if (initialization != null && !(initialization instanceof PsiEmptyStatement)) {
        return false;
      }
      final PsiStatement update = statement.getUpdate();
      if (update != null && !(update instanceof PsiEmptyStatement)) {
        return false;
      }
      if (m_ignoreLoopsWithoutConditions) {
        final PsiExpression condition = statement.getCondition();
        return condition != null && !BoolUtils.isTrue(condition);
      }
      return true;
    }
  }
}