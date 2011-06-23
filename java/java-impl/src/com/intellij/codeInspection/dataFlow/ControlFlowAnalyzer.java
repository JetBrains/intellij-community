/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

class ControlFlowAnalyzer extends JavaElementVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer");
  private static final int NOT_FOUND = -10;
  private ControlFlow myPass1Flow;
  private ControlFlow myCurrentFlow;
  private int myPassNumber;
  private HashSet<DfaVariableValue> myFields;
  private Stack<CatchDescriptor> myCatchStack;
  private PsiType myRuntimeException;
  private final DfaValueFactory myFactory;
  private boolean myHonorRuntimeExceptions = true;

  ControlFlowAnalyzer(final DfaValueFactory valueFactory) {
    myFactory = valueFactory;
  }

  private static class CantAnalyzeException extends RuntimeException {
  }

  public void setHonorRuntimeExceptions(final boolean honorRuntimeExceptions) {
    myHonorRuntimeExceptions = honorRuntimeExceptions;
  }

  public ControlFlow buildControlFlow(PsiElement codeFragment) {
    if (codeFragment == null) return null;

    myRuntimeException = PsiType.getJavaLangRuntimeException(codeFragment.getManager(), codeFragment.getResolveScope());
    myFields = new HashSet<DfaVariableValue>();
    myCatchStack = new Stack<CatchDescriptor>();
    myPassNumber = 1;
    myPass1Flow = new ControlFlow(myFactory);
    myCurrentFlow = myPass1Flow;

    try {
      codeFragment.accept(this);
    }
    catch (CantAnalyzeException e) {
      return null;
    }

    myPassNumber = 2;
    final ControlFlow pass2Flow = new ControlFlow(myFactory);
    myCurrentFlow = pass2Flow;

    codeFragment.accept(this);

    pass2Flow.setFields(myFields.toArray(new DfaVariableValue[myFields.size()]));

    LOG.assertTrue(myPass1Flow.getInstructionCount() == pass2Flow.getInstructionCount());

    addInstruction(new ReturnInstruction());

    return pass2Flow;
  }

  private boolean myRecursionStopper = false;

  private void addInstruction(Instruction instruction) {
    ProgressManager.checkCanceled();

    if (!myRecursionStopper) {
      myRecursionStopper = true;
      try {
         //Add extra conditional goto in order to handle possible runtime exceptions that could be caught by finally block.
        if (instruction instanceof BranchingInstruction || instruction instanceof AssignInstruction ||
            instruction instanceof MethodCallInstruction) {
          addConditionalRuntimeThrow();
        }
      }
      finally {
        myRecursionStopper = false;
      }
    }

    myCurrentFlow.addInstruction(instruction);
  }

  private int getEndOffset(PsiElement element) {
    return myPassNumber == 2 ? myPass1Flow.getEndOffset(element) : 0;
  }

  private int getStartOffset(PsiElement element) {
    return myPassNumber == 2 ? myPass1Flow.getStartOffset(element) : 0;
  }

  private void startElement(PsiElement element) {
    myCurrentFlow.startElement(element);
  }

  private void finishElement(PsiElement element) {
    myCurrentFlow.finishElement(element);
  }

  @Override public void visitAssignmentExpression(PsiAssignmentExpression expression) {
    startElement(expression);

    try {
      PsiExpression lExpr = expression.getLExpression();
      PsiExpression rExpr = expression.getRExpression();

      if (rExpr == null) {
        pushUnknown();
        return;
      }

      lExpr.accept(this);

      IElementType op = expression.getOperationTokenType();
      PsiType type = expression.getType();
      boolean isBoolean = PsiType.BOOLEAN.equals(type);
      if (op == JavaTokenType.EQ) {
        rExpr.accept(this);
        generateBoxingUnboxingInstructionFor(rExpr, type);
      }
      else if (op == JavaTokenType.ANDEQ) {
        if (isBoolean) {
          generateNonMaccartyExpression(true, lExpr, rExpr, type);
        }
        else {
          generateDefaultBinop(lExpr, rExpr, type);
        }
      }
      else if (op == JavaTokenType.OREQ) {
        if (isBoolean) {
          generateNonMaccartyExpression(false, lExpr, rExpr, type);
        }
        else {
          generateDefaultBinop(lExpr, rExpr, type);
        }
      }
      else if (op == JavaTokenType.XOREQ) {
        if (isBoolean) {
          generateXorExpression(expression, lExpr, rExpr, type);
        }
        else {
          generateDefaultBinop(lExpr, rExpr, type);
        }
      }
      else {
        generateDefaultBinop(lExpr, rExpr, type);
      }

      addInstruction(new AssignInstruction(rExpr));
    }
    finally {
      finishElement(expression);
    }
  }

  private void generateDefaultBinop(PsiExpression lExpr, PsiExpression rExpr, final PsiType exprType) {
    lExpr.accept(this);
    generateBoxingUnboxingInstructionFor(lExpr,exprType);
    rExpr.accept(this);
    generateBoxingUnboxingInstructionFor(rExpr,exprType);
    addInstruction(new BinopInstruction(null, null, lExpr.getProject()));
  }

  @Override public void visitAssertStatement(PsiAssertStatement statement) {
    startElement(statement);
    final PsiExpression condition = statement.getAssertCondition();
    final PsiExpression description = statement.getAssertDescription();
    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
      addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), false, condition));
      if (description != null) {
        description.accept(this);
      }
      addInstruction(new ReturnInstruction());
    }
    finishElement(statement);
  }

  @Override public void visitDeclarationStatement(PsiDeclarationStatement statement) {
    startElement(statement);

    PsiElement[] elements = statement.getDeclaredElements();
    for (PsiElement element : elements) {
      if (element instanceof PsiClass) {
        element.accept(this);
      }
      else if (element instanceof PsiVariable) {
        PsiVariable variable = (PsiVariable)element;
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null) {
          initializeVariable(variable, initializer);
        }
      }
    }

    finishElement(statement);
  }

  private void initializeVariable(PsiVariable variable, PsiExpression initializer) {
    DfaVariableValue dfaVariable = myFactory.getVarFactory().create(variable, false);
    addInstruction(new PushInstruction(dfaVariable, initializer));
    initializer.accept(this);
    generateBoxingUnboxingInstructionFor(initializer, variable.getType());
    addInstruction(new AssignInstruction(initializer));
    addInstruction(new PopInstruction());
  }

  @Override
  public void visitCodeFragment(JavaCodeFragment codeFragment) {
    startElement(codeFragment);
    if (codeFragment instanceof PsiExpressionCodeFragment) {
      PsiExpression expression = ((PsiExpressionCodeFragment)codeFragment).getExpression();
      if (expression != null) {
        expression.accept(this);
      }
    }
    finishElement(codeFragment);
  }

  @Override public void visitCodeBlock(PsiCodeBlock block) {
    startElement(block);

    PsiStatement[] statements = block.getStatements();
    for (PsiStatement statement : statements) {
      statement.accept(this);
    }

    for (PsiStatement statement : statements) {
      if (statement instanceof PsiDeclarationStatement) {
        PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
        PsiElement[] declarations = declarationStatement.getDeclaredElements();
        for (PsiElement declaration : declarations) {
          if (declaration instanceof PsiVariable) {
            myCurrentFlow.removeVariable((PsiVariable)declaration);
          }
        }
      }
    }

    finishElement(block);
  }

  @Override public void visitBlockStatement(PsiBlockStatement statement) {
    startElement(statement);
    statement.getCodeBlock().accept(this);
    finishElement(statement);
  }

  @Override public void visitBreakStatement(PsiBreakStatement statement) {
    startElement(statement);

    PsiStatement exitedStatement = statement.findExitedStatement();

    if (exitedStatement != null) {
      int offset = myPass1Flow.getEndOffset(exitedStatement);
      if (offset == -1) offset = myPass1Flow.getInstructionCount();
      addInstruction(new GotoInstruction(offset));
    }

    finishElement(statement);
  }

  @Override public void visitContinueStatement(PsiContinueStatement statement) {
    startElement(statement);
    PsiStatement continuedStatement = statement.findContinuedStatement();
    if (continuedStatement != null) {
      int offset = -1;
      if (continuedStatement instanceof PsiForStatement) {
        PsiStatement body = ((PsiForStatement)continuedStatement).getBody();
        offset = myPass1Flow.getEndOffset(body);
      }
      else if (continuedStatement instanceof PsiWhileStatement) {
        PsiStatement body = ((PsiWhileStatement)continuedStatement).getBody();
        offset = myPass1Flow.getEndOffset(body);
      }
      else if (continuedStatement instanceof PsiDoWhileStatement) {
        PsiStatement body = ((PsiDoWhileStatement)continuedStatement).getBody();
        offset = myPass1Flow.getEndOffset(body);
      }
      else if (continuedStatement instanceof PsiForeachStatement) {
        PsiStatement body = ((PsiForeachStatement)continuedStatement).getBody();
        offset = myPass1Flow.getEndOffset(body);
      }
      Instruction instruction = offset == -1 ? new EmptyInstruction() : new GotoInstruction(offset);
      addInstruction(instruction);
    }
    finishElement(statement);
  }

  @Override public void visitDoWhileStatement(PsiDoWhileStatement statement) {
    startElement(statement);

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
      PsiExpression condition = statement.getCondition();
      if (condition != null) {
        condition.accept(this);
        generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
        addInstruction(new ConditionalGotoInstruction(getStartOffset(statement), false, condition));
      }
    }

    finishElement(statement);
  }

  @Override public void visitEmptyStatement(PsiEmptyStatement statement) {
    startElement(statement);
    finishElement(statement);
  }

  @Override public void visitExpressionStatement(PsiExpressionStatement statement) {
    startElement(statement);
    final PsiExpression expr = statement.getExpression();
    expr.accept(this);
    addInstruction(new PopInstruction());
    finishElement(statement);
  }

  @Override public void visitExpressionListStatement(PsiExpressionListStatement statement) {
    startElement(statement);
    PsiExpression[] expressions = statement.getExpressionList().getExpressions();
    for (PsiExpression expr : expressions) {
      expr.accept(this);
      addInstruction(new PopInstruction());
    }
    finishElement(statement);
  }

  @Override public void visitForeachStatement(PsiForeachStatement statement) {
    startElement(statement);
    final PsiParameter parameter = statement.getIterationParameter();
    final PsiExpression iteratedValue = statement.getIteratedValue();

    if (iteratedValue != null) {
      iteratedValue.accept(this);
      addInstruction(new FieldReferenceInstruction(iteratedValue, "Collection iterator or array.length"));
    }

    int offset = myCurrentFlow.getInstructionCount();
    DfaVariableValue dfaVariable = myFactory.getVarFactory().create(parameter, false);
    addInstruction(new PushInstruction(dfaVariable, null));
    pushUnknown();
    addInstruction(new AssignInstruction(null));

    addInstruction(new PopInstruction());

    pushUnknown();
    addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), true, null));

    final PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    addInstruction(new GotoInstruction(offset));

    finishElement(statement);
    myCurrentFlow.removeVariable(parameter);
  }

  @Override public void visitForStatement(PsiForStatement statement) {
    startElement(statement);
    final ArrayList<PsiElement> declaredVariables = new ArrayList<PsiElement>();

    PsiStatement initialization = statement.getInitialization();
    if (initialization != null) {
      initialization.accept(this);
      initialization.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
          visitElement(expression);
        }

        @Override public void visitDeclarationStatement(PsiDeclarationStatement statement) {
          PsiElement[] declaredElements = statement.getDeclaredElements();
          for (PsiElement element : declaredElements) {
            if (element instanceof PsiVariable) {
              declaredVariables.add(element);
            }
          }
        }
      });
    }

    PsiExpression condition = statement.getCondition();
    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
    }
    else {
      addInstruction(new PushInstruction(myFactory.getConstFactory().getTrue(), null));
    }
    addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), true, condition));

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    PsiStatement update = statement.getUpdate();
    if (update != null) {
      update.accept(this);
    }

    int offset = initialization != null
                 ? getEndOffset(initialization)
                 : getStartOffset(statement);

    addInstruction(new GotoInstruction(offset));
    finishElement(statement);

    for (PsiElement declaredVariable : declaredVariables) {
      PsiVariable psiVariable = (PsiVariable)declaredVariable;
      myCurrentFlow.removeVariable(psiVariable);
    }
  }

  @Override public void visitIfStatement(PsiIfStatement statement) {
    startElement(statement);

    PsiExpression condition = statement.getCondition();

    PsiStatement thenStatement = statement.getThenBranch();
    PsiStatement elseStatement = statement.getElseBranch();

    int offset = elseStatement != null
                 ? getStartOffset(elseStatement)
                 : getEndOffset(statement);

    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
      addInstruction(new ConditionalGotoInstruction(offset, true, condition));
    }

    if (thenStatement != null) {
      thenStatement.accept(this);
    }

    if (elseStatement != null) {
      offset = getEndOffset(statement);
      Instruction instruction = new GotoInstruction(offset);
      addInstruction(instruction);
      elseStatement.accept(this);
    }

    finishElement(statement);
  }

  // in case of JspTemplateStatement
  @Override public void visitStatement(PsiStatement statement) {
    startElement(statement);
    finishElement(statement);
  }

  @Override public void visitLabeledStatement(PsiLabeledStatement statement) {
    startElement(statement);
    PsiStatement childStatement = statement.getStatement();
    if (childStatement != null) {
      childStatement.accept(this);
    }
    finishElement(statement);
  }

  @Override public void visitReturnStatement(PsiReturnStatement statement) {
    startElement(statement);

    PsiExpression returnValue = statement.getReturnValue();
    if (returnValue != null) {
      returnValue.accept(this);
      PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, true, PsiMember.class);
      if (method != null) {
        generateBoxingUnboxingInstructionFor(returnValue, method.getReturnType());
      }
      addInstruction(new CheckReturnValueInstruction(statement));
    }

    int finallyOffset = getFinallyOffset();
    if (finallyOffset != NOT_FOUND) {
      addInstruction(new GosubInstruction(finallyOffset));
    }
    addInstruction(new ReturnInstruction());
    finishElement(statement);
  }

  @Override public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    startElement(statement);
    finishElement(statement);
  }

  @Override public void visitSwitchStatement(PsiSwitchStatement switchStmt) {
    startElement(switchStmt);
    PsiElementFactory psiFactory = JavaPsiFacade.getInstance(switchStmt.getProject()).getElementFactory();
    PsiExpression caseExpression = switchStmt.getExpression();
    Set<PsiEnumConstant> enumVals = null;
    if (caseExpression != null /*&& !(caseExpression instanceof PsiReferenceExpression)*/) {
      caseExpression.accept(this);

      generateBoxingUnboxingInstructionFor(caseExpression, PsiType.INT);
      final PsiClass psiClass = PsiUtil.resolveClassInType(caseExpression.getType());
      if (psiClass != null && psiClass.isEnum()) {
        addInstruction(new FieldReferenceInstruction(caseExpression, "switch statement expression"));
        enumVals = new HashSet<PsiEnumConstant>();
        for (PsiField f : psiClass.getFields()) {
          if (f instanceof PsiEnumConstant) {
            enumVals.add((PsiEnumConstant)f);
          }
        }
      } else {
        addInstruction(new PopInstruction());
      }

    }

    PsiCodeBlock body = switchStmt.getBody();

    if (body != null) {
      PsiStatement[] statements = body.getStatements();
      PsiSwitchLabelStatement defaultLabel = null;
      for (PsiStatement statement : statements) {
        if (statement instanceof PsiSwitchLabelStatement) {
          PsiSwitchLabelStatement psiLabelStatement = (PsiSwitchLabelStatement)statement;
          if (psiLabelStatement.isDefaultCase()) {
            defaultLabel = psiLabelStatement;
          }
          else {
            try {
              int offset = getStartOffset(statement);
              PsiExpression caseValue = psiLabelStatement.getCaseValue();

              if (caseValue != null &&
                  caseExpression instanceof PsiReferenceExpression &&
                  ((PsiReferenceExpression)caseExpression).getQualifierExpression() == null &&
                  JavaPsiFacade.getInstance(body.getProject()).getConstantEvaluationHelper().computeConstantExpression(caseValue) != null) {
                PsiExpression psiComparison = psiFactory.createExpressionFromText(
                  caseExpression.getText() + "==" + caseValue.getText(), switchStmt);
                psiComparison.accept(this);
              }
              else {
                pushUnknown();
              }

              addInstruction(new ConditionalGotoInstruction(offset, false, statement));

              if (enumVals != null) {
                if (caseValue instanceof PsiReferenceExpression) {
                  enumVals.remove(((PsiReferenceExpression)caseValue).resolve());
                }
              }
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }

      if (enumVals == null || !enumVals.isEmpty()) {
        int offset = defaultLabel != null ? getStartOffset(defaultLabel) : getEndOffset(body);
        addInstruction(new GotoInstruction(offset));
      }

      body.accept(this);
    }

    finishElement(switchStmt);
  }

  @Override public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
    startElement(statement);

    PsiExpression lock = statement.getLockExpression();
    if (lock != null) {
      lock.accept(this);
      addInstruction(new PopInstruction());
    }

    addInstruction(new FlushVariableInstruction(null));

    PsiCodeBlock body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    finishElement(statement);
  }

  @Override public void visitThrowStatement(PsiThrowStatement statement) {
    startElement(statement);

    PsiExpression exception = statement.getException();

    if (exception != null) {
      exception.accept(this);
      addThrowCode(exception.getType());
    }

    finishElement(statement);
  }

  private void addConditionalRuntimeThrow() {
    for (int i = myCatchStack.size() - 1; i >= 0; i--) {
      CatchDescriptor cd = myCatchStack.get(i);
      if (cd.isFinally()) {
        pushUnknown();
        final ConditionalGotoInstruction branch = new ConditionalGotoInstruction(-1, false, null);
        addInstruction(branch);
        addInstruction(new GosubInstruction(cd.getJumpOffset()));
        addInstruction(new ReturnInstruction());
        branch.setOffset(myCurrentFlow.getInstructionCount());
      }
      else if (cd.getType() instanceof PsiClassType &&
               ExceptionUtil.isUncheckedExceptionOrSuperclass((PsiClassType)cd.getType())) {
        pushUnknown();
        final ConditionalGotoInstruction branch = new ConditionalGotoInstruction(-1, false, null);
        addInstruction(branch);
        addInstruction(new PushInstruction(myFactory.getNotNullFactory().create(myRuntimeException), null));
        addGotoCatch(cd);
        branch.setOffset(myCurrentFlow.getInstructionCount());
        return;
      }
    }
  }

  private void addThrowCode(PsiType exceptionClass) {
    if (exceptionClass == null) return;
    for (int i = myCatchStack.size() - 1; i >= 0; i--) {
      CatchDescriptor cd = myCatchStack.get(i);
      if (cd.isFinally()) {
        addInstruction(new GosubInstruction(cd.getJumpOffset()));
      }
      else if (cd.getType().isAssignableFrom(exceptionClass)) { // Definite catch.
        addGotoCatch(cd);
        return;
      }
      else if (cd.getType().isConvertibleFrom(exceptionClass)) { // Probable catch
        addInstruction(new DupInstruction());
        pushUnknown();
        final ConditionalGotoInstruction branch = new ConditionalGotoInstruction(-1, false, null);
        addInstruction(branch);
        addGotoCatch(cd);
        branch.setOffset(myCurrentFlow.getInstructionCount());
      }
    }

    addInstruction(new ReturnInstruction());
  }

  /**
   * Exception is expected on the stack.
   * 
   * @param cd
   */
  private void addGotoCatch(CatchDescriptor cd) {
    addInstruction(new PushInstruction(myFactory.getVarFactory().create(cd.getParameter(), false), null));
    addInstruction(new SwapInstruction());
    addInstruction(new AssignInstruction(null));
    addInstruction(new PopInstruction());
    addInstruction(new GotoInstruction(cd.getJumpOffset()));
  }

  private int getFinallyOffset() {
    for (int i = myCatchStack.size() - 1; i >= 0; i--) {
      CatchDescriptor cd = myCatchStack.get(i);
      if (cd.isFinally()) return cd.getJumpOffset();
    }

    return NOT_FOUND;
  }

  class CatchDescriptor {
    private final PsiType myType;
    private PsiParameter myParameter;
    private final PsiCodeBlock myBlock;
    private final boolean myIsFinally;

    CatchDescriptor(PsiCodeBlock finallyBlock) {
      myType = null;
      myBlock = finallyBlock;
      myIsFinally = true;
    }

    CatchDescriptor(PsiParameter parameter, PsiCodeBlock catchBlock) {
      myType = parameter.getType();
      myParameter = parameter;
      myBlock = catchBlock;
      myIsFinally = false;
    }

    public PsiType getType() {
      return myType;
    }

    public boolean isFinally() {
      return myIsFinally;
    }

    public int getJumpOffset() {
      return getStartOffset(myBlock);
    }

    public PsiParameter getParameter() { return myParameter; }
  }

  @Override public void visitErrorElement(PsiErrorElement element) {
    throw new CantAnalyzeException();
  }

  @Override public void visitTryStatement(PsiTryStatement statement) {
    startElement(statement);
    PsiCodeBlock finallyBlock = statement.getFinallyBlock();

    if (finallyBlock != null) {
      myCatchStack.push(new CatchDescriptor(finallyBlock));
    }

    int catchesPushCount = 0;
    PsiCatchSection[] sections = statement.getCatchSections();
    for (int i = sections.length - 1; i >= 0; i--) {
      PsiCatchSection section = sections[i];
      PsiCodeBlock catchBlock = section.getCatchBlock();
      PsiParameter parameter = section.getParameter();
      if (parameter != null && catchBlock != null && parameter.getType() instanceof PsiClassType &&
          (!myHonorRuntimeExceptions || !ExceptionUtil.isUncheckedException((PsiClassType)parameter.getType()))) {
        myCatchStack.push(new CatchDescriptor(parameter, catchBlock));
        catchesPushCount++;
      }
      else {
        throw new CantAnalyzeException();
      }
    }

    int endOffset = finallyBlock == null ? getEndOffset(statement) : getStartOffset(finallyBlock) - 2;

    PsiCodeBlock tryBlock = statement.getTryBlock();

    if (tryBlock != null) {
      tryBlock.accept(this);
    }

    for (int i = 0; i < catchesPushCount; i++) myCatchStack.pop();

    addInstruction(new GotoInstruction(endOffset));

    for (PsiCatchSection section : sections) {
      section.accept(this);
      addInstruction(new GotoInstruction(endOffset));
    }

    if (finallyBlock != null) {
      myCatchStack.pop();
      addInstruction(new GosubInstruction(getStartOffset(finallyBlock)));
      addInstruction(new GotoInstruction(getEndOffset(statement)));
      finallyBlock.accept(this);
      addInstruction(new ReturnFromSubInstruction());
    }

    finishElement(statement);
  }

  @Override public void visitCatchSection(PsiCatchSection section) {
    PsiCodeBlock catchBlock = section.getCatchBlock();
    if (catchBlock != null) catchBlock.accept(this);
  }

  @Override public void visitWhileStatement(PsiWhileStatement statement) {
    startElement(statement);

    PsiExpression condition = statement.getCondition();

    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
      addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), true, condition));
    }

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    if (condition != null) {
      addInstruction(new GotoInstruction(getStartOffset(statement)));
    }

    finishElement(statement);
  }

  @Override public void visitExpressionList(PsiExpressionList list) {
    startElement(list);

    PsiExpression[] expressions = list.getExpressions();
    for (PsiExpression expression : expressions) {
      expression.accept(this);
    }

    finishElement(list);
  }

  @Override public void visitExpression(PsiExpression expression) {
    startElement(expression);
    DfaValue dfaValue = myFactory.create(expression);
    addInstruction(new PushInstruction(dfaValue, expression));
    finishElement(expression);
  }

  @Override public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
    //TODO:::
    startElement(expression);
    PsiExpression arrayExpression = expression.getArrayExpression();
    arrayExpression.accept(this);
    addInstruction(new FieldReferenceInstruction(expression, null));

    PsiExpression indexExpression = expression.getIndexExpression();
    if (indexExpression != null) {
      indexExpression.accept(this);
      generateBoxingUnboxingInstructionFor(indexExpression, PsiType.INT);
      addInstruction(new PopInstruction());
    }

    pushTypeOrUnknown(arrayExpression);
    finishElement(expression);
  }

  @Override public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
    //TODO:::
    startElement(expression);
    PsiType type = expression.getType();
    PsiExpression[] initializers = expression.getInitializers();
    for (PsiExpression initializer : initializers) {
      initializer.accept(this);
      if (type instanceof PsiArrayType) {
        generateBoxingUnboxingInstructionFor(initializer, ((PsiArrayType)type).getComponentType());
      }
      addInstruction(new PopInstruction());
    }
    pushUnknown();
    finishElement(expression);
  }

  @Override public void visitBinaryExpression(PsiBinaryExpression expression) {
    startElement(expression);

    try {
      DfaValue dfaValue = myFactory.create(expression);
      if (dfaValue != null) {
        addInstruction(new PushInstruction(dfaValue, expression));
      }
      else {
        IElementType op = expression.getOperationTokenType();
        PsiExpression lExpr = expression.getLOperand();
        PsiExpression rExpr = expression.getROperand();

        if (rExpr == null) {
          pushUnknown();
          return;
        }
        PsiType type = expression.getType();
        if (op == JavaTokenType.ANDAND) {
          generateAndExpression(lExpr, rExpr, type);
        }
        else if (op == JavaTokenType.OROR) {
          generateOrExpression(lExpr, rExpr, type);
        }
        else if (op == JavaTokenType.XOR && PsiType.BOOLEAN.equals(type)) {
          generateXorExpression(expression, lExpr, rExpr, type);
        }
        else {
          lExpr.accept(this);
          boolean comparing = op == JavaTokenType.EQEQ || op == JavaTokenType.NE;
          PsiType lType = lExpr.getType();
          PsiType rType = rExpr.getType();

          boolean comparingRef = comparing
                                 && !TypeConversionUtil.isPrimitiveAndNotNull(lType)
                                 && !TypeConversionUtil.isPrimitiveAndNotNull(rType);

          boolean comparingPrimitiveNumerics = comparing &&
                                               TypeConversionUtil.isPrimitiveAndNotNull(lType) &&
                                               TypeConversionUtil.isPrimitiveAndNotNull(rType) &&
                                               TypeConversionUtil.isNumericType(lType) &&
                                               TypeConversionUtil.isNumericType(rType);

          PsiType castType = comparingPrimitiveNumerics ? PsiType.LONG : type;

          if (!comparingRef) {
            generateBoxingUnboxingInstructionFor(lExpr,castType);
          }
          rExpr.accept(this);
          if (!comparingRef) {
            generateBoxingUnboxingInstructionFor(rExpr,castType);
          }

          String opSign = expression.getOperationSign().getText();
          if ("+".equals(opSign)) {
            if (type == null || !type.equalsToText("java.lang.String")) {
              opSign = null;
            }
          }

          PsiElement psiAnchor = expression.isPhysical() ? expression : null;
          addInstruction(new BinopInstruction(opSign, psiAnchor, expression.getProject()));
        }
      }
    }
    finally {
      finishElement(expression);
    }
  }

  private void generateBoxingUnboxingInstructionFor(PsiExpression expression, PsiType expectedType) {
    PsiType exprType = expression.getType();

    if (TypeConversionUtil.isPrimitiveAndNotNull(expectedType) && TypeConversionUtil.isPrimitiveWrapper(exprType)) {
      addInstruction(new MethodCallInstruction(expression, MethodCallInstruction.MethodType.UNBOXING));
    }
    else if (TypeConversionUtil.isPrimitiveWrapper(expectedType) && TypeConversionUtil.isPrimitiveAndNotNull(exprType)) {
      addInstruction(new MethodCallInstruction(expression, MethodCallInstruction.MethodType.BOXING));
    }
    else if (exprType != expectedType &&
             TypeConversionUtil.isPrimitiveAndNotNull(exprType) &&
             TypeConversionUtil.isPrimitiveAndNotNull(expectedType) &&
             TypeConversionUtil.isNumericType(exprType) &&
             TypeConversionUtil.isNumericType(expectedType)) {
      addInstruction(new MethodCallInstruction(expression, MethodCallInstruction.MethodType.CAST, expectedType) {
        @Override
        public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
          return visitor.visitCast(this, runner, stateBefore);
        }
      });
    }
  }

  private void generateXorExpression(PsiExpression expression, PsiExpression lExpr, PsiExpression rExpr, final PsiType exprType) {
    lExpr.accept(this);
    generateBoxingUnboxingInstructionFor(lExpr,exprType);
    rExpr.accept(this);
    generateBoxingUnboxingInstructionFor(rExpr,exprType);
    PsiElement psiAnchor = expression.isPhysical() ? expression : null;
    addInstruction(new BinopInstruction("!=", psiAnchor, expression.getProject()));
  }

  private void generateOrExpression(PsiExpression lExpr, PsiExpression rExpr, final PsiType exprType) {
    lExpr.accept(this);
    generateBoxingUnboxingInstructionFor(lExpr,exprType);
    addInstruction(new ConditionalGotoInstruction(getStartOffset(rExpr), true, lExpr));
    addInstruction(new PushInstruction(myFactory.getConstFactory().getTrue(), null));
    addInstruction(new GotoInstruction(getEndOffset(rExpr)));
    rExpr.accept(this);
    generateBoxingUnboxingInstructionFor(rExpr,exprType);
  }

  private void generateNonMaccartyExpression(boolean and, PsiExpression lExpression, PsiExpression rExpression, final PsiType exprType) {
    rExpression.accept(this);
    generateBoxingUnboxingInstructionFor(rExpression, exprType);

    lExpression.accept(this);
    generateBoxingUnboxingInstructionFor(lExpression, exprType);

    ConditionalGotoInstruction toPopAndPushSuccess = new ConditionalGotoInstruction(-1, and, lExpression);
    addInstruction(toPopAndPushSuccess);
    final GotoInstruction overPushSuccess = new GotoInstruction(-1);
    addInstruction(overPushSuccess);

    final PopInstruction pop = new PopInstruction();
    addInstruction(pop);
    final PushInstruction pushSuccess = new PushInstruction(and
                                                            ? myFactory.getConstFactory().getFalse()
                                                            : myFactory.getConstFactory().getTrue(), null);
    addInstruction(pushSuccess);

    toPopAndPushSuccess.setOffset(pop.getIndex());
    overPushSuccess.setOffset(pushSuccess.getIndex() + 1);
  }

  private void generateAndExpression(PsiExpression lExpr, PsiExpression rExpr, final PsiType exprType) {
    lExpr.accept(this);
    generateBoxingUnboxingInstructionFor(lExpr, exprType);
    ConditionalGotoInstruction firstTrueGoto = new ConditionalGotoInstruction(-1, true, lExpr);
    addInstruction(firstTrueGoto);
    rExpr.accept(this);
    generateBoxingUnboxingInstructionFor(rExpr, exprType);

    GotoInstruction overPushFalse = new GotoInstruction(-1);
    addInstruction(overPushFalse);
    PushInstruction pushFalse = new PushInstruction(myFactory.getConstFactory().getFalse(), null);
    addInstruction(pushFalse);

    firstTrueGoto.setOffset(pushFalse.getIndex());
    overPushFalse.setOffset(pushFalse.getIndex() + 1);
  }

  @Override public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    startElement(expression);
    PsiElement[] children = expression.getChildren();
    for (PsiElement child : children) {
      child.accept(this);
    }
    pushUnknown();
    finishElement(expression);
  }

  @Override public void visitConditionalExpression(PsiConditionalExpression expression) {
    startElement(expression);

    DfaValue dfaValue = myFactory.create(expression);
    if (dfaValue != null) {
      addInstruction(new PushInstruction(dfaValue, expression));
    }
    else {
      PsiExpression condition = expression.getCondition();

      PsiExpression thenExpression = expression.getThenExpression();
      PsiExpression elseExpression = expression.getElseExpression();

      final int elseOffset = elseExpression == null ? getEndOffset(expression) - 1 : getStartOffset(elseExpression);
      if (thenExpression != null) {
        condition.accept(this);
        generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
        PsiType type = expression.getType();
        addInstruction(new ConditionalGotoInstruction(elseOffset, true, condition));
        thenExpression.accept(this);
        generateBoxingUnboxingInstructionFor(thenExpression,type);

        addInstruction(new GotoInstruction(getEndOffset(expression)));

        if (elseExpression != null) {
          elseExpression.accept(this);
          generateBoxingUnboxingInstructionFor(elseExpression,type);
        }
        else {
          pushUnknown();
        }
      }
      else {
        pushUnknown();
      }
    }

    finishElement(expression);
  }

  private void pushUnknown() {
    addInstruction(new PushInstruction(DfaUnknownValue.getInstance(), null));
  }

  @Override public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
    startElement(expression);
    PsiExpression operand = expression.getOperand();
    PsiTypeElement checkType = expression.getCheckType();
    if (checkType != null) {
      operand.accept(this);
      PsiType type = checkType.getType();
      if (type instanceof PsiClassType) {
        type = ((PsiClassType)type).rawType();
      }
      addInstruction(new PushInstruction(myFactory.getTypeFactory().create(type), null));
      addInstruction(new InstanceofInstruction(expression, expression.getProject(), operand, type));
    }
    else {
      pushUnknown();
    }

    finishElement(expression);
  }

  private void addMethodThrows(PsiMethod method) {
    if (method != null) {
      PsiClassType[] refs = method.getThrowsList().getReferencedTypes();
      for (PsiClassType ref : refs) {
        pushUnknown();
        ConditionalGotoInstruction cond = new ConditionalGotoInstruction(NOT_FOUND, false, null);
        addInstruction(cond);
        addInstruction(new EmptyStackInstruction());
        addInstruction(new PushInstruction(myFactory.getTypeFactory().create(ref), null));
        addThrowCode(ref);
        cond.setOffset(myCurrentFlow.getInstructionCount());
      }
    }
  }

  @Override public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    try {
      startElement(expression);

      if (processSpecialMethods(expression)) {
        return;
      }

      PsiReferenceExpression methodExpression = expression.getMethodExpression();
      PsiExpression qualifierExpression = methodExpression.getQualifierExpression();

      if (qualifierExpression != null) {
        qualifierExpression.accept(this);
      }
      else {
        pushUnknown();
      }

      PsiExpression[] paramExprs = expression.getArgumentList().getExpressions();
      PsiElement method = methodExpression.resolve();
      PsiParameter[] parameters = method instanceof PsiMethod ? ((PsiMethod)method).getParameterList().getParameters() : null;
      for (int i = 0; i < paramExprs.length; i++) {
        PsiExpression paramExpr = paramExprs[i];
        paramExpr.accept(this);
        if (parameters != null && i < parameters.length) {
          generateBoxingUnboxingInstructionFor(paramExpr, parameters[i].getType());
        }
      }

      addInstruction(new MethodCallInstruction(expression));

      if (!myCatchStack.isEmpty()) {
        addMethodThrows(expression.resolveMethod());
      }
    }
    finally {
      finishElement(expression);
    }
  }

  private boolean processSpecialMethods(PsiMethodCallExpression expression) {
    PsiReferenceExpression methodExpression = expression.getMethodExpression();
    PsiExpression qualifierExpression = methodExpression.getQualifierExpression();

    PsiMethod resolved = expression.resolveMethod();
    if (resolved != null) {
      final PsiExpressionList argList = expression.getArgumentList();
      @NonNls String methodName = resolved.getName();

      PsiExpression[] params = argList.getExpressions();
      PsiClass owner = resolved.getContainingClass();
      final int exitPoint = getEndOffset(expression) - 1;
      if (owner != null) {
        final String className = owner.getQualifiedName();
        if ("java.lang.System".equals(className)) {
          if ("exit".equals(methodName)) {
            pushParameters(params, false, false);
            addInstruction(new ReturnInstruction());
            return true;
          }
        }
        else if ("junit.framework.Assert".equals(className) || "org.junit.Assert".equals(className) || "org.testng.Assert".equals(className)) {
          boolean testng = "org.testng.Assert".equals(className);
          if ("fail".equals(methodName)) {
            pushParameters(params, false, !testng);
            addInstruction(new ReturnInstruction());
            return true;
          }
          else if ("assertTrue".equals(methodName)) {
            pushParameters(params, true, !testng);
            conditionalExit(exitPoint, false);
            return true;
          }
          else if ("assertFalse".equals(methodName)) {
            pushParameters(params, true, !testng);
            conditionalExit(exitPoint, true);
            return true;
          }
          else if ("assertNull".equals(methodName)) {
            pushParameters(params, true, !testng);

            addInstruction(new PushInstruction(myFactory.getConstFactory().getNull(), null));
            addInstruction(new BinopInstruction("==", null, expression.getProject()));
            conditionalExit(exitPoint, false);
            return true;
          }
          else if ("assertNotNull".equals(methodName)) {
            pushParameters(params, true, !testng);

            addInstruction(new PushInstruction(myFactory.getConstFactory().getNull(), null));
            addInstruction(new BinopInstruction("==", null, expression.getProject()));
            conditionalExit(exitPoint, true);
            return true;
          }
          return false;
        }
      }

      // Idea project only.
      if (qualifierExpression != null) {
        if (qualifierExpression.textMatches("LOG")) {
          final PsiType qualifierType = qualifierExpression.getType();
          if (qualifierType != null && qualifierType.equalsToText("com.intellij.openapi.diagnostic.Logger")) {
            if ("error".equals(methodName)) {
              for (PsiExpression param : params) {
                param.accept(this);
                addInstruction(new PopInstruction());
              }
              addInstruction(new ReturnInstruction());
              return true;
            }
            else if ("assertTrue".equals(methodName)) {
              params[0].accept(this);
              for (int i = 1; i < params.length; i++) {
                params[i].accept(this);
                addInstruction(new PopInstruction());
              }
              conditionalExit(exitPoint, false);
              return true;
            }
          }
        }
      }
    }

    return false;
  }

  private void conditionalExit(final int continuePoint, final boolean exitIfTrue) {
    addInstruction(new ConditionalGotoInstruction(continuePoint, exitIfTrue, null));
    addInstruction(new ReturnInstruction());
    pushUnknown();
  }

  private void pushParameters(final PsiExpression[] params, final boolean leaveOnStack, boolean lastParameterIsSignificant) {
    for (int i = 0; i < params.length; i++) {
      PsiExpression param = params[i];
      param.accept(this);
      if (leaveOnStack) {
        if (lastParameterIsSignificant && i == params.length - 1 || !lastParameterIsSignificant && i == 0) continue;
      }

      addInstruction(new PopInstruction());
    }
  }

  private void pushTypeOrUnknown(PsiExpression expr) {
    PsiType type = expr.getType();

    final DfaValue dfaValue;
    if (type instanceof PsiClassType) {
      dfaValue = myFactory.getTypeFactory().create(type);
    }
    else {
      dfaValue = null;
    }

    addInstruction(new PushInstruction(dfaValue, null));
  }

  @Override public void visitNewExpression(PsiNewExpression expression) {
    startElement(expression);

    pushUnknown();

    if (expression.getType() instanceof PsiArrayType) {
      final PsiExpression[] dimensions = expression.getArrayDimensions();
      for (final PsiExpression dimension : dimensions) {
        dimension.accept(this);
      }
      for (PsiExpression dimension : dimensions) {
        addInstruction(new PopInstruction());
      }
      final PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
      if (arrayInitializer != null) {
        for (final PsiExpression initializer : arrayInitializer.getInitializers()) {
          initializer.accept(this);
          addInstruction(new PopInstruction());
        }
      }
      addInstruction(new MethodCallInstruction(expression));
    }
    else {
      final PsiExpressionList args = expression.getArgumentList();
      PsiMethod ctr = expression.resolveConstructor();
      if (args != null) {
        PsiExpression[] params = args.getExpressions();
        PsiParameter[] parameters = ctr == null ? null : ctr.getParameterList().getParameters();
        for (int i = 0; i < params.length; i++) {
          PsiExpression param = params[i];
          param.accept(this);
          if (parameters != null && i < parameters.length) {
            generateBoxingUnboxingInstructionFor(param, parameters[i].getType());
          }
        }
      }

      addInstruction(new MethodCallInstruction(expression));

      if (!myCatchStack.isEmpty()) {
        addMethodThrows(ctr);
      }

    }

    finishElement(expression);
  }

  @Override public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
    startElement(expression);
    PsiExpression inner = expression.getExpression();
    if (inner != null) {
      inner.accept(this);
    }
    else {
      pushUnknown();
    }
    finishElement(expression);
  }

  @Override public void visitPostfixExpression(PsiPostfixExpression expression) {
    startElement(expression);

    PsiExpression operand = expression.getOperand();
    operand.accept(this);
    generateBoxingUnboxingInstructionFor(operand, PsiType.INT);

    addInstruction(new PopInstruction());
    pushUnknown();

    if (operand instanceof PsiReferenceExpression) {
      PsiVariable psiVariable = DfaValueFactory.resolveVariable((PsiReferenceExpression)expression.getOperand());
      if (psiVariable != null) {
        DfaVariableValue dfaVariable = myFactory.getVarFactory().create(psiVariable, false);
        addInstruction(new FlushVariableInstruction(dfaVariable));
      }
    }

    finishElement(expression);
  }

  @Override public void visitPrefixExpression(PsiPrefixExpression expression) {
    startElement(expression);

    DfaValue dfaValue = myFactory.create(expression);
    if (dfaValue == null) {
      PsiExpression operand = expression.getOperand();

      if (operand == null) {
        pushUnknown();
      }
      else {
        operand.accept(this);
        PsiType type = expression.getType();
        PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(type);
        generateBoxingUnboxingInstructionFor(operand, unboxed == null ? type : unboxed);
        if (expression.getOperationSign().getTokenType() == JavaTokenType.EXCL) {
          addInstruction(new NotInstruction());
        }
        else {
          addInstruction(new PopInstruction());
          pushUnknown();

          if (operand instanceof PsiReferenceExpression) {
            PsiVariable psiVariable = DfaValueFactory.resolveVariable((PsiReferenceExpression)operand);
            if (psiVariable != null) {
              DfaVariableValue dfaVariable = myFactory.getVarFactory().create(psiVariable, false);
              addInstruction(new FlushVariableInstruction(dfaVariable));
            }
          }
        }
      }
    }
    else {
      addInstruction(new PushInstruction(dfaValue, expression));
    }

    finishElement(expression);
  }

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    startElement(expression);

    DfaValue dfaValue = myFactory.create(expression);
    if (dfaValue instanceof DfaVariableValue) {
      DfaVariableValue dfaVariable = (DfaVariableValue)dfaValue;
      PsiVariable psiVariable = dfaVariable.getPsiVariable();
      if (psiVariable instanceof PsiField && !psiVariable.hasModifierProperty(PsiModifier.FINAL)) {
        addField(dfaVariable);
      }
    }

    final PsiExpression qualifierExpression = expression.getQualifierExpression();
    if (qualifierExpression != null) {
      qualifierExpression.accept(this);
      if (expression.resolve() instanceof PsiField) {
        addInstruction(new FieldReferenceInstruction(expression, null));
      }
      else {
        addInstruction(new PopInstruction());
      }
    }

    addInstruction(new PushInstruction(dfaValue, expression));

    finishElement(expression);
  }

  private void addField(DfaVariableValue field) {
    myFields.add(field);
  }

  @Override public void visitSuperExpression(PsiSuperExpression expression) {
    startElement(expression);
    addInstruction(new PushInstruction(myFactory.getNotNullFactory().create(expression.getType()), null));
    finishElement(expression);
  }

  @Override public void visitThisExpression(PsiThisExpression expression) {
    startElement(expression);
    addInstruction(new PushInstruction(myFactory.getNotNullFactory().create(expression.getType()), null));
    finishElement(expression);
  }

  @Override public void visitLiteralExpression(PsiLiteralExpression expression) {
    startElement(expression);

    DfaValue dfaValue = myFactory.create(expression);
    addInstruction(new PushInstruction(dfaValue, expression));

    finishElement(expression);
  }

  @Override public void visitTypeCastExpression(PsiTypeCastExpression castExpression) {
    startElement(castExpression);
    PsiExpression operand = castExpression.getOperand();

    if (operand != null) {
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(operand, castExpression.getType());
    }
    else {
      pushTypeOrUnknown(castExpression);
    }

    addInstruction(createCastInstruction(castExpression));
    finishElement(castExpression);
  }

  @Override public void visitClass(PsiClass aClass) {
  }

  @NotNull
  private static Instruction createCastInstruction(PsiTypeCastExpression castExpression) {
    PsiExpression expr = castExpression.getOperand();
    final PsiTypeElement typeElement = castExpression.getCastType();
    if (typeElement != null && !RedundantCastUtil.isTypeCastSemantical(castExpression)) {
      PsiType castType = typeElement.getType();
      if (expr != null) {
        return new TypeCastInstruction(castExpression, expr, castType);
      }
    }
    return new Instruction() {

      @Override
      public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
        stateBefore.pop();
        stateBefore.push(DfaUnknownValue.getInstance());
        return nextInstruction(runner, stateBefore);
      }
    };
  }


}
