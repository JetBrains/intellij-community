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
import com.siyeh.ig.psiutils.ControlFlowUtils;
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
    mover.moveTo(context.refactoredStatement);
    return !mover.isEmpty();
  }

  private static void doApply(PsiReturnStatement returnStatement) {
    ReturnContext context = createReturnContext(returnStatement);
    if (context != null) {
      ControlFlow flow = createControlFlow(context);
      if (flow != null) {
        Mover mover = new Mover(flow, context.refactoredStatement, context.returnedVariable);
        boolean removeReturn = mover.moveTo(context.refactoredStatement);
        if (!mover.isEmpty()) {
          applyChanges(mover, context, removeReturn);
        }
      }
    }
  }

  private static void applyChanges(@NotNull Mover mover, @NotNull ReturnContext context, boolean removeReturn) {
    mover.insertAfter.forEach(e -> e.getParent().addAfter(context.returnStatement, e));
    mover.insertBefore.forEach(e -> e.getParent().addBefore(context.returnStatement, e));
    mover.replaceInline.forEach(e -> {
      if (e instanceof PsiBreakStatement) e.replace(context.returnStatement);
      if (e instanceof PsiAssignmentExpression) inlineAssignment((PsiAssignmentExpression)e, context.returnStatement);
    });
    mover.removeCompletely.forEach(PsiElement::delete);

    if (removeReturn) {
      Set<PsiElement> skippedEmptyStatements = new THashSet<>();
      getPrevNonEmptyStatement(context.returnStatement, skippedEmptyStatements);
      skippedEmptyStatements.forEach(PsiElement::delete);
      context.returnStatement.delete();
    }
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
    boolean moveTo(PsiStatement targetStatement) {
      if (targetStatement instanceof PsiBlockStatement) {
        return moveToBlock((PsiBlockStatement)targetStatement);
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
        return moveToTry(((PsiTryStatement)targetStatement));
      }
      if (targetStatement instanceof PsiLabeledStatement) {
        return moveToLabeled(((PsiLabeledStatement)targetStatement));
      }
      if (targetStatement instanceof PsiExpressionStatement) {
        return inlineExpression(((PsiExpressionStatement)targetStatement));
      }
      return false;
    }

    private boolean moveToBlock(PsiBlockStatement targetStatement) {
      return moveToBlock(targetStatement.getCodeBlock());
    }

    private boolean moveToBlock(@NotNull PsiCodeBlock codeBlock) {
      PsiJavaToken rBrace = codeBlock.getRBrace();
      if (rBrace != null) {
        PsiStatement lastNonEmptyStatement = getPrevNonEmptyStatement(rBrace, removeCompletely);
        if (lastNonEmptyStatement == null || !moveTo(lastNonEmptyStatement)) {
          insertBefore.add(rBrace);
        }
        return true;
      }
      return false;
    }

    private boolean moveToIf(PsiIfStatement targetStatement) {
      PsiStatement thenBranch = targetStatement.getThenBranch();
      PsiStatement elseBranch = targetStatement.getElseBranch();

      boolean thenPart = thenBranch != null && moveTo(thenBranch);
      boolean elsePart = elseBranch != null && moveTo(elseBranch);
      return thenPart && elsePart;
    }

    private boolean moveToFor(PsiForStatement targetStatement) {
      moveToBreaks(targetStatement);
      return isAlwaysTrue(targetStatement.getCondition(), true);
    }

    private boolean moveToDoWhile(PsiDoWhileStatement targetStatement) {
      moveToBreaks(targetStatement);
      return isAlwaysTrue(targetStatement.getCondition(), false);
    }

    private boolean moveToWhile(PsiWhileStatement targetStatement) {
      moveToBreaks(targetStatement);
      return isAlwaysTrue(targetStatement.getCondition(), false);
    }
    private boolean moveToForeach(PsiForeachStatement targetStatement) {
      moveToBreaks(targetStatement);
      return false;
    }

    private boolean moveToTry(PsiTryStatement targetStatement) {
      PsiCodeBlock tryBlock = targetStatement.getTryBlock();
      if (tryBlock == null) {
        return false;
      }
      PsiCodeBlock finallyBlock = targetStatement.getFinallyBlock();
      if (finallyBlock != null && ControlFlowUtils.codeBlockMayCompleteNormally(finallyBlock) && writesVariable(finallyBlock)) {
        return false;
      }
      PsiCatchSection[] catchSections = targetStatement.getCatchSections();
      for (PsiCatchSection catchSection : catchSections) {
        PsiCodeBlock catchBlock = catchSection.getCatchBlock();
        if (catchBlock != null && ControlFlowUtils.codeBlockMayCompleteNormally(catchBlock) && writesVariable(finallyBlock)) {
          return false;
        }
      }
      return moveToBlock(tryBlock);
    }

    private boolean moveToLabeled(PsiLabeledStatement targetStatement) {
      PsiStatement statement = targetStatement.getStatement();
      if (statement == null) {
        return false;
      }
      moveToBreaks(statement);
      return moveTo(statement);
    }

    private boolean inlineExpression(PsiExpressionStatement statement) {
      PsiExpression expression = statement.getExpression();
      if (expression instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
        PsiExpression lExpression = assignmentExpression.getLExpression();
        if (lExpression instanceof PsiReferenceExpression) {
          PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lExpression;
          if (!referenceExpression.isQualified() && referenceExpression.resolve() == resultVariable) {
            if (assignmentExpression.getOperationTokenType() == JavaTokenType.EQ) {
              replaceInline.add(assignmentExpression);
              return true;
            }
          }
        }
      }
      return false;
    }

    private void moveToBreaks(Set<PsiBreakStatement> breaks) {
      for (PsiBreakStatement breakStatement : breaks) {
        PsiStatement prevNonEmptyStatement = getPrevNonEmptyStatement(breakStatement, removeCompletely);
        if (prevNonEmptyStatement == null || !moveTo(prevNonEmptyStatement)) {
          replaceInline.add(breakStatement);
        }
        else {
          removeCompletely.add(breakStatement);
        }
      }
    }

    private void moveToBreaks(PsiStatement targetStatement) {
      Set<PsiBreakStatement> breaks = getBreaks(targetStatement);
      moveToBreaks(breaks);
    }

    private boolean writesVariable(PsiElement element) {
      int startOffset = flow.getStartOffset(element);
      int endOffset = flow.getEndOffset(element);
      if (startOffset < 0 || endOffset < 0) {
        return true;
      }
      List<Instruction> instructions = flow.getInstructions();
      for (int i = startOffset; i < endOffset; i++) {
        Instruction instruction = instructions.get(i);
        if (instruction instanceof WriteVariableInstruction && ((WriteVariableInstruction)instruction).variable == resultVariable) {
          return true;
        }
      }
      return false;
    }

    private static boolean isAlwaysTrue(@Nullable PsiExpression condition, boolean nullIsTrue) {
      if(condition == null) return nullIsTrue;
      return ExpressionUtils.computeConstantExpression(condition) == Boolean.TRUE;
    }

    private Set<PsiBreakStatement> getBreaks(PsiStatement targetStatement) {
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

    @Nullable
    private static PsiStatement getNearestEnclosingStatement(PsiElement element) {
      return element instanceof PsiStatement ? (PsiStatement)element : PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    }

  }

  private static PsiStatement getPrevNonEmptyStatement(PsiElement psiElement, Set<PsiElement> skippedEmptyStatements) {
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
