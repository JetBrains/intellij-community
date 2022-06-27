// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.tryCast;

public class ConditionalBreakInInfiniteLoopInspection extends AbstractBaseJavaLocalInspectionTool {
  public boolean noConversionToDoWhile = false;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(JavaBundle.message("inspection.conditional.break.in.infinite.loop.no.conversion.with.do.while"), "noConversionToDoWhile");
    return panel;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitForStatement(@NotNull PsiForStatement statement) {
        visitLoop(statement, statement.getFirstChild());
      }

      @Override
      public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
        visitLoop(statement, statement.getFirstChild());
      }

      @Override
      public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
        visitLoop(statement, statement.getWhileKeyword());
      }

      private void visitLoop(@NotNull PsiConditionalLoopStatement loopStatement, @Nullable PsiElement keyword) {
        if (keyword == null) return;
        Context context = Context.from(loopStatement, noConversionToDoWhile);
        if (context == null) return;
        LocalQuickFix[] fixes;
        if (context.myConditionInTheBeginning) {
          fixes = new LocalQuickFix[]{new LoopTransformationFix()};
        }
        else {
          SetInspectionOptionFix setInspectionOptionFix =
            new SetInspectionOptionFix(ConditionalBreakInInfiniteLoopInspection.this,
                                       "noConversionToDoWhile",
                                       JavaBundle.message(
                                         "inspection.conditional.break.in.infinite.loop.no.conversion.with.do.while"),
                                       true);
          fixes = new LocalQuickFix[]{new LoopTransformationFix(), setInspectionOptionFix};
        }
        holder.registerProblem(keyword, JavaBundle.message("inspection.conditional.break.in.infinite.loop.description"), fixes);
      }
    };
  }

  private static class Context {
    final @NotNull PsiLoopStatement myLoopStatement;
    final @NotNull PsiStatement myLoopBody;
    final @NotNull PsiExpression myCondition;
    final @NotNull PsiIfStatement myConditionStatement;
    final boolean myConditionInTheBeginning;
    final boolean myConditionInThen;

    Context(@NotNull PsiLoopStatement loopStatement,
            @NotNull PsiStatement loopBody,
            @NotNull PsiExpression condition,
            @NotNull PsiIfStatement statement,
            boolean conditionInTheBeginning,
            boolean conditionInThen) {
      myLoopStatement = loopStatement;
      myLoopBody = loopBody;
      myCondition = condition;
      myConditionStatement = statement;
      myConditionInTheBeginning = conditionInTheBeginning;
      myConditionInThen = conditionInThen;
    }

    @Nullable
    static Context from(@NotNull PsiConditionalLoopStatement loopStatement, boolean noConversionToDoWhile) {
      boolean isEndlessLoop = ControlFlowUtils.isEndlessLoop(loopStatement);
      if (!isEndlessLoop) {
        if (loopStatement instanceof PsiForStatement) {
          PsiForStatement forStatement = (PsiForStatement)loopStatement;
          if ((forStatement.getInitialization() != null && !(forStatement.getInitialization() instanceof PsiEmptyStatement))
              || (forStatement.getUpdate() != null && !(forStatement.getUpdate() instanceof PsiEmptyStatement))) {
            return null;
          }
        }
      }
      PsiStatement body = loopStatement.getBody();
      if (body == null) return null;
      PsiStatement[] statements = ControlFlowUtils.unwrapBlock(body);
      if (statements.length < 1) return null;
      if (StreamEx.ofTree((PsiElement)body, el -> StreamEx.of(el.getChildren()))
            .select(PsiBreakStatement.class)
            .filter(stmt -> ControlFlowUtils.statementBreaksLoop(stmt, loopStatement))
            .count() != 1) {
        return null;
      }
      PsiIfStatement first = tryCast(statements[0], PsiIfStatement.class);
      boolean[] isBreakInThen = new boolean[1];
      PsiExpression firstBreakCondition = extractBreakCondition(first, loopStatement, isBreakInThen);
      PsiExpression loopCondition = loopStatement.getCondition();
      boolean isLoopConditionAtStart = !(loopStatement instanceof PsiDoWhileStatement);
      if (first != null
          && firstBreakCondition != null
          && (isEndlessLoop || (isLoopConditionAtStart && (BoolUtils.getLogicalOperandCount(loopCondition) + BoolUtils.getLogicalOperandCount(firstBreakCondition)) < 4))) {
        return new Context(loopStatement, body, firstBreakCondition, first, true, isBreakInThen[0]);
      }
      if (noConversionToDoWhile) return null;
      PsiIfStatement last = tryCast(statements[statements.length - 1], PsiIfStatement.class);
      PsiExpression lastBreakCondition = extractBreakCondition(last, loopStatement, isBreakInThen);
      if (lastBreakCondition == null || !isBreakInThen[0]) return null;
      if (!isEndlessLoop && (isLoopConditionAtStart || (3 < BoolUtils.getLogicalOperandCount(loopCondition) + BoolUtils.getLogicalOperandCount(lastBreakCondition)))) return null;
      if (StreamEx.of(statements)
        .flatMap(statement -> StreamEx.ofTree((PsiElement)statement, el -> StreamEx.of(el.getChildren())))
        .anyMatch(e -> e instanceof PsiContinueStatement &&
                       ((PsiContinueStatement)e).findContinuedStatement() == loopStatement)) {
        return null;
      }
      boolean variablesInLoop = VariableAccessUtils.collectUsedVariables(lastBreakCondition).stream()
        .anyMatch(variable -> PsiTreeUtil.isAncestor(loopStatement, variable, false));
      if (last == null || variablesInLoop) return null;
      return new Context(loopStatement, body, lastBreakCondition, last, false, isBreakInThen[0]);
    }

    @Nullable
    private static PsiExpression extractBreakCondition(@Nullable PsiIfStatement ifStatement,
                                                       @NotNull PsiLoopStatement loopStatement,
                                                       @NotNull boolean[] isBreakInThen) {
      if (ifStatement == null) return null;
      if (ControlFlowUtils.statementBreaksLoop(ControlFlowUtils.stripBraces(ifStatement.getThenBranch()), loopStatement)) {
        if (hasVariableNameConflict(loopStatement, ifStatement, ifStatement.getElseBranch())) return null;
        isBreakInThen[0] = true;
        return ifStatement.getCondition();
      }
      if (ifStatement.getElseBranch() != null && ControlFlowUtils.statementBreaksLoop(ControlFlowUtils.stripBraces(ifStatement.getElseBranch()), loopStatement)) {
        if (hasVariableNameConflict(loopStatement, ifStatement, ifStatement.getThenBranch())) return null;
        isBreakInThen[0] = false;
        return ifStatement.getCondition();
      }
      return null;
    }

    private static boolean hasVariableNameConflict(@NotNull PsiLoopStatement loopStatement,
                                                   @NotNull PsiIfStatement ifStatement,
                                                   @Nullable PsiStatement branch) {
      if (branch == null) return false;
      Set<String> variablesCreatedInBranch = VariableAccessUtils.findDeclaredVariables(branch).stream()
        .map(PsiVariable::getName)
        .collect(Collectors.toSet());
      Set<String> otherVariablesUsedInLoop = VariableAccessUtils.collectUsedVariables(loopStatement.getBody()).stream()
        .filter(variable -> !PsiTreeUtil.isAncestor(ifStatement, variable, false))
        .map(PsiVariable::getName)
        .collect(Collectors.toSet());
      return !Collections.disjoint(variablesCreatedInBranch, otherVariablesUsedInLoop);
    }
  }

  private static class LoopTransformationFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.conditional.break.in.infinite.loop");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiConditionalLoopStatement loop = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiConditionalLoopStatement.class);
      if (loop == null) return;
      Context context = Context.from(loop, false);
      if (context == null) return;
      CommentTracker ct = new CommentTracker();
      String loopText;
      if (ControlFlowUtils.isEndlessLoop(loop)) {
        String conditionForWhile = context.myConditionInThen ? BoolUtils.getNegatedExpressionText(context.myCondition, ct) : ct.text(context.myCondition);
        pullDownStatements(context.myConditionStatement, loop, context.myConditionInThen ? context.myConditionStatement.getElseBranch() : context.myConditionStatement.getThenBranch());
        ct.delete(context.myConditionStatement);
        loopText = context.myConditionInTheBeginning
                   ? "while(" + conditionForWhile + ")" + ct.text(context.myLoopBody)
                   : "do" + ct.text(context.myLoopBody) + "while(" + conditionForWhile + ");";
      } else {
        String conditionForWhile = context.myConditionInThen ? BoolUtils.getNegatedExpressionText(context.myCondition, ParenthesesUtils.AND_PRECEDENCE, ct) : ct.text(context.myCondition, ParenthesesUtils.AND_PRECEDENCE);
        ct.delete(context.myConditionStatement);
        PsiExpression loopCondition = loop.getCondition();
        loopText = context.myConditionInTheBeginning
                   ? "while(" + ct.text(loopCondition, ParenthesesUtils.AND_PRECEDENCE) + " && " + conditionForWhile + ")" + ct.text(context.myLoopBody)
                   : "do" + ct.text(context.myLoopBody) + "while(" + conditionForWhile + " && " + ct.text(loopCondition, ParenthesesUtils.AND_PRECEDENCE) + ");";
      }
      ct.replaceAndRestoreComments(context.myLoopStatement, loopText);
    }

    private void pullDownStatements(@NotNull PsiIfStatement conditionStatement, @NotNull PsiConditionalLoopStatement loop, @Nullable PsiStatement branch) {
      if (branch != null) {
        PsiElement parent = conditionStatement.getParent();
        PsiStatement[] branchStatements = ControlFlowUtils.unwrapBlock(branch);
        for (int statementIndex = branchStatements.length - 1; 0 <= statementIndex; statementIndex--) {
          PsiStatement statement = branchStatements[statementIndex];
          parent.addAfter(statement, conditionStatement);
        }
      }
    }
  }
}
