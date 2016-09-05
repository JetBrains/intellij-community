/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.intermediaryVariable;

import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Pavel.Dolgov
 */
public class ReturnSeparatedFromComputationInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#" + ReturnSeparatedFromComputationInspection.class.getName());

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitReturnStatement(PsiReturnStatement returnStatement) {
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
    if (returnParent instanceof PsiCodeBlock) {
      final PsiCodeBlock returnScope = (PsiCodeBlock)returnParent;
      final PsiStatement[] statements = returnScope.getStatements();
      if (statements.length != 0 && statements[statements.length - 1] == returnStatement) {
        PsiStatement refactoredStatement = getPrevNonEmptyStatement(returnStatement, new THashSet<>());
        if (refactoredStatement != null) {
          final PsiExpression returnValue = returnStatement.getReturnValue();
          if (returnValue instanceof PsiReferenceExpression) {
            final PsiElement resolved = ((PsiReferenceExpression)returnValue).resolve();
            if (resolved instanceof PsiVariable) {
              final PsiVariable returnedVariable = (PsiVariable)resolved;
              final PsiCodeBlock variableScope = getVariableScopeBlock(returnedVariable);
              if (variableScope != null) {
                return new ReturnContext(returnStatement, returnScope, refactoredStatement, returnedVariable, variableScope);
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
    if (variable instanceof PsiLocalVariable) {
      final PsiElement variableScope = RefactoringUtil.getVariableScope((PsiLocalVariable)variable);
      if (variableScope instanceof PsiCodeBlock) {
        return ((PsiCodeBlock)variableScope);
      }
    }
    else if (variable instanceof PsiParameter) {
      final PsiParameter parameter = (PsiParameter)variable;
      final PsiElement parameterScope = parameter.getDeclarationScope();
      if (parameterScope instanceof PsiMethod) {
        return ((PsiMethod)parameterScope).getBody();
      }
      else if (parameterScope instanceof PsiLambdaExpression) {
        final PsiElement lambdaBody = ((PsiLambdaExpression)parameterScope).getBody();
        if (lambdaBody instanceof PsiCodeBlock) {
          return (PsiCodeBlock)lambdaBody;
        }
      }
    }
    return null;
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
    final int flowStart = flow.getStartOffset(context.returnScope);
    final int flowEnd = flow.getEndOffset(context.returnScope);
    if (flowStart < 0 || flowEnd < 0) return false;

    final int returnStartOffset = flow.getStartOffset(context.returnStatement);
    final int returnEndOffset = flow.getEndOffset(context.returnStatement);
    if (returnStartOffset < 0 || returnEndOffset < 0) return false;

    if (context.returnScope != context.variableScope &&
        ControlFlowUtil.hasObservableThrowExitPoints(flow, flowStart, flowEnd,
                                                     new PsiElement[]{context.refactoredStatement}, context.variableScope)) {
      return false;
    }

    Mover mover = new Mover(flow, context.refactoredStatement, context.returnedVariable);
    mover.moveTo(context.refactoredStatement, true);
    return !mover.isEmpty();
  }

  private static void doApply(PsiReturnStatement returnStatement) {
    ReturnContext context = createReturnContext(returnStatement);
    if (context != null) {
      ControlFlow flow = createControlFlow(context);
      if (flow != null) {
        Mover mover = new Mover(flow, context.refactoredStatement, context.returnedVariable);
        boolean removeReturn = mover.moveTo(context.refactoredStatement, true);
        if (!mover.isEmpty()) {
          applyChanges(mover, context, removeReturn);
        }
      }
    }
  }

  private static void applyChanges(@NotNull Mover mover, @NotNull ReturnContext context, boolean removeReturn) {
    PsiReturnStatement returnStatement = (PsiReturnStatement)context.returnStatement.copy();
    if (removeReturn) {
      removeReturn(context);
    }
    else {
      //inlineReturnedValue(mover, context);
    }
    mover.insertAfter.forEach(e -> e.getParent().addAfter(returnStatement, e));
    mover.insertBefore.forEach(e -> e.getParent().addBefore(returnStatement, e));
    mover.replaceInline.forEach(e -> {
      if (e instanceof PsiBreakStatement) e.replace(returnStatement);
      if (e instanceof PsiAssignmentExpression) inlineAssignment((PsiAssignmentExpression)e, returnStatement);
    });
    mover.removeCompletely.forEach(PsiElement::delete);
  }

  private static void removeReturn(@NotNull ReturnContext context) {
    Set<PsiElement> skippedEmptyStatements = new THashSet<>();
    getPrevNonEmptyStatement(context.returnStatement, skippedEmptyStatements);
    skippedEmptyStatements.forEach(PsiElement::delete);
    context.returnStatement.delete();
  }

  private static void inlineReturnedValue(Mover mover, ReturnContext context) {
    List<Instruction> instructions = mover.flow.getInstructions();
    PsiAssignmentExpression assignment = null;
    for (int i = 0; i < instructions.size(); i++) {
      Instruction instruction = instructions.get(i);
      if (instruction instanceof WriteVariableInstruction && ((WriteVariableInstruction)instruction).variable == mover.resultVariable) {
        PsiElement element = mover.flow.getElement(i);
        PsiElement parent = element.getParent();
        if (element instanceof PsiAssignmentExpression && parent instanceof PsiExpressionStatement) {
          if (!mover.replaceInline.contains(element)) {
            if (assignment != null) {
              return;
            }
            assignment = (PsiAssignmentExpression)element;
          }
        }
        else if (!(getNearestEnclosingStatement(element) instanceof PsiDeclarationStatement)) {
          return;
        }
      }
    }

    PsiExpression localInitializer = context.returnedVariable instanceof PsiLocalVariable ?
                                     context.returnedVariable.getInitializer() : null;
    if (assignment != null && localInitializer == null) {
      PsiExpression rExpression = assignment.getRExpression();
      replaceReturnedValue(rExpression, context.returnStatement, mover.flow);
    }
    else if (assignment == null && localInitializer != null) {
      replaceReturnedValue(localInitializer, context.returnStatement, mover.flow);
    }
  }

  private static void replaceReturnedValue(PsiExpression newReturnValue, PsiReturnStatement returnStatement, ControlFlow flow) {
    PsiExpression returnValue = returnStatement.getReturnValue();
    if (returnValue != null &&
        (ExpressionUtils.computeConstantExpression(newReturnValue) != null || isUnchangedReferenceToLocal(newReturnValue, flow))) {
      returnValue.replace(newReturnValue);
    }
  }

  private static boolean isUnchangedReferenceToLocal(PsiExpression expression, ControlFlow flow) {
    if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      if (!referenceExpression.isQualified()) {
        PsiElement resolved = referenceExpression.resolve();
        if (resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) {
          if (((PsiVariable)resolved).hasModifierProperty(PsiModifier.FINAL)) {
            return true;
          }
          for (Instruction instruction : flow.getInstructions()) {
            if (instruction instanceof WriteVariableInstruction && ((WriteVariableInstruction)instruction).variable == resolved) {
              return false;
            }
          }
          return true;
        }
      }
    }
    return false;
  }

  private static void inlineAssignment(PsiAssignmentExpression assignmentExpression, PsiReturnStatement returnStatement) {
    PsiElement assignmentParent = assignmentExpression.getParent();
    LOG.assertTrue(assignmentParent instanceof PsiExpressionStatement, "PsiExpressionStatement");
    PsiReturnStatement returnStatementCopy = (PsiReturnStatement)returnStatement.copy();
    PsiExpression rExpression = assignmentExpression.getRExpression();
    PsiExpression returnValue = returnStatementCopy.getReturnValue();
    if (rExpression != null && returnValue!=null) {
      returnValue.replace(rExpression);
      assignmentParent.replace(returnStatementCopy);
    }
  }

  private static class Mover {
    final ControlFlow flow;
    final PsiStatement enclosingStatement;
    final PsiVariable resultVariable;
    final Set<PsiElement> insertBefore = new THashSet<>();
    final Set<PsiElement> insertAfter = new THashSet<>();
    final Set<PsiElement> replaceInline = new THashSet<>();
    final Set<PsiElement> removeCompletely = new THashSet<>();

    private Map<PsiStatement, Set<PsiBreakStatement>> breakStatements;

    private Mover(@NotNull ControlFlow flow, @NotNull PsiStatement enclosingStatement, @NotNull PsiVariable resultVariable) {
      this.flow = flow;
      this.enclosingStatement = enclosingStatement;
      this.resultVariable = resultVariable;
    }

    boolean isEmpty() {
      return insertBefore.isEmpty() && insertAfter.isEmpty() && replaceInline.isEmpty();
    }

    /**
     * Returns true if the targetStatement will always exit via return/throw/etc after the transformation,
     * so if the next statement is a return or a break it can be removed safely.
     */
    boolean moveTo(PsiStatement targetStatement, boolean returnAtTheEnd) {
      if (targetStatement instanceof PsiBlockStatement) {
        return moveToBlock((PsiBlockStatement)targetStatement, returnAtTheEnd);
      }
      if (targetStatement instanceof PsiIfStatement) {
        return moveToIf((PsiIfStatement)targetStatement);
      }
      if (targetStatement instanceof PsiForStatement) {
        return moveToFor((PsiForStatement)targetStatement);
      }
      if (targetStatement instanceof PsiWhileStatement) {
        return moveToWhile((PsiWhileStatement)targetStatement);
      }
      if (targetStatement instanceof PsiDoWhileStatement) {
        return moveToDoWhile((PsiDoWhileStatement)targetStatement);
      }
      if (targetStatement instanceof PsiForeachStatement) {
        return moveToForeach((PsiForeachStatement)targetStatement);
      }
      if (targetStatement instanceof PsiTryStatement) {
        return moveToTry((PsiTryStatement)targetStatement, returnAtTheEnd);
      }
      if (targetStatement instanceof PsiLabeledStatement) {
        return moveToLabeled((PsiLabeledStatement)targetStatement, returnAtTheEnd);
      }
      if (targetStatement instanceof PsiExpressionStatement) {
        return inlineExpression((PsiExpressionStatement)targetStatement);
      }
      if (targetStatement instanceof PsiThrowStatement || targetStatement instanceof PsiReturnStatement) {
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

    private boolean moveToFor(@NotNull PsiForStatement targetStatement) {
      moveToBreaks(targetStatement, false);
      return isAlwaysTrue(targetStatement.getCondition(), true);
    }

    private boolean moveToDoWhile(@NotNull PsiDoWhileStatement targetStatement) {
      moveToBreaks(targetStatement, false);
      return isAlwaysTrue(targetStatement.getCondition(), false);
    }

    private boolean moveToWhile(@NotNull PsiWhileStatement targetStatement) {
      moveToBreaks(targetStatement, false);
      return isAlwaysTrue(targetStatement.getCondition(), false);
    }

    private boolean moveToForeach(@NotNull PsiForeachStatement targetStatement) {
      moveToBreaks(targetStatement, false);
      return false;
    }

    private boolean moveToTry(@NotNull PsiTryStatement targetStatement, boolean returnAtTheEnd) {
      PsiCodeBlock tryBlock = targetStatement.getTryBlock();
      if (tryBlock == null) {
        return false;
      }
      PsiCodeBlock finallyBlock = targetStatement.getFinallyBlock();
      if (finallyBlock != null && usesVariable(finallyBlock)) {
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
      moveToBreaks(statement, false);
      return moveTo(statement, returnAtTheEnd);
    }

    private boolean inlineExpression(@NotNull PsiExpressionStatement statement) {
      PsiExpression expression = statement.getExpression();
      if (expression instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
        PsiExpression lExpression = assignmentExpression.getLExpression();
        if (assignmentExpression.getOperationTokenType() == JavaTokenType.EQ && isReferenceTo(lExpression, resultVariable)) {
          replaceInline.add(assignmentExpression);
          return true;
        }
      }
      return false;
    }

    private void moveToBreaks(@NotNull PsiStatement targetStatement, boolean returnAtTheEnd) {
      Set<PsiBreakStatement> breaks = getBreaks(targetStatement);
      for (PsiBreakStatement breakStatement : breaks) {
        PsiStatement prevNonEmptyStatement = getPrevNonEmptyStatement(breakStatement, removeCompletely);
        if (prevNonEmptyStatement == null || !moveTo(prevNonEmptyStatement, returnAtTheEnd)) {
          replaceInline.add(breakStatement);
        }
        else {
          removeCompletely.add(breakStatement);
        }
      }
    }

    private boolean usesVariable(@NotNull PsiElement element) {
      int startOffset = flow.getStartOffset(element);
      int endOffset = flow.getEndOffset(element);
      if (startOffset < 0 || endOffset < 0) {
        return true;
      }
      return ControlFlowUtil.isVariableUsed(flow, startOffset, endOffset, resultVariable);
    }

    private static boolean isAlwaysTrue(@Nullable PsiExpression condition, boolean nullIsTrue) {
      if(condition == null) return nullIsTrue;
      return ExpressionUtils.computeConstantExpression(condition) == Boolean.TRUE;
    }

    private Set<PsiBreakStatement> getBreaks(@NotNull PsiStatement targetStatement) {
      if (breakStatements == null) {
        breakStatements = new THashMap<>();
        List<Instruction> instructions = flow.getInstructions();
        for (int i = 0; i < instructions.size(); i++) {
          PsiElement element = flow.getElement(i);
          PsiStatement statement = getNearestEnclosingStatement(element);
          if (statement instanceof PsiBreakStatement) {
            PsiStatement exitedStatement = ((PsiBreakStatement)statement).findExitedStatement();
            if (exitedStatement != null) {
              breakStatements.computeIfAbsent(exitedStatement, unused -> new THashSet<>()).add((PsiBreakStatement)statement);
            }
          }
        }
      }
      Set<PsiBreakStatement> breaks = breakStatements.get(targetStatement);
      return breaks != null ? breaks : Collections.emptySet();
    }

    private static boolean isReferenceTo(PsiExpression expression, PsiVariable variable) {
      if (expression instanceof PsiReferenceExpression) {
        PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
        if (!referenceExpression.isQualified() && referenceExpression.resolve() == variable) {
          return true;
        }
      }
      return false;
    }
  }

  private static PsiStatement getPrevNonEmptyStatement(@NotNull PsiElement psiElement, @NotNull Set<PsiElement> skippedEmptyStatements) {
    PsiStatement prevStatement = PsiTreeUtil.getPrevSiblingOfType(psiElement, PsiStatement.class);
    List<PsiStatement> skipped = new ArrayList<>();
    while (prevStatement instanceof PsiEmptyStatement) {
      skipped.add(prevStatement);
      prevStatement = PsiTreeUtil.getPrevSiblingOfType(prevStatement, PsiStatement.class);
    }
    if (prevStatement != null) {
      skippedEmptyStatements.addAll(skipped);
    }
    return prevStatement;
  }

  @Nullable
  private static PsiStatement getNearestEnclosingStatement(@NotNull PsiElement element) {
    return element instanceof PsiStatement ? (PsiStatement)element : PsiTreeUtil.getParentOfType(element, PsiStatement.class);
  }

  private static void registerProblem(@NotNull ProblemsHolder holder,
                                      @NotNull PsiReturnStatement returnStatement,
                                      @NotNull PsiVariable variable) {
    String name = variable.getName();
    holder.registerProblem(returnStatement, InspectionsBundle.message("inspection.return.separated.from.computation.descriptor", name),
                           new VariableFix(name, variable instanceof PsiParameter));
  }

  private static class VariableFix implements LocalQuickFix {
    private String myName;
    private boolean myIsParameter;

    public VariableFix(String name, boolean isParameter) {
      myName = name;
      myIsParameter = isParameter;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.return.separated.from.computation.quickfix", myName);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.return.separated.from.computation.family.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiReturnStatement) {
        doApply(((PsiReturnStatement)element));
      }
    }
  }

  private static class ReturnContext {
    private final PsiReturnStatement returnStatement;
    private final PsiCodeBlock returnScope;
    private final PsiStatement refactoredStatement;
    private final PsiVariable returnedVariable;
    private final PsiCodeBlock variableScope;

    private ReturnContext(@NotNull PsiReturnStatement returnStatement,
                          @NotNull PsiCodeBlock returnScope,
                          @NotNull PsiStatement refactoredStatement,
                          @NotNull PsiVariable returnedVariable,
                          @NotNull PsiCodeBlock variableScope) {

      this.returnStatement = returnStatement;
      this.returnScope = returnScope;
      this.refactoredStatement = refactoredStatement;
      this.returnedVariable = returnedVariable;
      this.variableScope = variableScope;
    }
  }
}
