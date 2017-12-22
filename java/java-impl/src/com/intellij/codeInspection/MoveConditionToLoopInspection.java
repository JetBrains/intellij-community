// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;


import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.util.ObjectUtils.tryCast;

public class MoveConditionToLoopInspection extends AbstractBaseJavaLocalInspectionTool {
  public boolean noConversionToDoWhile = false;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionsBundle.message("inspection.move.condition.to.loop.no.conversion.to.do.while"), "noConversionToDoWhile");
    return panel;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitWhileStatement(PsiWhileStatement statement) {
        visitLoop(statement);
      }

      @Override
      public void visitDoWhileStatement(PsiDoWhileStatement statement) {
        visitLoop(statement);
      }

      @Override
      public void visitForStatement(PsiForStatement statement) {
        visitLoop(statement);
      }

      private void visitLoop(@NotNull PsiLoopStatement loopStatement) {
        PsiElement keyword = getKeyword(loopStatement);
        if(keyword == null) return;
        Context context = Context.from(loopStatement, noConversionToDoWhile);
        if (context == null) return;
        LocalQuickFix[] fixes;
        if (context.myConditionInTheBeginning) {
          fixes = new LocalQuickFix[]{new LoopTransformationFix()};
        }
        else {
          SetInspectionOptionFix setInspectionOptionFix =
            new SetInspectionOptionFix(MoveConditionToLoopInspection.this,
                                       "noConversionToDoWhile",
                                       InspectionsBundle.message("inspection.move.condition.to.loop.no.conversion.to.do.while"),
                                       true);
          fixes = new LocalQuickFix[]{new LoopTransformationFix(), setInspectionOptionFix};
        }
        holder.registerProblem(keyword, InspectionsBundle.message("inspection.move.condition.to.loop.description"), fixes);
      }
    };
  }

  @Nullable
  private static PsiElement getKeyword(@NotNull PsiLoopStatement loopStatement) {
    if(loopStatement instanceof PsiWhileStatement || loopStatement instanceof PsiForStatement) {
      return loopStatement.getFirstChild();
    }
    return ((PsiDoWhileStatement)loopStatement).getWhileKeyword();
  }

  private static class Context {
    final @NotNull PsiLoopStatement myLoopStatement;
    final @NotNull PsiStatement myLoopBody;
    final @NotNull PsiExpression myCondition;
    final @NotNull PsiStatement myConditionStatement;
    final boolean myConditionInTheBeginning;

    public Context(@NotNull PsiLoopStatement loopStatement,
                   @NotNull PsiStatement loopBody,
                   @NotNull PsiExpression condition,
                   @NotNull PsiStatement statement,
                   boolean conditionInTheBeginning) {
      myLoopStatement = loopStatement;
      myLoopBody = loopBody;
      myCondition = condition;
      myConditionStatement = statement;
      myConditionInTheBeginning = conditionInTheBeginning;
    }


    @Nullable
    static Context from(@NotNull PsiLoopStatement loopStatement, boolean noConversionToDoWhile) {
      if (!ControlFlowUtils.isEndlessLoop(loopStatement)) return null;
      PsiStatement body = loopStatement.getBody();
      if (body == null) return null;
      PsiStatement[] statements = ControlFlowUtils.unwrapBlock(body);
      if (statements.length < 2) return null;
      if (StreamEx.ofTree((PsiElement)body, el -> StreamEx.of(el.getChildren()))
            .select(PsiBreakStatement.class)
            .filter(stmt -> ControlFlowUtils.statementBreaksLoop(stmt, loopStatement))
            .count() != 1) {
        return null;
      }
      PsiStatement first = statements[0];
      PsiExpression firstBreakCondition = extractBreakCondition(first, loopStatement);
      if (firstBreakCondition != null) {
        return new Context(loopStatement, body, firstBreakCondition, first, true);
      }
      if (noConversionToDoWhile) return null;
      PsiStatement last = statements[statements.length - 1];
      PsiExpression lastBreakCondition = extractBreakCondition(last, loopStatement);
      if (lastBreakCondition != null) {
        if (StreamEx.of(statements)
          .flatMap(statement -> StreamEx.ofTree((PsiElement)statement, el -> StreamEx.of(el.getChildren())))
          .anyMatch(e -> e instanceof PsiContinueStatement &&
                         ((PsiContinueStatement)e).findContinuedStatement() == loopStatement)) {
          return null;
        }
        boolean variablesInLoop = VariableAccessUtils.collectUsedVariables(lastBreakCondition).stream()
          .anyMatch(var -> PsiTreeUtil.isAncestor(loopStatement, var, false));
        if (variablesInLoop) return null;
        return new Context(loopStatement, body, lastBreakCondition, last, false);
      }
      return null;
    }

    @Nullable
    private static PsiExpression extractBreakCondition(@NotNull PsiStatement statement, @NotNull PsiLoopStatement loopStatement) {
      PsiIfStatement ifStatement = tryCast(statement, PsiIfStatement.class);
      if (ifStatement == null) return null;
      if (ifStatement.getElseBranch() != null) return null;
      PsiStatement thenBranch = ifStatement.getThenBranch();
      if (!ControlFlowUtils.statementBreaksLoop(ControlFlowUtils.stripBraces(thenBranch), loopStatement)) return null;
      return ifStatement.getCondition();
    }
  }

  private static class LoopTransformationFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.move.condition.to.loop");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiLoopStatement loop = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiLoopStatement.class);
      if (loop == null) return;
      Context context = Context.from(loop, false);
      if (context == null) return;
      CommentTracker ct = new CommentTracker();
      String negated = BoolUtils.getNegatedExpressionText(context.myCondition, ct);
      ct.delete(context.myConditionStatement);
      String loopText = context.myConditionInTheBeginning
                 ? "while(" + negated + ")" + ct.text(context.myLoopBody)
                 : "do" + ct.text(context.myLoopBody) + "while(" + negated + ");";
      ct.replaceAndRestoreComments(context.myLoopStatement, loopText);
    }
  }
}
