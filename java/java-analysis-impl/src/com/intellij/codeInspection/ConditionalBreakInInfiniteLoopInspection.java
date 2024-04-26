// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.util.ObjectUtils.tryCast;

public final class ConditionalBreakInInfiniteLoopInspection extends AbstractBaseJavaLocalInspectionTool {
  public boolean noConversionToDoWhile = false;
  public boolean allowConditionFusion = false;
  public boolean suggestConversionWhenIfIsASingleStmtInLoop = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("noConversionToDoWhile", JavaBundle.message("inspection.conditional.break.in.infinite.loop.no.conversion.with.do.while")),
      checkbox("allowConditionFusion", JavaBundle.message("inspection.conditional.break.in.infinite.loop.allow.condition.fusion")),
      checkbox("suggestConversionWhenIfIsASingleStmtInLoop",
               JavaBundle.message("inspection.conditional.break.in.infinite.loop.suggest.conversion.when.if.is.single.stmt.in.loop")));
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
        Context context = Context.from(loopStatement, noConversionToDoWhile, suggestConversionWhenIfIsASingleStmtInLoop);
        if (context == null) return;
        LocalQuickFix[] fixes;
        if (context.conditionInTheBeginning) {
          fixes = new LocalQuickFix[]{new LoopTransformationFix(noConversionToDoWhile)};
        }
        else {
          var setInspectionOptionFix = new UpdateInspectionOptionFix(
            ConditionalBreakInInfiniteLoopInspection.this, "noConversionToDoWhile",
            JavaBundle.message("inspection.conditional.break.in.infinite.loop.no.conversion.with.do.while"),
            true);
          fixes = new LocalQuickFix[]{new LoopTransformationFix(noConversionToDoWhile), LocalQuickFix.from(setInspectionOptionFix)};
        }
        ProblemHighlightType highlightType;
        if (!allowConditionFusion && !context.isInfiniteLoop) {
          if (holder.isOnTheFly()) {
            highlightType = ProblemHighlightType.INFORMATION;
          } else {
            return;
          }
        } else {
          highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        }
        holder.registerProblem(keyword, JavaBundle.message("inspection.conditional.break.in.infinite.loop.description"), highlightType, fixes);
      }
    };
  }

  public static void tryTransform(@NotNull PsiWhileStatement whileStatement) {
    Context context = Context.from(whileStatement, true, true);
    if (context != null) {
      context.simplify(whileStatement);
    }
  }

  private record Context(@NotNull PsiLoopStatement loopStatement,
                         @NotNull PsiStatement loopBody,
                         @NotNull PsiExpression condition,
                         @NotNull PsiIfStatement statement,
                         boolean conditionInTheBeginning,
                         boolean conditionInThen,
                         boolean isInfiniteLoop) {
    @Nullable
    private static Context from(@NotNull PsiConditionalLoopStatement loopStatement,
                                boolean noConversionToDoWhile,
                                boolean suggestConversionWhenIfIsASingleStmtInLoop) {
      boolean isEndlessLoop = ControlFlowUtils.isEndlessLoop(loopStatement);
      if (!isEndlessLoop) {
        if (loopStatement instanceof PsiForStatement forStatement) {
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
      if (statements.length == 1 && !suggestConversionWhenIfIsASingleStmtInLoop) return null;
      if (StreamEx.ofTree((PsiElement)body, el -> StreamEx.of(el.getChildren()))
            .select(PsiBreakStatement.class)
            .filter(stmt -> ControlFlowUtils.statementBreaksLoop(stmt, loopStatement))
            .count() != 1) {
        return null;
      }
      PsiIfStatement first = tryCast(statements[0], PsiIfStatement.class);
      Ref<Boolean> isBreakInThen = new Ref<>(false);
      PsiExpression firstBreakCondition = extractBreakCondition(first, loopStatement, isBreakInThen);
      PsiExpression loopCondition = loopStatement.getCondition();
      boolean isLoopConditionAtStart = !(loopStatement instanceof PsiDoWhileStatement);
      if (first != null
          && firstBreakCondition != null
          && (isEndlessLoop ||
           (isLoopConditionAtStart &&
            (BoolUtils.getLogicalOperandCount(loopCondition) + BoolUtils.getLogicalOperandCount(firstBreakCondition)) < 4))) {
        return new Context(loopStatement, body, firstBreakCondition, first, true, isBreakInThen.get(), isEndlessLoop);
      }
      if (noConversionToDoWhile) return null;
      PsiIfStatement last = tryCast(statements[statements.length - 1], PsiIfStatement.class);
      if (last != null && last.getElseBranch() != null) return null;
      PsiExpression lastBreakCondition = extractBreakCondition(last, loopStatement, isBreakInThen);
      if (lastBreakCondition == null || !isBreakInThen.get()) return null;
      if (!isEndlessLoop &&
          (isLoopConditionAtStart ||
           (3 < BoolUtils.getLogicalOperandCount(loopCondition) + BoolUtils.getLogicalOperandCount(lastBreakCondition)))) {
        return null;
      }
      if (StreamEx.of(statements)
        .flatMap(statement -> StreamEx.ofTree((PsiElement)statement, el -> StreamEx.of(el.getChildren())))
        .anyMatch(e -> e instanceof PsiContinueStatement &&
                       ((PsiContinueStatement)e).findContinuedStatement() == loopStatement)) {
        return null;
      }
      boolean variablesInLoop =
        ContainerUtil.exists(VariableAccessUtils.collectUsedVariables(lastBreakCondition),
                             variable -> PsiTreeUtil.isAncestor(loopStatement, variable, false));
      if (variablesInLoop) return null;
      return new Context(loopStatement, body, lastBreakCondition, last, false, isBreakInThen.get(), isEndlessLoop);
    }

    @Contract("null, _, _ -> null")
    @Nullable
    private static PsiExpression extractBreakCondition(@Nullable PsiIfStatement ifStatement,
                                                       @NotNull PsiLoopStatement loopStatement,
                                                       Ref<? super @NotNull Boolean> isBreakInThen) {
      if (ifStatement == null) return null;
      if (ControlFlowUtils.statementBreaksLoop(ControlFlowUtils.stripBraces(ifStatement.getThenBranch()), loopStatement)) {
        if (hasVariableNameConflict(loopStatement, ifStatement, ifStatement.getElseBranch())) return null;
        isBreakInThen.set(true);
        return ifStatement.getCondition();
      }
      if (ifStatement.getElseBranch() != null &&
          ControlFlowUtils.statementBreaksLoop(ControlFlowUtils.stripBraces(ifStatement.getElseBranch()), loopStatement)) {
        if (hasVariableNameConflict(loopStatement, ifStatement, ifStatement.getThenBranch())) return null;
        isBreakInThen.set(false);
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

    private void simplify(@NotNull PsiConditionalLoopStatement loop) {
      CommentTracker ct = new CommentTracker();
      String loopText;
      if (ControlFlowUtils.isEndlessLoop(loop)) {
        String conditionForWhile = this.conditionInThen ? BoolUtils.getNegatedExpressionText(this.condition, ct) : ct.text(
          this.condition);
        LoopTransformationFix.pullDownStatements(this.statement,
                                                 this.conditionInThen ? this.statement.getElseBranch() : this.statement.getThenBranch());
        ct.delete(this.statement);
        loopText = this.conditionInTheBeginning
                   ? "while(" + conditionForWhile + ")" + ct.text(this.loopBody)
                   : "do" + ct.text(this.loopBody) + "while(" + conditionForWhile + ");";
      }
      else {
        String conditionForWhile = this.conditionInThen
                                   ? BoolUtils.getNegatedExpressionText(this.condition, ParenthesesUtils.AND_PRECEDENCE, ct) : ct.text(
          this.condition, ParenthesesUtils.AND_PRECEDENCE);
        ct.delete(this.statement);
        PsiExpression loopCondition = loop.getCondition();
        assert loopCondition != null;
        loopText = this.conditionInTheBeginning
                   ? "while(" + ct.text(loopCondition, ParenthesesUtils.AND_PRECEDENCE) + " && " + conditionForWhile + ")" + ct.text(
          this.loopBody)
                   : "do" + ct.text(this.loopBody) + "while(" + conditionForWhile + " && " + ct.text(loopCondition, ParenthesesUtils.AND_PRECEDENCE) + ");";
      }
      ct.replaceAndRestoreComments(this.loopStatement, loopText);
    }
  }

  private static class LoopTransformationFix extends PsiUpdateModCommandQuickFix {
    private final boolean noConversionToDoWhile;

    private LoopTransformationFix(boolean noConversionToDoWhile) {
      this.noConversionToDoWhile = noConversionToDoWhile;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.conditional.break.in.infinite.loop");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiConditionalLoopStatement loop = PsiTreeUtil.getParentOfType(element, PsiConditionalLoopStatement.class);
      if (loop == null) return;
      Context context = Context.from(loop, noConversionToDoWhile, false);
      if (context == null) return;
      context.simplify(loop);
    }

    private static void pullDownStatements(@NotNull PsiIfStatement conditionStatement, @Nullable PsiStatement branch) {
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
