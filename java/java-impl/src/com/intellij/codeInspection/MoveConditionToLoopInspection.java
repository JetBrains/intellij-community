// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;


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

import static com.intellij.util.ObjectUtils.tryCast;

public class MoveConditionToLoopInspection extends AbstractBaseJavaLocalInspectionTool {
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
        Context context = Context.from(loopStatement);
        if (context == null) return;
        PsiElement highlightElement = context.myConditionStatement.getChildren()[0];
        holder.registerProblem(highlightElement, InspectionsBundle.message("inspection.move.condition.to.loop.description"),
                               new LoopTransformationFix());
      }
    };
  }

  @Nullable
  private static PsiLoopStatement getEnclosingLoop(@NotNull PsiStatement statement) {
    PsiElement parent = statement.getParent();
    PsiElement probablyLoop;
    if (parent instanceof PsiCodeBlock) {
      PsiElement grandParent = parent.getParent();
      if (grandParent == null) return null;
      probablyLoop = grandParent.getParent();
    }
    else {
      probablyLoop = parent;
    }
    return tryCast(probablyLoop, PsiLoopStatement.class);
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
    static Context from(@NotNull PsiLoopStatement loopStatement) {
      if (!ControlFlowUtils.isEndlessLoop(loopStatement)) return null;
      PsiStatement body = loopStatement.getBody();
      if(body == null) return null;
      PsiStatement[] statements = ControlFlowUtils.unwrapBlock(body);
      if (statements.length < 2) return null;
      PsiStatement first = statements[0];
      PsiExpression firstBreakCondition = extractBreakCondition(first, loopStatement);
      if (firstBreakCondition != null) {
        return new Context(loopStatement, body, firstBreakCondition, first, true);
      }
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
      PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiIfStatement.class);
      if (ifStatement == null) return;
      PsiLoopStatement loop = getEnclosingLoop(ifStatement);
      if (loop == null) return;
      Context context = Context.from(loop);
      if (context == null) return;
      CommentTracker ct = new CommentTracker();
      PsiExpression conditionCopy = (PsiExpression)ct.markUnchanged(context.myCondition).copy();
      ct.delete(context.myConditionStatement);
      String loopText;
      if (context.myConditionInTheBeginning) {
        loopText = "while(" + BoolUtils.getNegatedExpressionText(conditionCopy) + ")" + context.myLoopBody.getText();
      }
      else {
        loopText = "do" + context.myLoopBody.getText() + "while(" + BoolUtils.getNegatedExpressionText(conditionCopy) + ");";
      }
      ct.replaceAndRestoreComments(context.myLoopStatement, loopText);
    }
  }
}
