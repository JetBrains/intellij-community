// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.intermediaryVariable;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.PsiUpdateModCommandQuickFix;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.CommonJavaInlineUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ReturnSeparatedFromComputationInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(ReturnSeparatedFromComputationInspection.class);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitReturnStatement(@NotNull PsiReturnStatement returnStatement) {
        super.visitReturnStatement(returnStatement);
        final ReturnContext context = createReturnContext(returnStatement);
        if (context != null && isApplicable(context)) {
          registerProblem(holder, returnStatement, context.returnedVariable);
        }
      }
    };
  }

  private static ReturnContext createReturnContext(PsiReturnStatement returnStatement) {
    final PsiElement returnParent = returnStatement.getParent();
    if (returnParent instanceof PsiCodeBlock returnScope) {
      final PsiStatement[] statements = returnScope.getStatements();
      if (statements.length != 0 && statements[statements.length - 1] == returnStatement) {
        final PsiType returnType = PsiTypesUtil.getMethodReturnType(returnStatement);
        if (returnType != null) {
          PsiStatement refactoredStatement = getPrevNonEmptyStatement(returnStatement, null);
          if (refactoredStatement != null) {
            final PsiExpression returnValue = PsiUtil.skipParenthesizedExprDown(returnStatement.getReturnValue());
            if (returnValue instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiVariable returnedVariable) {
              final PsiCodeBlock variableScope = getVariableScopeBlock(returnedVariable);
              if (variableScope != null) {
                PsiElement variableMethod = PsiTreeUtil.getParentOfType(variableScope, PsiMethod.class, PsiLambdaExpression.class);
                PsiElement returnMethod = PsiTreeUtil.getParentOfType(returnScope, PsiMethod.class, PsiLambdaExpression.class);
                if (variableMethod == returnMethod) {
                  return new ReturnContext(returnStatement, returnScope, returnType, refactoredStatement, returnedVariable,
                                           variableScope);
                }
              }
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static PsiCodeBlock getVariableScopeBlock(@Nullable PsiVariable variable) {
    if (variable instanceof PsiLocalVariable localVar) {
      if (CommonJavaRefactoringUtil.getVariableScope(localVar) instanceof PsiCodeBlock block) {
        return block;
      }
    }
    else if (variable instanceof PsiParameter parameter) {
      final PsiElement parameterScope = parameter.getDeclarationScope();
      if (parameterScope instanceof PsiMethod method) {
        return method.getBody();
      }
      else if (parameterScope instanceof PsiLambdaExpression lambda && lambda.getBody() instanceof PsiCodeBlock block) {
        return block;
      }
    }
    return null;
  }

  /**
   * Detect the case like the following:
   * <pre>
   *         int result = size;
   *         result = 31 * result + width;
   *         result = 31 * result + height;
   *         return result;
   * </pre>
   */
  private static boolean hasChainedAssignmentsInScope(@NotNull ControlFlow flow,
                                                      @NotNull PsiVariable variable,
                                                      @NotNull PsiStatement lastStatementInScope) {
    for (PsiStatement statement = getPrevNonEmptyStatement(lastStatementInScope, null);
         statement != null;
         statement = getPrevNonEmptyStatement(statement, null)) {
      if (statement instanceof PsiExpressionStatement expressionStatement) {
        PsiExpression expression = expressionStatement.getExpression();
        if (expression instanceof PsiAssignmentExpression assignmentExpression) {
          if (isVariableUsed(flow, assignmentExpression.getLExpression(), variable) &&
              isVariableUsed(flow, assignmentExpression.getRExpression(), variable)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean isVariableUsed(@NotNull ControlFlow flow, @Nullable PsiElement element, @NotNull PsiVariable variable) {
    if (element == null) return false;
    int startOffset = flow.getStartOffset(element);
    int endOffset = flow.getEndOffset(element);
    if (startOffset < 0 || endOffset < 0) return true;
    return ControlFlowUtil.isVariableUsed(flow, startOffset, endOffset, variable);
  }

  private static boolean isApplicable(@NotNull ReturnContext context) {
    final ControlFlow flow = createControlFlow(context);
    return flow != null && isApplicable(flow, context);
  }

  @Nullable
  private static ControlFlow createControlFlow(@NotNull ReturnContext context) {
    try {
      final ControlFlowPolicy policy = new LocalsControlFlowPolicy(context.variableScope);
      return ControlFlowFactory.getInstance(context.variableScope.getProject()).getControlFlow(context.variableScope, policy);
    }
    catch (AnalysisCanceledException e) {
      return null;
    }
  }

  private static boolean isApplicable(@NotNull ControlFlow flow, @NotNull ReturnContext context) {
    if (hasChainedAssignmentsInScope(flow, context.returnedVariable, context.returnStatement)) {
      return false;
    }
    if (context.returnScope != context.variableScope) {
      if (ControlFlowUtil.isVariableReadInFinally(flow, context.returnScope, context.variableScope, context.returnedVariable)) {
        return false;
      }
      final int flowStart = flow.getStartOffset(context.returnScope);
      final int flowEnd = flow.getEndOffset(context.returnScope);
      if (flowStart < 0 || flowEnd < 0) return false;
      if (ControlFlowUtil.hasObservableThrowExitPoints(flow, flowStart, flowEnd,
                                                       new PsiElement[]{context.refactoredStatement}, context.variableScope)) {
        return false;
      }
    }

    Mover mover = new Mover(flow, context.refactoredStatement, context.returnedVariable, context.returnType, true);
    mover.moveTo(context.refactoredStatement, true);
    return !mover.isEmpty();
  }

  private static void doApply(PsiReturnStatement returnStatement, @NotNull ModPsiUpdater updater) {
    ReturnContext context = createReturnContext(returnStatement);
    if (context != null) {
      ControlFlow flow = createControlFlow(context);
      if (flow != null) {
        Mover mover = new Mover(flow, context.refactoredStatement, context.returnedVariable, context.returnType, false);
        boolean removeReturn = mover.moveTo(context.refactoredStatement, true);
        if (!mover.isEmpty()) {
          applyChanges(mover, context, removeReturn, updater);
          deleteRedundantVariable(context, updater);
        }
      }
    }
  }

  private static void deleteRedundantVariable(@NotNull ReturnContext context, @NotNull ModPsiUpdater updater) {
    PsiExpression value = PsiUtil.skipParenthesizedExprDown(context.returnedVariable.getInitializer());

    boolean isConstant = value instanceof PsiLiteralExpression || value instanceof PsiThisExpression || PsiUtil.isConstantExpression(value);
    boolean isSimple = isSimpleExpression(value, context.returnScope);
    if (value != null && !isConstant && !isSimple) {
      return;
    }

    List<PsiReferenceExpression> usages = VariableAccessUtils.getVariableReferences(context.returnedVariable, context.variableScope);
    for (PsiReference usage : usages) {
      PsiElement parent = PsiTreeUtil.skipParentsOfType(usage.getElement(),
                                                        PsiParenthesizedExpression.class, PsiTypeCastExpression.class);
      if (!(parent instanceof PsiReturnStatement)) {
        return;
      }
    }
    PsiExpression firstInlined = null;
    boolean isSingleUsage = value != null && usages.size() == 1;
    if (isSimple || isSingleUsage) {
      for (PsiJavaCodeReferenceElement usage : usages) {
        PsiExpression inlined = CommonJavaInlineUtil.getInstance().inlineVariable(context.returnedVariable, value, usage, null);
        if (firstInlined == null) firstInlined = inlined;
        updater.highlight(inlined);
      }
    }
    if (isSimple || isSingleUsage || usages.isEmpty()) {
      CommentTracker tracker = new CommentTracker();
      if (firstInlined != null) {
        tracker.delete(context.returnedVariable);
        tracker.insertCommentsBefore(firstInlined);
      }
      else {
        tracker.deleteAndRestoreComments(context.returnedVariable);
      }
    }
  }

  @Contract("null,_ -> false")
  private static boolean isSimpleExpression(@Nullable PsiExpression expression, @NotNull PsiElement scope) {
    if (expression instanceof PsiReferenceExpression) {
      PsiVariable variable = ObjectUtils.tryCast(((PsiReferenceExpression)expression).resolve(), PsiVariable.class);
      return variable != null && (variable.hasModifierProperty(PsiModifier.FINAL) ||
                                  HighlightControlFlowUtil.isEffectivelyFinal(variable, scope, null));
    }
    if (expression instanceof PsiUnaryExpression) {
      return ((PsiUnaryExpression)expression).getOperand() instanceof PsiLiteralExpression; // "-1" and "!true"
    }
    return expression instanceof PsiLiteralExpression ||
           expression instanceof PsiThisExpression ||
           expression instanceof PsiClassObjectAccessExpression;
  }

  private static void applyChanges(@NotNull Mover mover,
                                   @NotNull ReturnContext context,
                                   boolean removeReturn,
                                   @NotNull ModPsiUpdater updater) {
    for (PsiElement anchor : mover.insertBefore) {
      PsiElement added = anchor.getParent().addBefore(context.returnStatement, anchor);
      updater.highlight(added);
    }
    mover.replaceInline.forEach(e -> {
      if (e instanceof PsiBreakStatement breakStatement) {
        replaceStatementKeepComments(breakStatement, context.returnStatement, updater);
      }
      else if (e instanceof PsiAssignmentExpression assignment) {
        inlineAssignment(assignment, context.returnStatement, updater);
      }
    });
    mover.removeCompletely.forEach(e -> removeElementKeepComments(e));
    if (removeReturn) {
      removeReturn(context);
    }
  }

  private static void removeReturn(@NotNull ReturnContext context) {
    Set<PsiElement> skippedEmptyStatements = new HashSet<>();
    getPrevNonEmptyStatement(context.returnStatement, skippedEmptyStatements);
    skippedEmptyStatements.forEach(PsiElement::delete);
    removeElementKeepComments(context.returnStatement);
  }

  private static void inlineAssignment(PsiAssignmentExpression assignmentExpression,
                                       PsiReturnStatement returnStatement,
                                       @NotNull ModPsiUpdater updater) {
    PsiElement assignmentParent = assignmentExpression.getParent();
    LOG.assertTrue(assignmentParent instanceof PsiExpressionStatement, "PsiExpressionStatement");
    PsiReturnStatement returnStatementCopy = (PsiReturnStatement)returnStatement.copy();
    PsiExpression rExpression = assignmentExpression.getRExpression();
    PsiExpression returnValue = returnStatementCopy.getReturnValue();
    if (rExpression != null && returnValue != null) {
      returnValue.replace(rExpression);
      replaceStatementKeepComments((PsiExpressionStatement)assignmentParent, returnStatementCopy, updater);
    }
  }

  private static void replaceStatementKeepComments(PsiStatement replacedStatement,
                                                   PsiReturnStatement returnStatement,
                                                   @NotNull ModPsiUpdater updater) {
    updater.highlight(new CommentTracker().replaceAndRestoreComments(replacedStatement, returnStatement));
  }

  private static void removeElementKeepComments(PsiElement removedElement) {
    new CommentTracker().deleteAndRestoreComments(removedElement);
  }

  private static final class Mover {
    final ControlFlow flow;
    final PsiStatement enclosingStatement;
    final PsiVariable resultVariable;
    final PsiType returnType;
    final boolean checkingApplicability;
    final Set<PsiElement> insertBefore = new HashSet<>();
    final Set<PsiElement> replaceInline = new HashSet<>();

    final Set<PsiElement> removeCompletely = new HashSet<>();
    private Map<PsiStatement, Set<PsiBreakStatement>> breakStatements;

    private Mover(@NotNull ControlFlow flow,
                  @NotNull PsiStatement enclosingStatement,
                  @NotNull PsiVariable resultVariable,
                  PsiType returnType, boolean checkingApplicability) {
      this.flow = flow;
      this.enclosingStatement = enclosingStatement;
      this.resultVariable = resultVariable;
      this.returnType = returnType;
      this.checkingApplicability = checkingApplicability;
    }

    boolean isEmpty() {
      return insertBefore.isEmpty() && replaceInline.isEmpty();
    }

    /**
     * Returns true if the targetStatement will always exit via return/throw/etc after the transformation,
     * so if the next statement is a return or a break it can be removed safely.
     */
    boolean moveTo(PsiStatement targetStatement, boolean returnAtTheEnd) {
      if (checkingApplicability && !isEmpty()) {
        return false; // optimization
      }
      if (targetStatement instanceof PsiBlockStatement blockStatement) {
        return moveToBlock(blockStatement, returnAtTheEnd);
      }
      if (targetStatement instanceof PsiIfStatement ifStatement) {
        return moveToIf(ifStatement);
      }
      if (targetStatement instanceof PsiConditionalLoopStatement loop) {
        return moveToConditionalLoop(loop);
      }
      if (targetStatement instanceof PsiForeachStatement foreachStatement) {
        return moveToForeach(foreachStatement);
      }
      if (targetStatement instanceof PsiSwitchStatement switchStatement) {
        return moveToSwitch(switchStatement);
      }
      if (targetStatement instanceof PsiTryStatement tryStatement) {
        return moveToTry(tryStatement, returnAtTheEnd);
      }
      if (targetStatement instanceof PsiLabeledStatement labeledStatement) {
        return moveToLabeled(labeledStatement, returnAtTheEnd);
      }
      if (targetStatement instanceof PsiExpressionStatement expressionStatement) {
        return inlineExpression(expressionStatement);
      }
      if (targetStatement instanceof PsiThrowStatement ||
          targetStatement instanceof PsiReturnStatement ||
          targetStatement instanceof PsiBreakStatement ||
          targetStatement instanceof PsiContinueStatement) {
        return true;
      }
      return false;
    }

    private boolean moveToBlock(@NotNull PsiBlockStatement targetStatement, boolean returnAtTheEnd) {
      return moveToBlockBody(targetStatement.getCodeBlock(), returnAtTheEnd);
    }

    private boolean moveToBlockBody(@NotNull PsiCodeBlock codeBlock, boolean returnAtTheEnd) {
      PsiJavaToken rBrace = codeBlock.getRBrace();
      if (rBrace != null) {
        PsiStatement lastNonEmptyStatement = getPrevNonEmptyStatement(rBrace, removeCompletely);
        if (lastNonEmptyStatement == null) {
          return false;
        }
        if (moveTo(lastNonEmptyStatement, returnAtTheEnd)) {
          return true;
        }
        if (returnAtTheEnd) {
          insertBefore.add(rBrace);
          return true;
        }
      }
      return false;
    }

    private boolean moveToIf(@NotNull PsiIfStatement targetStatement) {
      PsiStatement thenBranch = targetStatement.getThenBranch();
      PsiStatement elseBranch = targetStatement.getElseBranch();

      boolean thenPart = thenBranch != null && moveTo(thenBranch, false);
      boolean elsePart = elseBranch != null && moveTo(elseBranch, false);
      return thenPart && elsePart;
    }

    private boolean moveToConditionalLoop(@NotNull PsiConditionalLoopStatement loop) {
      moveToBreaks(loop);
      return isAlwaysTrue(loop.getCondition(), loop instanceof PsiForStatement);
    }

    private boolean moveToForeach(@NotNull PsiForeachStatement targetStatement) {
      moveToBreaks(targetStatement);
      return false;
    }

    private boolean moveToSwitch(@NotNull PsiSwitchStatement targetStatement) {
      moveToBreaks(targetStatement);
      PsiCodeBlock body = targetStatement.getBody();
      return body != null && moveToBlockBody(body, false) && hasTotalSwitchLabel(body);
    }

    private boolean moveToTry(@NotNull PsiTryStatement targetStatement, boolean returnAtTheEnd) {
      PsiCodeBlock tryBlock = targetStatement.getTryBlock();
      if (tryBlock == null) {
        return false;
      }
      PsiCodeBlock finallyBlock = targetStatement.getFinallyBlock();
      if (isVariableUsed(flow, finallyBlock, resultVariable)) {
        return false;
      }
      boolean allCatchesReturn = true;
      PsiCatchSection[] catchSections = targetStatement.getCatchSections();
      for (PsiCatchSection catchSection : catchSections) {
        PsiCodeBlock catchBlock = catchSection.getCatchBlock();
        if (catchBlock == null || !moveToBlockBody(catchBlock, false)) {
          allCatchesReturn = false;
        }
      }
      return moveToBlockBody(tryBlock, returnAtTheEnd && allCatchesReturn) && allCatchesReturn;
    }

    private boolean moveToLabeled(@NotNull PsiLabeledStatement targetStatement, boolean returnAtTheEnd) {
      PsiStatement statement = targetStatement.getStatement();
      if (statement == null) {
        return false;
      }
      moveToBreaks(statement);
      return moveTo(statement, returnAtTheEnd);
    }

    private boolean inlineExpression(@NotNull PsiExpressionStatement statement) {
      PsiExpression expression = statement.getExpression();
      if (expression instanceof PsiAssignmentExpression assignmentExpression) {
        PsiExpression lExpression = assignmentExpression.getLExpression();
        if (assignmentExpression.getOperationTokenType() == JavaTokenType.EQ && isReferenceTo(lExpression, resultVariable)) {
          PsiExpression rExpression = assignmentExpression.getRExpression();
          if (rExpression != null) {
            PsiType rExpressionType = rExpression.getType();
            if (rExpressionType != null && returnType.isAssignableFrom(rExpressionType)) {
              replaceInline.add(assignmentExpression);
              return true;
            }
          }
        }
      }
      return false;
    }

    private void moveToBreaks(@NotNull PsiStatement targetStatement) {
      Set<PsiBreakStatement> breaks = getBreaks(targetStatement);
      for (PsiBreakStatement breakStatement : breaks) {
        PsiStatement prevNonEmptyStatement = getPrevNonEmptyStatement(breakStatement, removeCompletely);
        if (prevNonEmptyStatement == null || !moveTo(prevNonEmptyStatement, false)) {
          replaceInline.add(breakStatement);
        }
        else {
          removeCompletely.add(breakStatement);
        }
      }
    }

    private static boolean hasTotalSwitchLabel(@NotNull PsiCodeBlock switchBody) {
      for (PsiStatement statement : switchBody.getStatements()) {
        if (statement instanceof PsiSwitchLabelStatement && SwitchUtils.isUnconditionalLabel((PsiSwitchLabelStatement)statement)) { // TODO check because isUnconditionalLabel was chanched
          return true;
        }
      }
      return false;
    }

    private static boolean isAlwaysTrue(@Nullable PsiExpression condition, boolean nullIsTrue) {
      if(condition == null) return nullIsTrue;
      return ExpressionUtils.computeConstantExpression(condition) == Boolean.TRUE;
    }

    private Set<PsiBreakStatement> getBreaks(@NotNull PsiStatement targetStatement) {
      if (breakStatements == null) {
        breakStatements = new HashMap<>();
        List<Instruction> instructions = flow.getInstructions();
        for (int i = 0; i < instructions.size(); i++) {
          PsiElement element = flow.getElement(i);
          PsiStatement statement = getNearestEnclosingStatement(element);
          if (statement instanceof PsiBreakStatement) {
            PsiStatement exitedStatement = ((PsiBreakStatement)statement).findExitedStatement();
            if (exitedStatement != null) {
              breakStatements.computeIfAbsent(exitedStatement, unused -> new HashSet<>()).add((PsiBreakStatement)statement);
            }
          }
        }
      }
      Set<PsiBreakStatement> breaks = breakStatements.get(targetStatement);
      return breaks != null ? breaks : Collections.emptySet();
    }

    private static boolean isReferenceTo(PsiExpression expression, PsiVariable variable) {
      return expression instanceof PsiReferenceExpression referenceExpression &&
             !referenceExpression.isQualified() && referenceExpression.resolve() == variable;
    }
  }

  @Nullable
  private static PsiStatement getPrevNonEmptyStatement(@Nullable PsiElement psiElement, @Nullable Set<? super PsiElement> skippedEmptyStatements) {
    if (psiElement == null || !(psiElement.getParent() instanceof PsiCodeBlock)) {
      return null;
    }
    PsiStatement prevStatement = PsiTreeUtil.getPrevSiblingOfType(psiElement, PsiStatement.class);
    List<PsiStatement> skipped = new ArrayList<>();
    while (prevStatement instanceof PsiEmptyStatement) {
      skipped.add(prevStatement);
      prevStatement = PsiTreeUtil.getPrevSiblingOfType(prevStatement, PsiStatement.class);
    }
    if (prevStatement != null && skippedEmptyStatements != null) {
      skippedEmptyStatements.addAll(skipped);
    }
    return prevStatement;
  }

  @Nullable
  private static PsiStatement getNearestEnclosingStatement(@Nullable PsiElement element) {
    return element instanceof PsiStatement ? (PsiStatement)element : PsiTreeUtil.getParentOfType(element, PsiStatement.class);
  }

  private static void registerProblem(@NotNull ProblemsHolder holder,
                                      @NotNull PsiReturnStatement returnStatement,
                                      @NotNull PsiVariable variable) {
    String name = variable.getName();
    holder.registerProblem(returnStatement,
                           JavaBundle.message("inspection.return.separated.from.computation.descriptor", name),
                           new VariableFix(name));
  }

  private static class VariableFix extends PsiUpdateModCommandQuickFix {
    private final String myName;

    VariableFix(String name) {
      myName = name;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("inspection.return.separated.from.computation.quickfix", myName);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.return.separated.from.computation.family.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiReturnStatement returnStatement) {
        doApply(returnStatement, updater);
      }
    }
  }

  private record ReturnContext(@NotNull PsiReturnStatement returnStatement,
                              @NotNull PsiCodeBlock returnScope,
                              @NotNull PsiType returnType,
                              @NotNull PsiStatement refactoredStatement,
                              @NotNull PsiVariable returnedVariable,
                              @NotNull PsiCodeBlock variableScope) {
    }
}
