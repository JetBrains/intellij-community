/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ConditionCheckManager;
import com.intellij.codeInsight.ConditionChecker;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInsight.ConditionChecker.Type.*;
import static com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint;
import static com.intellij.psi.CommonClassNames.*;

class ControlFlowAnalyzer extends JavaElementVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer");
  private static final int NOT_FOUND = -10;
  private boolean myIgnoreAssertions;

  private static class CannotAnalyzeException extends RuntimeException { }

  private final DfaValueFactory myFactory;
  private ControlFlow myPass1Flow;
  private ControlFlow myCurrentFlow;
  private int myPassNumber;
  private Set<DfaVariableValue> myFields;
  private Stack<CatchDescriptor> myCatchStack;
  private DfaValue myRuntimeException;
  private DfaValue myError;
  private PsiType myNpe;

  ControlFlowAnalyzer(final DfaValueFactory valueFactory) {
    myFactory = valueFactory;
  }

  public ControlFlow buildControlFlow(@NotNull PsiElement codeFragment, boolean ignoreAssertions) {
    myIgnoreAssertions = ignoreAssertions;
    PsiManager manager = codeFragment.getManager();
    GlobalSearchScope scope = codeFragment.getResolveScope();
    myRuntimeException = myFactory.getNotNullFactory().create(PsiType.getJavaLangRuntimeException(manager, scope));
    myError = myFactory.getNotNullFactory().create(PsiType.getJavaLangError(manager, scope));
    myNpe = JavaPsiFacade.getElementFactory(manager.getProject()).createTypeByFQClassName(JAVA_LANG_NULL_POINTER_EXCEPTION, scope);
    myFields = new HashSet<DfaVariableValue>();
    myCatchStack = new Stack<CatchDescriptor>();
    myPassNumber = 1;
    myPass1Flow = new ControlFlow(myFactory);
    myCurrentFlow = myPass1Flow;

    try {
      codeFragment.accept(this);
    }
    catch (CannotAnalyzeException e) {
      return null;
    }

    myPassNumber = 2;
    final ControlFlow pass2Flow = new ControlFlow(myFactory);
    myCurrentFlow = pass2Flow;

    codeFragment.accept(this);

    pass2Flow.setFields(myFields.toArray(new DfaVariableValue[myFields.size()]));

    if (myPass1Flow.getInstructionCount() != pass2Flow.getInstructionCount()) {
      LOG.error(Arrays.toString(myPass1Flow.getInstructions()) + "!=\n" + Arrays.toString(pass2Flow.getInstructions()));
    }

    addInstruction(new ReturnInstruction());

    return pass2Flow;
  }

  private boolean myRecursionStopper = false;

  private void addInstruction(Instruction i) {
    ProgressManager.checkCanceled();

    if (!myRecursionStopper) {
      myRecursionStopper = true;
      try {
        // add extra conditional goto in order to handle possible runtime exceptions that could be caught by finally block
        if (i instanceof BranchingInstruction || i instanceof AssignInstruction || i instanceof MethodCallInstruction) {
          addConditionalRuntimeThrow();
        }
      }
      finally {
        myRecursionStopper = false;
      }
    }

    myCurrentFlow.addInstruction(i);
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

  @Override
  public void visitErrorElement(PsiErrorElement element) {
    throw new CannotAnalyzeException();
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
          generateNonLazyExpression(true, lExpr, rExpr, type);
        }
        else {
          generateDefaultBinOp(lExpr, rExpr, type);
        }
      }
      else if (op == JavaTokenType.OREQ) {
        if (isBoolean) {
          generateNonLazyExpression(false, lExpr, rExpr, type);
        }
        else {
          generateDefaultBinOp(lExpr, rExpr, type);
        }
      }
      else if (op == JavaTokenType.XOREQ) {
        if (isBoolean) {
          generateXorExpression(expression, new PsiExpression[]{lExpr, rExpr}, type);
        }
        else {
          generateDefaultBinOp(lExpr, rExpr, type);
        }
      }
      else if (op == JavaTokenType.PLUSEQ && type != null && type.equalsToText(JAVA_LANG_STRING)) {
        lExpr.accept(this);
        rExpr.accept(this);
        addInstruction(new BinopInstruction(JavaTokenType.PLUS, null, lExpr.getProject()));
      }
      else {
        generateDefaultBinOp(lExpr, rExpr, type);
      }

      addInstruction(new AssignInstruction(rExpr));
    }
    finally {
      finishElement(expression);
    }
  }

  private void generateDefaultBinOp(PsiExpression lExpr, PsiExpression rExpr, final PsiType exprType) {
    lExpr.accept(this);
    generateBoxingUnboxingInstructionFor(lExpr,exprType);
    rExpr.accept(this);
    generateBoxingUnboxingInstructionFor(rExpr,exprType);
    addInstruction(new BinopInstruction(null, null, lExpr.getProject()));
  }

  @Override public void visitAssertStatement(PsiAssertStatement statement) {
    if (myIgnoreAssertions) {
      return;
    }

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
        addInstruction(new EmptyInstruction(element));
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

  @Override
  public void visitField(PsiField field) {
    PsiExpression initializer = field.getInitializer();
    if (initializer != null) {
      initializeVariable(field, initializer);
    }
  }

  private void initializeVariable(PsiVariable variable, PsiExpression initializer) {
    DfaVariableValue dfaVariable = myFactory.getVarFactory().createVariableValue(variable, false);
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

    for (PsiStatement statement : block.getStatements()) {
      if (statement instanceof PsiDeclarationStatement) {
        for (PsiElement declaration : ((PsiDeclarationStatement)statement).getDeclaredElements()) {
          if (declaration instanceof PsiVariable) {
            myCurrentFlow.removeVariable((PsiVariable)declaration);
          }
        }
      }
      statement.accept(this);
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
      Instruction instruction = offset == -1 ? new EmptyInstruction(null) : new GotoInstruction(offset);
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
    DfaVariableValue dfaVariable = myFactory.getVarFactory().createVariableValue(parameter, false);
    addInstruction(new FlushVariableInstruction(dfaVariable));

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
      addInstruction(new PushInstruction(null, null));
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

    returnCheckingFinally();
    finishElement(statement);
  }

  private void returnCheckingFinally() {
    int finallyOffset = getFinallyOffset();
    if (finallyOffset != NOT_FOUND) {
      addInstruction(new GosubInstruction(finallyOffset));
    }
    addInstruction(new ReturnInstruction());
  }

  @Override public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    startElement(statement);
    finishElement(statement);
  }

  @Override public void visitSwitchStatement(PsiSwitchStatement switchStmt) {
    startElement(switchStmt);
    PsiExpression caseExpression = switchStmt.getExpression();
    Set<PsiEnumConstant> enumValues = null;
    if (caseExpression != null /*&& !(caseExpression instanceof PsiReferenceExpression)*/) {
      caseExpression.accept(this);

      generateBoxingUnboxingInstructionFor(caseExpression, PsiType.INT);
      final PsiClass psiClass = PsiUtil.resolveClassInType(caseExpression.getType());
      if (psiClass != null && psiClass.isEnum()) {
        addInstruction(new FieldReferenceInstruction(caseExpression, "switch statement expression"));
        enumValues = new HashSet<PsiEnumConstant>();
        for (PsiField f : psiClass.getFields()) {
          if (f instanceof PsiEnumConstant) {
            enumValues.add((PsiEnumConstant)f);
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
                
                addInstruction(new PushInstruction(getExpressionDfaValue((PsiReferenceExpression)caseExpression), caseExpression));
                caseValue.accept(this);
                addInstruction(new BinopInstruction(JavaTokenType.EQEQ, null, caseExpression.getProject()));
              }
              else {
                pushUnknown();
              }

              addInstruction(new ConditionalGotoInstruction(offset, false, statement));

              if (enumValues != null) {
                if (caseValue instanceof PsiReferenceExpression) {
                  //noinspection SuspiciousMethodCalls
                  enumValues.remove(((PsiReferenceExpression)caseValue).resolve());
                }
              }
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }

      if (enumValues == null || !enumValues.isEmpty()) {
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
      addInstruction(new FieldReferenceInstruction(lock, "Synchronized value"));
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
      addInstruction(new DupInstruction());
      addInstruction(new PushInstruction(myFactory.getConstFactory().getNull(), null));
      addInstruction(new BinopInstruction(JavaTokenType.EQEQ, null, statement.getProject()));
      ConditionalGotoInstruction gotoInstruction = new ConditionalGotoInstruction(-1, true, null);
      addInstruction(gotoInstruction);
      addThrowCode(myNpe);
      gotoInstruction.setOffset(myCurrentFlow.getInstructionCount());
      addThrowCode(exception.getType());
    }

    finishElement(statement);
  }

  private void addConditionalRuntimeThrow() {
    for (int i = myCatchStack.size() - 1; i >= 0; i--) {
      CatchDescriptor cd = myCatchStack.get(i);
      if (cd.isFinally()) {
        addConditionalRuntimeThrow(cd, false);
        continue;
      }

      PsiType type = cd.getLubType();
      if (type instanceof PsiClassType && ExceptionUtil.isUncheckedExceptionOrSuperclass((PsiClassType)type)) {
        addConditionalRuntimeThrow(cd, true);
      }
    }
  }

  private void addConditionalRuntimeThrow(CatchDescriptor cd, boolean forCatch) {
    pushUnknown();
    final ConditionalGotoInstruction branch = new ConditionalGotoInstruction(-1, false, null);
    addInstruction(branch);
    addInstruction(new EmptyStackInstruction());
    if (forCatch) {
      PsiType type = cd.getLubType();
      boolean isRuntime = InheritanceUtil.isInheritor(type, JAVA_LANG_RUNTIME_EXCEPTION) || ExceptionUtil.isGeneralExceptionType(type);
      boolean isError = InheritanceUtil.isInheritor(type, JAVA_LANG_ERROR) || type.equalsToText(JAVA_LANG_THROWABLE);
      if (isRuntime != isError) {
        addInstruction(new PushInstruction(isRuntime ? myRuntimeException : myError, null));
        addGotoCatch(cd);
      } else {
        pushUnknown();
        final ConditionalGotoInstruction branch2 = new ConditionalGotoInstruction(-1, false, null);
        addInstruction(branch2);
        addInstruction(new PushInstruction(myError, null));
        addGotoCatch(cd);
        branch2.setOffset(myCurrentFlow.getInstructionCount());
        addInstruction(new PushInstruction(myRuntimeException, null));
        addGotoCatch(cd);
      }
    }
    else {
      addInstruction(new GosubInstruction(cd.getJumpOffset(this)));
      addInstruction(new ReturnInstruction());
    }
    branch.setOffset(myCurrentFlow.getInstructionCount());
  }

  private void addThrowCode(PsiType exceptionClass) {
    if (exceptionClass == null) return;
    for (int i = myCatchStack.size() - 1; i >= 0; i--) {
      CatchDescriptor cd = myCatchStack.get(i);
      if (cd.isFinally()) {
        addInstruction(new GosubInstruction(cd.getJumpOffset(this)));
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
   */
  private void addGotoCatch(CatchDescriptor cd) {
    addInstruction(new PushInstruction(myFactory.getVarFactory().createVariableValue(cd.getParameter(), false), null));
    addInstruction(new SwapInstruction());
    myCurrentFlow.addInstruction(new AssignInstruction(null));
    addInstruction(new PopInstruction());
    addInstruction(new GotoInstruction(cd.getJumpOffset(this)));
  }

  private int getFinallyOffset() {
    for (int i = myCatchStack.size() - 1; i >= 0; i--) {
      CatchDescriptor cd = myCatchStack.get(i);
      if (cd.isFinally()) return cd.getJumpOffset(this);
    }

    return NOT_FOUND;
  }

  private static class ApplyNotNullInstruction extends Instruction {
    private PsiMethodCallExpression myCall;

    private ApplyNotNullInstruction(PsiMethodCallExpression call) {
      myCall = call;
    }

    @Override
    public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState state, InstructionVisitor visitor) {
      DfaValue value = state.pop();
      DfaValueFactory factory = runner.getFactory();
      if (state.applyCondition(
        factory.getRelationFactory().createRelation(value, factory.getConstFactory().getNull(), JavaTokenType.EQEQ, true))) {
        return nextInstruction(runner, state);
      }
      if (visitor instanceof StandardInstructionVisitor) {
        ((StandardInstructionVisitor)visitor).skipConstantConditionReporting(myCall);
      }
      return DfaInstructionState.EMPTY_ARRAY;
    }
  }

  private static class CatchDescriptor {
    private final PsiType myType;
    private final PsiParameter myParameter;
    private final PsiCodeBlock myBlock;
    private final boolean myIsFinally;

    public CatchDescriptor(PsiCodeBlock finallyBlock) {
      myType = null;
      myParameter = null;
      myBlock = finallyBlock;
      myIsFinally = true;
    }

    public CatchDescriptor(PsiParameter parameter, PsiCodeBlock catchBlock) {
      myType = parameter.getType();
      myParameter = parameter;
      myBlock = catchBlock;
      myIsFinally = false;
    }

    public PsiType getType() {
      return myType;
    }

    PsiType getLubType() {
      PsiType type = myType;
      if (type instanceof PsiDisjunctionType) {
        return ((PsiDisjunctionType)type).getLeastUpperBound();
      }
      return type;
    }

    public boolean isFinally() {
      return myIsFinally;
    }

    public int getJumpOffset(ControlFlowAnalyzer analyzer) {
      return analyzer.getStartOffset(myBlock);
    }

    public PsiParameter getParameter() {
      return myParameter;
    }
  }

  @Override
  public void visitTryStatement(PsiTryStatement statement) {
    startElement(statement);

    PsiResourceList resourceList = statement.getResourceList();
    PsiCodeBlock tryBlock = statement.getTryBlock();
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
      if (parameter != null && catchBlock != null) {
        PsiType type = parameter.getType();
        if (type instanceof PsiClassType || type instanceof PsiDisjunctionType) {
          myCatchStack.push(new CatchDescriptor(parameter, catchBlock));
          catchesPushCount++;
          continue;
        }
      }
      throw new CannotAnalyzeException();
    }

    int endOffset = finallyBlock == null ? getEndOffset(statement) : getStartOffset(finallyBlock) - 2;

    if (resourceList != null) {
      resourceList.accept(this);
    }

    if (tryBlock != null) {
      tryBlock.accept(this);
    }

    for (int i = 0; i < catchesPushCount; i++) {
      myCatchStack.pop();
    }

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

  @Override
  public void visitCatchSection(PsiCatchSection section) {
    PsiCodeBlock catchBlock = section.getCatchBlock();
    if (catchBlock != null) {
      catchBlock.accept(this);
    }
  }

  @Override
  public void visitResourceList(PsiResourceList resourceList) {
    for (PsiResourceVariable variable : resourceList.getResourceVariables()) {
      PsiExpression initializer = variable.getInitializer();
      if (initializer != null) {
        initializeVariable(variable, initializer);
      }
      PsiMethod closer = PsiUtil.getResourceCloserMethod(variable);
      if (closer != null) {
        addMethodThrows(closer);
      }
    }
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
    DfaValue dfaValue = myFactory.createValue(expression);
    addInstruction(new PushInstruction(dfaValue, expression));
    finishElement(expression);
  }

  @Override
  public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
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

  @Override
  public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
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

  @Override
  public void visitPolyadicExpression(PsiPolyadicExpression expression) {
    startElement(expression);

    try {
      DfaValue dfaValue = myFactory.createValue(expression);
      if (dfaValue != null) {
        addInstruction(new PushInstruction(dfaValue, expression));
        return;
      }
      IElementType op = expression.getOperationTokenType();

      PsiExpression[] operands = expression.getOperands();
      if (operands.length <= 1) {
        pushUnknown();
        return;
      }
      PsiType type = expression.getType();
      if (op == JavaTokenType.ANDAND) {
        generateAndExpression(operands, type);
      }
      else if (op == JavaTokenType.OROR) {
        generateOrExpression(operands, type);
      }
      else if (op == JavaTokenType.XOR && PsiType.BOOLEAN.equals(type)) {
        generateXorExpression(expression, operands, type);
      }
      else {
        generateOther(expression, op, operands, type);
      }
    }
    finally {
      finishElement(expression);
    }
  }

  private void generateOther(PsiPolyadicExpression expression, IElementType op, PsiExpression[] operands, PsiType type) {
    op = substituteBinaryOperation(op, type);

    PsiExpression lExpr = operands[0];
    lExpr.accept(this);
    PsiType lType = lExpr.getType();

    for (int i = 1; i < operands.length; i++) {
      PsiExpression rExpr = operands[i];
      PsiType rType = rExpr.getType();

      acceptBinaryRightOperand(op, type, lExpr, lType, rExpr, rType);
      addInstruction(new BinopInstruction(op, expression.isPhysical() ? expression : null, expression.getProject()));

      lExpr = rExpr;
      lType = rType;
    }
  }

  @Nullable
  private static IElementType substituteBinaryOperation(IElementType op, PsiType type) {
    if (JavaTokenType.PLUS == op && (type == null || !type.equalsToText(JAVA_LANG_STRING))) {
      return null;
    }
    return op;
  }

  private void acceptBinaryRightOperand(@Nullable IElementType op, PsiType type,
                                        PsiExpression lExpr, PsiType lType,
                                        PsiExpression rExpr, PsiType rType) {
    boolean comparing = op == JavaTokenType.EQEQ || op == JavaTokenType.NE;
    boolean comparingRef = comparing
                           && !TypeConversionUtil.isPrimitiveAndNotNull(lType)
                           && !TypeConversionUtil.isPrimitiveAndNotNull(rType);

    boolean comparingPrimitiveNumeric = comparing &&
                                        TypeConversionUtil.isPrimitiveAndNotNull(lType) &&
                                        TypeConversionUtil.isPrimitiveAndNotNull(rType) &&
                                        TypeConversionUtil.isNumericType(lType) &&
                                        TypeConversionUtil.isNumericType(rType);

    PsiType castType = comparingPrimitiveNumeric ? TypeConversionUtil.isFloatOrDoubleType(lType) ? PsiType.DOUBLE : PsiType.LONG : type;

    if (!comparingRef) {
      generateBoxingUnboxingInstructionFor(lExpr,castType);
    }

    rExpr.accept(this);
    if (!comparingRef) {
      generateBoxingUnboxingInstructionFor(rExpr, castType);
    }
  }

  private void generateBoxingUnboxingInstructionFor(PsiExpression expression, PsiType expectedType) {
    PsiType exprType = expression.getType();

    if (TypeConversionUtil.isPrimitiveAndNotNull(expectedType) && TypeConversionUtil.isPrimitiveWrapper(exprType)) {
      addInstruction(new MethodCallInstruction(expression, MethodCallInstruction.MethodType.UNBOXING, expectedType));
    }
    else if (TypeConversionUtil.isAssignableFromPrimitiveWrapper(expectedType) && TypeConversionUtil.isPrimitiveAndNotNull(exprType)) {
      addInstruction(new MethodCallInstruction(expression, MethodCallInstruction.MethodType.BOXING, expectedType));
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

  private void generateXorExpression(PsiExpression expression, PsiExpression[] operands, final PsiType exprType) {
    PsiExpression operand = operands[0];
    operand.accept(this);
    generateBoxingUnboxingInstructionFor(operand, exprType);
    for (int i = 1; i < operands.length; i++) {
      operand = operands[i];
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(operand, exprType);
      PsiElement psiAnchor = expression.isPhysical() ? expression : null;
      addInstruction(new BinopInstruction(JavaTokenType.NE, psiAnchor, expression.getProject()));
    }
  }

  private void generateOrExpression(PsiExpression[] operands, final PsiType exprType) {
    for (int i = 0; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(operand, exprType);
      PsiExpression nextOperand = i == operands.length - 1 ? null : operands[i + 1];
      if (nextOperand != null) {
        addInstruction(new ConditionalGotoInstruction(getStartOffset(nextOperand), true, operand));
        addInstruction(new PushInstruction(myFactory.getConstFactory().getTrue(), null));
        addInstruction(new GotoInstruction(getEndOffset(operands[operands.length - 1])));
      }
    }
  }

  private void generateNonLazyExpression(boolean and, PsiExpression lExpression, PsiExpression rExpression, PsiType exprType) {
    rExpression.accept(this);
    generateBoxingUnboxingInstructionFor(rExpression, exprType);

    lExpression.accept(this);
    generateBoxingUnboxingInstructionFor(lExpression, exprType);

    ConditionalGotoInstruction toPopAndPushSuccess = new ConditionalGotoInstruction(-1, and, lExpression);
    addInstruction(toPopAndPushSuccess);
    GotoInstruction overPushSuccess = new GotoInstruction(-1);
    addInstruction(overPushSuccess);

    PopInstruction pop = new PopInstruction();
    addInstruction(pop);
    DfaConstValue constValue = and ? myFactory.getConstFactory().getFalse() : myFactory.getConstFactory().getTrue();
    PushInstruction pushSuccess = new PushInstruction(constValue, null);
    addInstruction(pushSuccess);

    toPopAndPushSuccess.setOffset(pop.getIndex());
    overPushSuccess.setOffset(pushSuccess.getIndex() + 1);
  }

  private void generateAndExpression(PsiExpression[] operands, final PsiType exprType) {
    List<ConditionalGotoInstruction> branchToFail = new ArrayList<ConditionalGotoInstruction>();
    for (PsiExpression operand : operands) {
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(operand, exprType);

      ConditionalGotoInstruction onFail = new ConditionalGotoInstruction(-1, true, operand);
      branchToFail.add(onFail);
      addInstruction(onFail);
    }

    addInstruction(new PushInstruction(myFactory.getConstFactory().getTrue(), null));
    GotoInstruction toSuccess = new GotoInstruction(-1);
    addInstruction(toSuccess);
    PushInstruction pushFalse = new PushInstruction(myFactory.getConstFactory().getFalse(), null);
    addInstruction(pushFalse);
    for (ConditionalGotoInstruction toFail : branchToFail) {
      toFail.setOffset(pushFalse.getIndex());
    }
    toSuccess.setOffset(pushFalse.getIndex()+1);

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
        addInstruction(new PushInstruction(myFactory.getNotNullFactory().create(ref), null));
        addThrowCode(ref);
        cond.setOffset(myCurrentFlow.getInstructionCount());
      }
    }
  }

  @Override public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    try {
      startElement(expression);

      if (handleContracts(expression, getCallContracts(expression))) {
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

      PsiExpression[] expressions = expression.getArgumentList().getExpressions();
      PsiElement method = methodExpression.resolve();
      PsiParameter[] parameters = method instanceof PsiMethod ? ((PsiMethod)method).getParameterList().getParameters() : null;
      for (int i = 0; i < expressions.length; i++) {
        PsiExpression paramExpr = expressions[i];
        paramExpr.accept(this);
        if (parameters != null && i < parameters.length) {
          generateBoxingUnboxingInstructionFor(paramExpr, parameters[i].getType());
        }
      }

      addInstruction(new MethodCallInstruction(expression, createChainedVariableValue(expression)));

      if (!myCatchStack.isEmpty()) {
        addMethodThrows(expression.resolveMethod());
      }

      if (expressions.length == 1 && method instanceof PsiMethod &&
          "equals".equals(((PsiMethod)method).getName()) && parameters.length == 1 &&
          parameters[0].getType().equalsToText(JAVA_LANG_OBJECT) &&
          PsiType.BOOLEAN.equals(((PsiMethod)method).getReturnType())) {
        addInstruction(new PushInstruction(myFactory.getConstFactory().getFalse(), null));
        addInstruction(new SwapInstruction());
        addInstruction(new ConditionalGotoInstruction(getEndOffset(expression), true, null));

        addInstruction(new PopInstruction());
        addInstruction(new PushInstruction(myFactory.getConstFactory().getTrue(), null));

        expressions[0].accept(this);
        addInstruction(new ApplyNotNullInstruction(expression));
      }
    }
    finally {
      finishElement(expression);
    }
  }

  private boolean handleContracts(PsiMethodCallExpression expression, List<MethodContract> _contracts) {
    if (_contracts.isEmpty()) {
      return false;
    }

    final PsiExpression[] args = expression.getArgumentList().getExpressions();
    List<MethodContract> contracts = ContainerUtil.findAll(_contracts, new Condition<MethodContract>() {
      @Override
      public boolean value(MethodContract contract) {
        return args.length == contract.arguments.length;
      }
    });
    if (contracts.isEmpty()) {
      return false;
    }

    for (PsiExpression arg : args) {
      arg.accept(this);
    }

    if (contracts.size() > 1) {
      addInstruction(new DupInstruction(args.length, contracts.size() - 1));
    }
    for (MethodContract contract : contracts) {
      handleContract(expression, contract);
    }
    pushUnknown(); // goto here if all contracts are false
    return true;
  }
  
  private void handleContract(PsiMethodCallExpression expression, MethodContract contract) {
    PsiExpression[] args = expression.getArgumentList().getExpressions();

    final int exitPoint = getEndOffset(expression);

    List<ConditionalGotoInstruction> gotoContractFalse = new SmartList<ConditionalGotoInstruction>();
    for (int i = args.length - 1; i >= 0; i--) {
      ValueConstraint arg = contract.arguments[i];
      if (arg == ValueConstraint.NULL_VALUE || arg == ValueConstraint.NOT_NULL_VALUE) {
        addInstruction(new PushInstruction(myFactory.getConstFactory().getNull(), null));
        addInstruction(new BinopInstruction(JavaTokenType.EQEQ, null, expression.getProject()));
      }
      else if (arg != ValueConstraint.TRUE_VALUE && arg != ValueConstraint.FALSE_VALUE) {
        addInstruction(new PopInstruction());
        continue;
      }

      boolean expectingTrueOnStack = arg == ValueConstraint.NULL_VALUE || arg == ValueConstraint.TRUE_VALUE;
      ConditionalGotoInstruction condGoto = new ConditionalGotoInstruction(-1, expectingTrueOnStack, null);
      gotoContractFalse.add(condGoto);
      addInstruction(condGoto);
    }

    // if contract is true
    switch (contract.returnValue) {
      case ANY_VALUE:
        pushUnknown();
        addInstruction(new GotoInstruction(exitPoint));
        break;
      case NULL_VALUE:
        addInstruction(new PushInstruction(myFactory.getConstFactory().getNull(), null));
        addInstruction(new GotoInstruction(exitPoint));
        break;
      case NOT_NULL_VALUE:
        PsiType type = expression.getType();
        Nullness nullability = DfaPsiUtil.getElementNullability(type, expression.resolveMethod());
        addInstruction(new PushInstruction(myFactory.createTypeValueWithNullability(type, nullability), null));
        addInstruction(new GotoInstruction(exitPoint));
        break;
      case TRUE_VALUE:
        addInstruction(new PushInstruction(myFactory.getConstFactory().getTrue(), null));
        addInstruction(new GotoInstruction(exitPoint));
        break;
      case FALSE_VALUE:
        addInstruction(new PushInstruction(myFactory.getConstFactory().getFalse(), null));
        addInstruction(new GotoInstruction(exitPoint));
        break;
      case THROW_EXCEPTION:
        int finallyOffset = getFinallyOffset();
        if (finallyOffset != NOT_FOUND) {
          addInstruction(new GosubInstruction(finallyOffset));
        }
        addInstruction(new ReturnInstruction());
        break;
      case SYSTEM_EXIT:
        addInstruction(new ReturnInstruction());
        break;
    }

    // if contract is false
    for (ConditionalGotoInstruction instruction : gotoContractFalse) {
      instruction.setOffset(myCurrentFlow.getInstructionCount());
    }
  }

  private static List<MethodContract> getCallContracts(PsiMethodCallExpression expression) {
    PsiMethod resolved = expression.resolveMethod();
    if (resolved != null) {
      final PsiAnnotation contractAnno = AnnotationUtil.findAnnotation(resolved, "org.jetbrains.annotations.Contract");
      if (contractAnno != null) {
        final Project project = expression.getProject();
        return CachedValuesManager.getManager(project).getCachedValue(contractAnno, new CachedValueProvider<List<MethodContract>>() {
          @Nullable
          @Override
          public Result<List<MethodContract>> compute() {
            PsiAnnotationMemberValue value = contractAnno.findAttributeValue(null);
            Object text = JavaPsiFacade.getInstance(project).getConstantEvaluationHelper().computeConstantExpression(value);
            if (text instanceof String) {
              try {
                return Result.create(parseContract((String)text), contractAnno);
              }
              catch (Exception ignored) {
              }
            }
            return Result.create(Collections.<MethodContract>emptyList(), contractAnno);
          }
        });
      }

      @NonNls String methodName = resolved.getName();

      PsiExpression[] params = expression.getArgumentList().getExpressions();
      PsiClass owner = resolved.getContainingClass();
      if (owner != null) {
        final String className = owner.getQualifiedName();
        if ("java.lang.System".equals(className)) {
          if ("exit".equals(methodName)) {
            return Collections.singletonList(new MethodContract(getAnyArgConstraints(params), ValueConstraint.SYSTEM_EXIT));
          }
        }
        else if ("junit.framework.Assert".equals(className) || "org.junit.Assert".equals(className) ||
                 "junit.framework.TestCase".equals(className) || "org.testng.Assert".equals(className)) {
          boolean testng = "org.testng.Assert".equals(className);
          if ("fail".equals(methodName)) {
            return Collections.singletonList(new MethodContract(getAnyArgConstraints(params), ValueConstraint.THROW_EXCEPTION));
          }

          int checkedParam = testng ? 0 : params.length - 1;
          ValueConstraint[] constraints = getAnyArgConstraints(params);
          if ("assertTrue".equals(methodName)) {
            constraints[checkedParam] = ValueConstraint.FALSE_VALUE;
            return Collections.singletonList(new MethodContract(constraints, ValueConstraint.THROW_EXCEPTION));
          }
          if ("assertFalse".equals(methodName)) {
            constraints[checkedParam] = ValueConstraint.TRUE_VALUE;
            return Collections.singletonList(new MethodContract(constraints, ValueConstraint.THROW_EXCEPTION));
          }
          if ("assertNull".equals(methodName)) {
            constraints[checkedParam] = ValueConstraint.NOT_NULL_VALUE;
            return Collections.singletonList(new MethodContract(constraints, ValueConstraint.THROW_EXCEPTION));
          }
          if ("assertNotNull".equals(methodName)) {
            constraints[checkedParam] = ValueConstraint.NULL_VALUE;
            return Collections.singletonList(new MethodContract(constraints, ValueConstraint.THROW_EXCEPTION));
          }
          return Collections.emptyList();
        }
      }

      ConditionChecker checker = ConditionCheckManager.findConditionChecker(resolved);
      if (checker != null) {
        ValueConstraint[] constraints = getAnyArgConstraints(params);
        int checkedParam = checker.getCheckedParameterIndex();
        if (checkedParam >= constraints.length) {
          return Collections.emptyList();
        }

        ConditionChecker.Type type = checker.getConditionCheckType();
        if (type == ASSERT_IS_NULL_METHOD || type == ASSERT_IS_NOT_NULL_METHOD) {
          constraints[checkedParam] = type == ASSERT_IS_NOT_NULL_METHOD ? ValueConstraint.NULL_VALUE : ValueConstraint.NOT_NULL_VALUE;
          return Collections.singletonList(new MethodContract(constraints, ValueConstraint.THROW_EXCEPTION));
        } else if (type == IS_NULL_METHOD || type == IS_NOT_NULL_METHOD) {
          constraints[checkedParam] = type == IS_NULL_METHOD ? ValueConstraint.NOT_NULL_VALUE : ValueConstraint.NULL_VALUE;
          return Collections.singletonList(new MethodContract(constraints, ValueConstraint.FALSE_VALUE));
        } else { //assertTrue or assertFalse
          constraints[checkedParam] = type == ASSERT_FALSE_METHOD ? ValueConstraint.TRUE_VALUE : ValueConstraint.FALSE_VALUE;
          return Collections.singletonList(new MethodContract(constraints, ValueConstraint.THROW_EXCEPTION));
        }
      }
    }

    return Collections.emptyList();
  }

  private static List<MethodContract> parseContract(String text) throws ParseException {
    List<MethodContract> result = ContainerUtil.newArrayList();
    for (String clause : StringUtil.replace(text, " ", "").split(";")) {
      String arrow = "->";
      int arrowIndex = clause.indexOf(arrow);
      if (arrowIndex < 0) {
        throw new ParseException("A contract clause must be in form arg1, ..., argN -> return-value");
      }
      
      String[] argStrings = clause.substring(0, arrowIndex).split(",");
      ValueConstraint[] args = new ValueConstraint[argStrings.length];
      for (int i = 0; i < args.length; i++) {
        args[i] = parseConstraint(argStrings[i]);
      }
      result.add(new MethodContract(args, parseConstraint(clause.substring(arrowIndex + arrow.length()))));
    }
    return result;
  }
  
  private static ValueConstraint parseConstraint(String name) throws ParseException {
    if (StringUtil.isEmpty(name)) throw new ParseException("Constraint should not be empty");
    if ("null".equals(name)) return ValueConstraint.NULL_VALUE;
    if ("!null".equals(name)) return ValueConstraint.NOT_NULL_VALUE;
    if ("true".equals(name)) return ValueConstraint.TRUE_VALUE;
    if ("false".equals(name)) return ValueConstraint.FALSE_VALUE;
    if ("exit".equals(name)) return ValueConstraint.SYSTEM_EXIT;
    if ("fail".equals(name)) return ValueConstraint.THROW_EXCEPTION;
    if ("_".equals(name)) return ValueConstraint.ANY_VALUE;
    throw new ParseException("Constraint should be one of: null, !null, true, false, exit, fail, _. Found: " + name);
  }
  
  private static class ParseException extends Exception {
    private ParseException(String message) {
      super(message);
    }
  }

  private static ValueConstraint[] getAnyArgConstraints(PsiExpression[] params) {
    ValueConstraint[] args = new ValueConstraint[params.length];
    for (int i = 0; i < args.length; i++) {
      args[i] = ValueConstraint.ANY_VALUE;
    }
    return args;
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
      for (PsiExpression ignored : dimensions) {
        addInstruction(new PopInstruction());
      }
      final PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
      if (arrayInitializer != null) {
        for (final PsiExpression initializer : arrayInitializer.getInitializers()) {
          initializer.accept(this);
          addInstruction(new PopInstruction());
        }
      }
      addInstruction(new MethodCallInstruction(expression, null));
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

      addInstruction(new MethodCallInstruction(expression, null));

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
      PsiVariable psiVariable = DfaValueFactory.resolveUnqualifiedVariable((PsiReferenceExpression)expression.getOperand());
      if (psiVariable != null) {
        DfaVariableValue dfaVariable = myFactory.getVarFactory().createVariableValue(psiVariable, false);
        addInstruction(new FlushVariableInstruction(dfaVariable));
      }
    }

    finishElement(expression);
  }

  @Override public void visitPrefixExpression(PsiPrefixExpression expression) {
    startElement(expression);

    DfaValue dfaValue = myFactory.createValue(expression);
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
        if (expression.getOperationTokenType() == JavaTokenType.EXCL) {
          addInstruction(new NotInstruction());
        }
        else {
          addInstruction(new PopInstruction());
          pushUnknown();

          if (operand instanceof PsiReferenceExpression) {
            PsiVariable psiVariable = DfaValueFactory.resolveUnqualifiedVariable((PsiReferenceExpression)operand);
            if (psiVariable != null) {
              DfaVariableValue dfaVariable = myFactory.getVarFactory().createVariableValue(psiVariable, false);
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

    final PsiExpression qualifierExpression = expression.getQualifierExpression();
    if (qualifierExpression != null) {
      qualifierExpression.accept(this);
      addInstruction(expression.resolve() instanceof PsiField ? new FieldReferenceInstruction(expression, null) : new PopInstruction());
    }

    boolean referenceRead = PsiUtil.isAccessedForReading(expression) && !PsiUtil.isAccessedForWriting(expression);
    addInstruction(new PushInstruction(getExpressionDfaValue(expression), expression, referenceRead));

    finishElement(expression);
  }

  @Nullable
  private DfaValue getExpressionDfaValue(PsiReferenceExpression expression) {
    DfaValue dfaValue = myFactory.createReferenceValue(expression);
    if (dfaValue instanceof DfaVariableValue) {
      DfaVariableValue dfaVariable = (DfaVariableValue)dfaValue;
      if (dfaVariable.getPsiVariable() instanceof PsiField) {
        myFields.add(dfaVariable);
      }
    }
    if (dfaValue == null) {
      PsiElement resolved = expression.resolve();
      if (resolved instanceof PsiField) {
        dfaValue = createDfaValueForAnotherInstanceMemberAccess(expression, (PsiField)resolved);
      }
    }
    return dfaValue;
  }

  @NotNull
  private DfaValue createDfaValueForAnotherInstanceMemberAccess(PsiReferenceExpression expression, PsiField field) {
    DfaValue dfaValue = null;
    if (expression.getQualifierExpression() != null) {
      dfaValue = createChainedVariableValue(expression);
    }
    if (dfaValue == null) {
      PsiType type = expression.getType();
      return myFactory.createTypeValueWithNullability(type, DfaPsiUtil.getElementNullability(type, field));
    }
    return dfaValue;
  }

  @Nullable
  private DfaVariableValue createChainedVariableValue(@Nullable PsiExpression expression) {
    if (expression instanceof PsiParenthesizedExpression) {
      return createChainedVariableValue(((PsiParenthesizedExpression)expression).getExpression());
    }

    PsiReferenceExpression refExpr;
    if (expression instanceof PsiMethodCallExpression) {
      refExpr = ((PsiMethodCallExpression)expression).getMethodExpression();
    }
    else if (expression instanceof PsiReferenceExpression) {
      refExpr = (PsiReferenceExpression)expression;
    }
    else {
      return null;
    }

    PsiElement target = refExpr.resolve();
    PsiVariable var = getAccessedVariable(target);
    if (var == null) {
      return null;
    }

    PsiMethod accessMethod = target instanceof PsiMethod ? (PsiMethod)target : null;
    PsiExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null) {
      DfaVariableValue result = myFactory.getVarFactory().createVariableValue(var, refExpr.getType(), false, null, accessMethod);
      if (var instanceof PsiField) {
        myFields.add(result);
      }
      return result;
    }

    if (DfaPsiUtil.isFinalField(var) || DfaPsiUtil.isPlainMutableField(var)) {
      DfaVariableValue qualifierValue = createChainedVariableValue(qualifier);
      if (qualifierValue != null) {
        return myFactory.getVarFactory().createVariableValue(var, refExpr.getType(), false, qualifierValue, accessMethod);
      }
    }
    return null;
  }

  @Nullable
  private static PsiVariable getAccessedVariable(final PsiElement target) {
    if (target instanceof PsiVariable) {
      return (PsiVariable)target;
    }
    if (target instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)target;
      if (PropertyUtil.isSimpleGetter(method)) {
        return PropertyUtil.getSimplyReturnedField(method, PropertyUtil.getSingleReturnValue(method));
      }
    }
    return null;
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

    DfaValue dfaValue = myFactory.createLiteralValue(expression);
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

    final PsiTypeElement typeElement = castExpression.getCastType();
    if (typeElement != null && operand != null) {
      addInstruction(new TypeCastInstruction(castExpression, operand, typeElement.getType()));
    }
    finishElement(castExpression);
  }

  @Override public void visitClass(PsiClass aClass) {
  }

}

class MethodContract {
  public final ValueConstraint[] arguments;
  public final ValueConstraint returnValue;

  public MethodContract(ValueConstraint[] arguments, ValueConstraint returnValue) {
    this.arguments = arguments;
    this.returnValue = returnValue;
  }

  public enum ValueConstraint {
    ANY_VALUE, NULL_VALUE, NOT_NULL_VALUE, TRUE_VALUE, FALSE_VALUE, THROW_EXCEPTION, SYSTEM_EXIT
  }
}

