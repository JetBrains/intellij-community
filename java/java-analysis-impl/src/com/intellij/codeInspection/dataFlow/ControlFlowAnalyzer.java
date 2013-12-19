/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.diagnostic.Logger;
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
import com.siyeh.ig.numeric.UnnecessaryExplicitNumericCastInspection;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInsight.ConditionChecker.Type.*;
import static com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint;
import static com.intellij.psi.CommonClassNames.*;

public class ControlFlowAnalyzer extends JavaElementVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer");
  public static final String ORG_JETBRAINS_ANNOTATIONS_CONTRACT = Contract.class.getName();
  private boolean myIgnoreAssertions;

  private static class CannotAnalyzeException extends RuntimeException { }

  private final DfaValueFactory myFactory;
  private ControlFlow myCurrentFlow;
  private Set<DfaVariableValue> myFields;
  private Stack<CatchDescriptor> myCatchStack;
  private DfaValue myRuntimeException;
  private DfaValue myError;
  private DfaValue myString;
  private PsiType myNpe;
  private PsiType myAssertionError;
  private Stack<PsiElement> myElementStack = new Stack<PsiElement>();

  /**
   * A mock variable for try-related control transfers. Contains exceptions or an (Throwable-inconvertible) string to indicate return inside finally
   */
  private DfaVariableValue myExceptionHolder;

  ControlFlowAnalyzer(final DfaValueFactory valueFactory) {
    myFactory = valueFactory;
  }

  public ControlFlow buildControlFlow(@NotNull PsiElement codeFragment, boolean ignoreAssertions) {
    myIgnoreAssertions = ignoreAssertions;
    PsiManager manager = codeFragment.getManager();
    GlobalSearchScope scope = codeFragment.getResolveScope();
    myRuntimeException = myFactory.createTypeValue(createClassType(manager, scope, JAVA_LANG_RUNTIME_EXCEPTION), Nullness.NOT_NULL);
    myError = myFactory.createTypeValue(createClassType(manager, scope, JAVA_LANG_ERROR), Nullness.NOT_NULL);
    myNpe = createClassType(manager, scope, JAVA_LANG_NULL_POINTER_EXCEPTION);
    myAssertionError = createClassType(manager, scope, JAVA_LANG_ASSERTION_ERROR);
    myString = myFactory.createTypeValue(createClassType(manager, scope, JAVA_LANG_STRING), Nullness.NOT_NULL);
    
    PsiParameter mockVar = JavaPsiFacade.getElementFactory(manager.getProject()).createParameterFromText("java.lang.Object $exception$", null);
    myExceptionHolder = myFactory.getVarFactory().createVariableValue(mockVar, false);
    
    myFields = new HashSet<DfaVariableValue>();
    myCatchStack = new Stack<CatchDescriptor>();
    myCurrentFlow = new ControlFlow(myFactory);

    try {
      codeFragment.accept(this);
    }
    catch (CannotAnalyzeException e) {
      return null;
    }

    PsiElement parent = codeFragment.getParent();
    if (parent instanceof PsiLambdaExpression && codeFragment instanceof PsiExpression) {
      addInstruction(new CheckReturnValueInstruction(codeFragment));
    }
    myCurrentFlow.setFields(myFields.toArray(new DfaVariableValue[myFields.size()]));

    addInstruction(new ReturnInstruction(false));

    return myCurrentFlow;
  }

  private static PsiClassType createClassType(PsiManager manager, GlobalSearchScope scope, String fqn) {
    PsiClass aClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(fqn, scope);
    if (aClass != null) return JavaPsiFacade.getElementFactory(manager.getProject()).createType(aClass);
    return JavaPsiFacade.getElementFactory(manager.getProject()).createTypeByFQClassName(fqn, scope);
  }

  private <T extends Instruction> T addInstruction(T i) {
    myCurrentFlow.addInstruction(i);
    return i;
  }

  private ControlFlow.ControlFlowOffset getEndOffset(PsiElement element) {
    return myCurrentFlow.getEndOffset(element);
  }

  private ControlFlow.ControlFlowOffset getStartOffset(PsiElement element) {
    return myCurrentFlow.getStartOffset(element);
  }

  private void startElement(PsiElement element) {
    myCurrentFlow.startElement(element);
    myElementStack.push(element);
  }

  private void finishElement(PsiElement element) {
    myCurrentFlow.finishElement(element);
    PsiElement popped = myElementStack.pop();
    if (element != popped) {
      throw new AssertionError("Expected " + element + ", popped " + popped);
    }
  }

  @Override
  public void visitErrorElement(PsiErrorElement element) {
    throw new CannotAnalyzeException();
  }

  @Override public void visitAssignmentExpression(PsiAssignmentExpression expression) {
    PsiExpression lExpr = expression.getLExpression();
    PsiExpression rExpr = expression.getRExpression();

    startElement(expression);
    if (rExpr == null) {
      pushUnknown();
      finishElement(expression);
      return;
    }

    IElementType op = expression.getOperationTokenType();
    PsiType type = expression.getType();
    boolean isBoolean = PsiType.BOOLEAN.equals(type);
    if (op == JavaTokenType.EQ) {
      lExpr.accept(this);
      rExpr.accept(this);
      generateBoxingUnboxingInstructionFor(rExpr, type);
    }
    else if (op == JavaTokenType.ANDEQ) {
      if (isBoolean) {
        generateBooleanAssignmentExpression(true, lExpr, rExpr, type);
      }
      else {
        generateDefaultAssignmentBinOp(lExpr, rExpr, type);
      }
    }
    else if (op == JavaTokenType.OREQ) {
      if (isBoolean) {
        generateBooleanAssignmentExpression(false, lExpr, rExpr, type);
      }
      else {
        generateDefaultAssignmentBinOp(lExpr, rExpr, type);
      }
    }
    else if (op == JavaTokenType.XOREQ) {
      if (isBoolean) {
        generateXorExpression(expression, new PsiExpression[]{lExpr, rExpr}, type, true);
      }
      else {
        generateDefaultAssignmentBinOp(lExpr, rExpr, type);
      }
    }
    else if (op == JavaTokenType.PLUSEQ && type != null && type.equalsToText(JAVA_LANG_STRING)) {
      lExpr.accept(this);
      addInstruction(new DupInstruction());
      rExpr.accept(this);
      addInstruction(new BinopInstruction(JavaTokenType.PLUS, null, lExpr.getProject()));
    }
    else {
      generateDefaultAssignmentBinOp(lExpr, rExpr, type);
    }

    addInstruction(new AssignInstruction(rExpr));
    finishElement(expression);
  }

  private void generateDefaultAssignmentBinOp(PsiExpression lExpr, PsiExpression rExpr, final PsiType exprType) {
    lExpr.accept(this);
    addInstruction(new DupInstruction());
    generateBoxingUnboxingInstructionFor(lExpr,exprType);
    rExpr.accept(this);
    generateBoxingUnboxingInstructionFor(rExpr, exprType);
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
      
      initException(myAssertionError);
      addThrowCode(false);
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
      statement.accept(this);
    }

    flushCodeBlockVariables(block);

    finishElement(block);
  }

  private void flushCodeBlockVariables(PsiCodeBlock block) {
    for (PsiStatement statement : block.getStatements()) {
      if (statement instanceof PsiDeclarationStatement) {
        for (PsiElement declaration : ((PsiDeclarationStatement)statement).getDeclaredElements()) {
          if (declaration instanceof PsiVariable) {
            myCurrentFlow.removeVariable((PsiVariable)declaration);
          }
        }
      }
    }
    PsiElement parent = block.getParent();
    if (parent instanceof PsiCatchSection) {
      myCurrentFlow.removeVariable(((PsiCatchSection)parent).getParameter());
    }
    else if (parent instanceof PsiForeachStatement) {
      myCurrentFlow.removeVariable(((PsiForeachStatement)parent).getIterationParameter());
    }
    else if (parent instanceof PsiForStatement) {
      PsiStatement statement = ((PsiForStatement)parent).getInitialization();
      if (statement instanceof PsiDeclarationStatement) {
        for (PsiElement declaration : ((PsiDeclarationStatement)statement).getDeclaredElements()) {
          if (declaration instanceof PsiVariable) {
            myCurrentFlow.removeVariable((PsiVariable)declaration);
          }
        }
      }
    }
    else if (parent instanceof PsiTryStatement) {
      PsiResourceList list = ((PsiTryStatement)parent).getResourceList();
      if (list != null) {
        for (PsiResourceVariable variable : list.getResourceVariables()) {
          myCurrentFlow.removeVariable(variable);
        }
      }
    }
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
      flushVariablesOnControlTransfer(exitedStatement);
      addInstruction(new GotoInstruction(getEndOffset(exitedStatement)));
    }

    finishElement(statement);
  }

  @Override public void visitContinueStatement(PsiContinueStatement statement) {
    startElement(statement);
    PsiStatement continuedStatement = statement.findContinuedStatement();
    if (continuedStatement instanceof PsiLoopStatement) {
      PsiStatement body = ((PsiLoopStatement)continuedStatement).getBody();
      flushVariablesOnControlTransfer(body);
      addInstruction(new GotoInstruction(getEndOffset(body)));
    } else {
      addInstruction(new EmptyInstruction(null));
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

    ControlFlow.ControlFlowOffset offset = myCurrentFlow.getNextOffset();
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
        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          visitElement(expression);
        }

        @Override
        public void visitDeclarationStatement(PsiDeclarationStatement statement) {
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
      addInstruction(new PushInstruction(statement.getRParenth() == null ? null : myFactory.getConstFactory().getTrue(), null));
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

    ControlFlow.ControlFlowOffset offset = initialization != null
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

    ControlFlow.ControlFlowOffset offset = elseStatement != null
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

  @Override
  public void visitLambdaExpression(PsiLambdaExpression expression) {
    startElement(expression);
    DfaValue dfaValue = myFactory.createValue(expression);
    addInstruction(new PushInstruction(dfaValue, expression));
    addInstruction(new LambdaInstruction(expression));
    finishElement(expression);
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
      addInstruction(new CheckReturnValueInstruction(returnValue));
    }

    returnCheckingFinally();
    finishElement(statement);
  }

  private void returnCheckingFinally() {
    ControlFlow.ControlFlowOffset finallyOffset = getFinallyOffset();
    if (finallyOffset != null) {
      addInstruction(new PushInstruction(myExceptionHolder, null));
      addInstruction(new PushInstruction(myString, null));
      addInstruction(new AssignInstruction(null));
      addInstruction(new PopInstruction());
      
      addInstruction(new GotoInstruction(finallyOffset));
    } else {
      addInstruction(new ReturnInstruction(false));
    }
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
              ControlFlow.ControlFlowOffset offset = getStartOffset(statement);
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
        ControlFlow.ControlFlowOffset offset = defaultLabel != null ? getStartOffset(defaultLabel) : getEndOffset(body);
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
      if (myCatchStack.isEmpty()) {
        addInstruction(new ReturnInstruction(true));
        finishElement(statement);
        return;
      }
      
      addConditionalRuntimeThrow();
      addInstruction(new DupInstruction());
      addInstruction(new PushInstruction(myFactory.getConstFactory().getNull(), null));
      addInstruction(new BinopInstruction(JavaTokenType.EQEQ, null, statement.getProject()));
      ConditionalGotoInstruction gotoInstruction = new ConditionalGotoInstruction(null, true, null);
      addInstruction(gotoInstruction);
      
      addInstruction(new PopInstruction());
      initException(myNpe);
      addThrowCode(false);

      gotoInstruction.setOffset(myCurrentFlow.getInstructionCount());
      addInstruction(new PushInstruction(myExceptionHolder, null));
      addInstruction(new SwapInstruction());
      addInstruction(new AssignInstruction(null));
      addInstruction(new PopInstruction());
      addThrowCode(false);
    }

    finishElement(statement);
  }

  private void addConditionalRuntimeThrow() {
    if (myCatchStack.isEmpty()) {
      return;
    }
    
    pushUnknown();
    final ConditionalGotoInstruction ifNoException = addInstruction(new ConditionalGotoInstruction(null, false, null));
    addInstruction(new EmptyStackInstruction());

    addInstruction(new PushInstruction(myExceptionHolder, null));

    pushUnknown();
    final ConditionalGotoInstruction ifError = addInstruction(new ConditionalGotoInstruction(null, false, null));
    addInstruction(new PushInstruction(myRuntimeException, null));
    GotoInstruction ifRuntime = addInstruction(new GotoInstruction(null));
    ifError.setOffset(myCurrentFlow.getInstructionCount());
    addInstruction(new PushInstruction(myError, null));
    ifRuntime.setOffset(myCurrentFlow.getInstructionCount());

    addInstruction(new AssignInstruction(null));
    addInstruction(new PopInstruction());
    
    addThrowCode(false);

    ifNoException.setOffset(myCurrentFlow.getInstructionCount());
  }

  private void flushVariablesOnControlTransfer(PsiElement stopWhenAncestorOf) {
    for (int i = myElementStack.size() - 1; i >= 0; i--) {
      PsiElement scope = myElementStack.get(i);
      if (PsiTreeUtil.isAncestor(scope, stopWhenAncestorOf, true)) {
        break;
      }
      if (scope instanceof PsiCodeBlock) {
        flushCodeBlockVariables((PsiCodeBlock)scope);
      }
    }
  }

  // the exception object should be in $exception$ variable
  private void addThrowCode(boolean catchRethrow) {
    if (myCatchStack.isEmpty()) {
      addInstruction(new ReturnInstruction(true));
      return;
    }
    
    PsiElement currentElement = myElementStack.peek();
    
    CatchDescriptor cd = myCatchStack.get(myCatchStack.size() - 1);
    if (!cd.isFinally() && PsiTreeUtil.isAncestor(cd.getBlock().getParent(), currentElement, false)) {
      int i = myCatchStack.size() - 2;
      while (!catchRethrow && i >= 0 && !myCatchStack.get(i).isFinally() && myCatchStack.get(i).getTryStatement() == cd.getTryStatement()) {
        i--;
      }
      if (i < 0) {
        addInstruction(new ReturnInstruction(true));
        return;
      }
      cd = myCatchStack.get(i);
    }

    flushVariablesOnControlTransfer(cd.getBlock());
    addInstruction(new GotoInstruction(cd.getJumpOffset(this)));
  }

  @Nullable
  private ControlFlow.ControlFlowOffset getFinallyOffset() {
    for (int i = myCatchStack.size() - 1; i >= 0; i--) {
      CatchDescriptor cd = myCatchStack.get(i);
      if (cd.isFinally()) return cd.getJumpOffset(this);
    }

    return null;
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

    public PsiCodeBlock getBlock() {
      return myBlock;
    }
    public PsiTryStatement getTryStatement() {
      return (PsiTryStatement) (isFinally() ? myBlock.getParent() : myBlock.getParent().getParent());
    }

    public PsiType getType() {
      return myType;
    }

    public boolean isFinally() {
      return myIsFinally;
    }

    public ControlFlow.ControlFlowOffset getJumpOffset(ControlFlowAnalyzer analyzer) {
      return analyzer.getStartOffset(isFinally() ? myBlock : myBlock.getParent());
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

    PsiCatchSection[] sections = statement.getCatchSections();
    for (int i = sections.length - 1; i >= 0; i--) {
      PsiCatchSection section = sections[i];
      PsiCodeBlock catchBlock = section.getCatchBlock();
      PsiParameter parameter = section.getParameter();
      if (parameter != null && catchBlock != null) {
        PsiType type = parameter.getType();
        if (type instanceof PsiClassType || type instanceof PsiDisjunctionType) {
          myCatchStack.push(new CatchDescriptor(parameter, catchBlock));
          continue;
        }
      }
      throw new CannotAnalyzeException();
    }

    ControlFlow.ControlFlowOffset endOffset = finallyBlock == null ? getEndOffset(statement) : getStartOffset(finallyBlock);

    if (resourceList != null) {
      resourceList.accept(this);
    }

    if (tryBlock != null) {
      tryBlock.accept(this);
    }

    addInstruction(new GotoInstruction(endOffset));

    for (PsiCatchSection section : sections) {
      section.accept(this);
      addInstruction(new GotoInstruction(endOffset));
      myCatchStack.pop();
    }

    if (finallyBlock != null) {
      myCatchStack.pop();
      finallyBlock.accept(this);
      
      //if $exception$==null => continue normal execution
      addInstruction(new PushInstruction(myExceptionHolder, null));
      addInstruction(new PushInstruction(myFactory.getConstFactory().getNull(), null));
      addInstruction(new BinopInstruction(JavaTokenType.EQEQ, null, statement.getProject()));
      addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), false, null));
      
      // else throw $exception$
      addThrowCode(false);
    }

    finishElement(statement);
  }

  @Override
  public void visitCatchSection(PsiCatchSection section) {
    startElement(section);
    PsiCodeBlock catchBlock = section.getCatchBlock();
    if (catchBlock != null) {
      // exception is in myExceptionHolder mock variable
      // check if it's assignable to catch parameter type
      PsiType declaredType = section.getCatchType();
      List<PsiType> flattened = declaredType instanceof PsiDisjunctionType ? 
                                ((PsiDisjunctionType)declaredType).getDisjunctions() : 
                                ContainerUtil.createMaybeSingletonList(declaredType);
      for (PsiType catchType : flattened) {
        addInstruction(new PushInstruction(myExceptionHolder, null));
        addInstruction(new PushInstruction(myFactory.createTypeValue(catchType, Nullness.UNKNOWN), null));
        addInstruction(new BinopInstruction(JavaTokenType.INSTANCEOF_KEYWORD, null, section.getProject()));
        addInstruction(new ConditionalGotoInstruction(ControlFlow.deltaOffset(getStartOffset(catchBlock), -5), false, null));
      }
      
      // not assignable => rethrow 
      addThrowCode(true);

      // e = $exception$
      addInstruction(new PushInstruction(myFactory.getVarFactory().createVariableValue(section.getParameter(), false), null));
      addInstruction(new PushInstruction(myExceptionHolder, null));
      addInstruction(new AssignInstruction(null));
      addInstruction(new PopInstruction());
      
      addInstruction(new FlushVariableInstruction(myExceptionHolder));
      
      catchBlock.accept(this);
    }
    finishElement(section);
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

    DfaValue dfaValue = myFactory.createValue(expression);
    if (dfaValue != null) {
      addInstruction(new PushInstruction(dfaValue, expression));
      finishElement(expression);
      return;
    }
    IElementType op = expression.getOperationTokenType();

    PsiExpression[] operands = expression.getOperands();
    if (operands.length <= 1) {
      pushUnknown();
      finishElement(expression);
      return;
    }
    PsiType type = expression.getType();
    if (op == JavaTokenType.ANDAND) {
      generateAndExpression(operands, type, true);
    }
    else if (op == JavaTokenType.OROR) {
      generateOrExpression(operands, type, true);
    }
    else if (op == JavaTokenType.XOR && PsiType.BOOLEAN.equals(type)) {
      generateXorExpression(expression, operands, type, false);
    }
    else if (op == JavaTokenType.AND && PsiType.BOOLEAN.equals(type)) {
      generateAndExpression(operands, type, false);
    }
    else if (op == JavaTokenType.OR && PsiType.BOOLEAN.equals(type)) {
      generateOrExpression(operands, type, false);
    }
    else {
      generateOther(expression, op, operands, type);
    }
    finishElement(expression);
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
      addConditionalRuntimeThrow();
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

  private void generateXorExpression(PsiExpression expression, PsiExpression[] operands, final PsiType exprType, boolean forAssignment) {
    PsiExpression operand = operands[0];
    operand.accept(this);
    if (forAssignment) {
      addInstruction(new DupInstruction());
    }
    generateBoxingUnboxingInstructionFor(operand, exprType);
    for (int i = 1; i < operands.length; i++) {
      operand = operands[i];
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(operand, exprType);
      PsiElement psiAnchor = expression.isPhysical() ? expression : null;
      addInstruction(new BinopInstruction(JavaTokenType.NE, psiAnchor, expression.getProject()));
    }
  }

  private void generateOrExpression(PsiExpression[] operands, final PsiType exprType, boolean shortCircuit) {
    for (int i = 0; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(operand, exprType);
      if (!shortCircuit) {
        if (i > 0) {
          combineStackBooleans(false, operand);
        }
        continue;
      }

      PsiExpression nextOperand = i == operands.length - 1 ? null : operands[i + 1];
      if (nextOperand != null) {
        addInstruction(new ConditionalGotoInstruction(getStartOffset(nextOperand), true, operand));
        addInstruction(new PushInstruction(myFactory.getConstFactory().getTrue(), null));
        addInstruction(new GotoInstruction(getEndOffset(operands[operands.length - 1])));
      }
    }
  }

  private void generateBooleanAssignmentExpression(boolean and, PsiExpression lExpression, PsiExpression rExpression, PsiType exprType) {
    lExpression.accept(this);
    addInstruction(new DupInstruction());
    generateBoxingUnboxingInstructionFor(lExpression, exprType);

    rExpression.accept(this);
    generateBoxingUnboxingInstructionFor(rExpression, exprType);

    lExpression.accept(this);
    generateBoxingUnboxingInstructionFor(lExpression, exprType);

    combineStackBooleans(and, lExpression);
  }

  private void combineStackBooleans(boolean and, PsiExpression anchor) {
    ConditionalGotoInstruction toPopAndPushSuccess = new ConditionalGotoInstruction(null, and, anchor);
    addInstruction(toPopAndPushSuccess);
    GotoInstruction overPushSuccess = new GotoInstruction(null);
    addInstruction(overPushSuccess);

    PopInstruction pop = new PopInstruction();
    addInstruction(pop);
    DfaConstValue constValue = and ? myFactory.getConstFactory().getFalse() : myFactory.getConstFactory().getTrue();
    PushInstruction pushSuccess = new PushInstruction(constValue, null);
    addInstruction(pushSuccess);

    toPopAndPushSuccess.setOffset(pop.getIndex());
    overPushSuccess.setOffset(pushSuccess.getIndex() + 1);
  }

  private void generateAndExpression(PsiExpression[] operands, final PsiType exprType, boolean shortCircuit) {
    List<ConditionalGotoInstruction> branchToFail = new ArrayList<ConditionalGotoInstruction>();
    for (int i = 0; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(operand, exprType);

      if (!shortCircuit) {
        if (i > 0) {
          combineStackBooleans(true, operand);
        }
        continue;
      }

      ConditionalGotoInstruction onFail = new ConditionalGotoInstruction(null, true, operand);
      branchToFail.add(onFail);
      addInstruction(onFail);
    }

    if (!shortCircuit) {
      return;
    }

    addInstruction(new PushInstruction(myFactory.getConstFactory().getTrue(), null));
    GotoInstruction toSuccess = new GotoInstruction(null);
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

    final ControlFlow.ControlFlowOffset elseOffset = elseExpression == null ? ControlFlow.deltaOffset(getEndOffset(expression), -1) : getStartOffset(elseExpression);
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
      addInstruction(new PushInstruction(myFactory.createTypeValue(type, Nullness.UNKNOWN), null));
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
        ConditionalGotoInstruction cond = new ConditionalGotoInstruction(null, false, null);
        addInstruction(cond);
        addInstruction(new EmptyStackInstruction());
        initException(ref);
        addThrowCode(false);
        cond.setOffset(myCurrentFlow.getInstructionCount());
      }
    }
  }

  private void initException(PsiType ref) {
    addInstruction(new PushInstruction(myExceptionHolder, null));
    addInstruction(new PushInstruction(myFactory.createTypeValue(ref, Nullness.NOT_NULL), null));
    addInstruction(new AssignInstruction(null));
    addInstruction(new PopInstruction());
  }

  @Override public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    startElement(expression);

    if (handleContracts(expression, getCallContracts(expression))) {
      finishElement(expression);
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

    addConditionalRuntimeThrow();
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
    finishElement(expression);
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
    for (int i = 0; i < contracts.size(); i++) {
      handleContract(expression, contracts.get(i), contracts.size() - 1 - i);
    }
    pushUnknownReturnValue(expression); // goto here if all contracts are false
    return true;
  }
  
  private void handleContract(PsiMethodCallExpression expression, MethodContract contract, int remainingContracts) {
    PsiExpression[] args = expression.getArgumentList().getExpressions();

    final ControlFlow.ControlFlowOffset exitPoint = getEndOffset(expression);

    List<GotoInstruction> gotoContractFalse = new SmartList<GotoInstruction>();
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
      ConditionalGotoInstruction continueCheckingContract = addInstruction(new ConditionalGotoInstruction(null, !expectingTrueOnStack, null));

      for (int j = 0; j < i; j++) {
        addInstruction(new PopInstruction());
      }
      gotoContractFalse.add(addInstruction(new GotoInstruction(null)));
      continueCheckingContract.setOffset(myCurrentFlow.getInstructionCount());
    }

    for (int j = 0; j < remainingContracts * args.length; j++) {
      addInstruction(new PopInstruction());
    }

    // if contract is true
    switch (contract.returnValue) {
      case ANY_VALUE:
        pushUnknownReturnValue(expression);
        addInstruction(new GotoInstruction(exitPoint));
        break;
      case NULL_VALUE:
        addInstruction(new PushInstruction(myFactory.getConstFactory().getNull(), null));
        addInstruction(new GotoInstruction(exitPoint));
        break;
      case NOT_NULL_VALUE:
        PsiType type = expression.getType();
        addInstruction(new PushInstruction(myFactory.createTypeValue(type, Nullness.NOT_NULL), null));
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
        returnCheckingFinally();
        break;
    }

    // if contract is false
    for (GotoInstruction instruction : gotoContractFalse) {
      instruction.setOffset(myCurrentFlow.getInstructionCount());
    }
  }

  private void pushUnknownReturnValue(PsiMethodCallExpression expression) {
    PsiMethod method = expression.resolveMethod();
    if (method != null) {
      PsiType type = expression.getType();
      addInstruction(new PushInstruction(myFactory.createTypeValue(type, DfaPsiUtil.getElementNullability(type, method)), null));
    }
    else {
      pushUnknown();
    }
  }

  private static List<MethodContract> getCallContracts(PsiMethodCallExpression expression) {
    PsiMethod resolved = expression.resolveMethod();
    if (resolved != null) {
      final PsiAnnotation contractAnno = findContractAnnotation(resolved);
      if (contractAnno != null) {
        return CachedValuesManager.getCachedValue(contractAnno, new CachedValueProvider<List<MethodContract>>() {
          @Nullable
          @Override
          public Result<List<MethodContract>> compute() {
            String text = AnnotationUtil.getStringAttributeValue(contractAnno, null);
            if (text != null) {
              try {
                return Result.create(parseContract(text), contractAnno);
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
            return Collections.singletonList(new MethodContract(getAnyArgConstraints(params), ValueConstraint.THROW_EXCEPTION));
          }
        }
        else if ("junit.framework.Assert".equals(className) || "org.junit.Assert".equals(className) ||
                 "junit.framework.TestCase".equals(className) || "org.testng.Assert".equals(className) || "org.testng.AssertJUnit".equals(className)) {
          boolean testng = className.startsWith("org.testng.");
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
        } else if (type == IS_NOT_NULL_METHOD || type == IS_NULL_METHOD) {
          constraints[checkedParam] = ValueConstraint.NULL_VALUE;
          return Collections.singletonList(new MethodContract(constraints, type == IS_NULL_METHOD ? ValueConstraint.TRUE_VALUE : ValueConstraint.FALSE_VALUE));
        } else { //assertTrue or assertFalse
          constraints[checkedParam] = type == ASSERT_FALSE_METHOD ? ValueConstraint.TRUE_VALUE : ValueConstraint.FALSE_VALUE;
          return Collections.singletonList(new MethodContract(constraints, ValueConstraint.THROW_EXCEPTION));
        }
      }
    }

    return Collections.emptyList();
  }

  public static PsiAnnotation findContractAnnotation(PsiMethod method) {
    return AnnotationUtil.findAnnotation(method, ORG_JETBRAINS_ANNOTATIONS_CONTRACT);
  }

  public static List<MethodContract> parseContract(String text) throws ParseException {
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
    if ("fail".equals(name)) return ValueConstraint.THROW_EXCEPTION;
    if ("_".equals(name)) return ValueConstraint.ANY_VALUE;
    throw new ParseException("Constraint should be one of: null, !null, true, false, fail, _. Found: " + name);
  }
  
  public static class ParseException extends Exception {
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
      dfaValue = myFactory.createTypeValue(type, Nullness.UNKNOWN);
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
      addConditionalRuntimeThrow();
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

      addConditionalRuntimeThrow();
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
      return myFactory.createTypeValue(type, DfaPsiUtil.getElementNullability(type, field));
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
    PsiModifierListOwner var = getAccessedVariable(target);
    if (var == null) {
      return null;
    }

    PsiExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null) {
      DfaVariableValue result = myFactory.getVarFactory().createVariableValue(var, refExpr.getType(), false, null);
      if (var instanceof PsiField) {
        myFields.add(result);
      }
      return result;
    }

    if (!(var instanceof PsiField) || !var.hasModifierProperty(PsiModifier.TRANSIENT) && !var.hasModifierProperty(PsiModifier.VOLATILE)) {
      DfaVariableValue qualifierValue = createChainedVariableValue(qualifier);
      if (qualifierValue != null) {
        return myFactory.getVarFactory().createVariableValue(var, refExpr.getType(), false, qualifierValue);
      }
    }
    return null;
  }

  @Nullable
  private static PsiModifierListOwner getAccessedVariable(final PsiElement target) {
    if (target instanceof PsiVariable) {
      return (PsiVariable)target;
    }
    if (target instanceof PsiMethod) {
      if (PropertyUtil.isSimplePropertyGetter((PsiMethod)target)) {
        return (PsiMethod)target;
      }
    }
    return null;
  }

  @Override public void visitSuperExpression(PsiSuperExpression expression) {
    startElement(expression);
    addInstruction(new PushInstruction(myFactory.createTypeValue(expression.getType(), Nullness.NOT_NULL), null));
    finishElement(expression);
  }

  @Override public void visitThisExpression(PsiThisExpression expression) {
    startElement(expression);
    addInstruction(new PushInstruction(myFactory.createTypeValue(expression.getType(), Nullness.NOT_NULL), null));
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
    if (typeElement != null && operand != null && operand.getType() != null) {
      if (typeElement.getType() instanceof PsiPrimitiveType &&
          UnnecessaryExplicitNumericCastInspection.isPrimitiveNumericCastNecessary(castExpression)) {
        addInstruction(new PopInstruction());
        pushUnknown();
      } else {
        addInstruction(new TypeCastInstruction(castExpression, operand, typeElement.getType()));
      }
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
    ANY_VALUE, NULL_VALUE, NOT_NULL_VALUE, TRUE_VALUE, FALSE_VALUE, THROW_EXCEPTION
  }
}

