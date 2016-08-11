/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.Stack;
import com.siyeh.ig.numeric.UnnecessaryExplicitNumericCastInspection;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.CommonClassNames.*;

public class ControlFlowAnalyzer extends JavaElementVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer");
  public static final String ORG_JETBRAINS_ANNOTATIONS_CONTRACT = Contract.class.getName();
  private final PsiElement myCodeFragment;
  private boolean myIgnoreAssertions;
  private final Project myProject;

  private static class CannotAnalyzeException extends RuntimeException { }

  private final DfaValueFactory myFactory;
  private ControlFlow myCurrentFlow;
  private Stack<CatchDescriptor> myCatchStack;
  private final DfaValue myRuntimeException;
  private final DfaValue myError;
  private final DfaValue myString;
  private final PsiType myNpe;
  private final PsiType myAssertionError;
  private final Stack<PsiElement> myElementStack = new Stack<>();

  /**
   * Variables for try-related control transfers. Contain exceptions or an (Throwable-inconvertible) string to indicate return inside finally
   */
  private FactoryMap<PsiTryStatement, DfaVariableValue> myExceptionHolders;

  ControlFlowAnalyzer(final DfaValueFactory valueFactory, @NotNull PsiElement codeFragment, boolean ignoreAssertions) {
    myFactory = valueFactory;
    myCodeFragment = codeFragment;
    myProject = codeFragment.getProject();
    myIgnoreAssertions = ignoreAssertions;
    GlobalSearchScope scope = codeFragment.getResolveScope();
    myRuntimeException = myFactory.createTypeValue(createClassType(scope, JAVA_LANG_RUNTIME_EXCEPTION), Nullness.NOT_NULL);
    myError = myFactory.createTypeValue(createClassType(scope, JAVA_LANG_ERROR), Nullness.NOT_NULL);
    myNpe = createClassType(scope, JAVA_LANG_NULL_POINTER_EXCEPTION);
    myAssertionError = createClassType(scope, JAVA_LANG_ASSERTION_ERROR);
    myString = myFactory.createTypeValue(createClassType(scope, JAVA_LANG_STRING), Nullness.NOT_NULL);

    myExceptionHolders = new FactoryMap<PsiTryStatement, DfaVariableValue>() {
      @Nullable
      @Override
      protected DfaVariableValue create(PsiTryStatement key) {
        String text = "java.lang.Object $exception" + myExceptionHolders.size() + "$";
        PsiParameter mockVar = JavaPsiFacade.getElementFactory(myProject).createParameterFromText(text, null);
        return myFactory.getVarFactory().createVariableValue(mockVar, false);
      }
    };
  }

  @Nullable
  public ControlFlow buildControlFlow() {
    myCatchStack = new Stack<>();
    myCurrentFlow = new ControlFlow(myFactory);
    try {
      myCodeFragment.accept(this);
    }
    catch (CannotAnalyzeException e) {
      return null;
    }

    PsiElement parent = myCodeFragment.getParent();
    if (parent instanceof PsiLambdaExpression && myCodeFragment instanceof PsiExpression) {
      generateBoxingUnboxingInstructionFor((PsiExpression)myCodeFragment,
                                           LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)parent));
      addInstruction(new CheckReturnValueInstruction(myCodeFragment));
    }

    addInstruction(new ReturnInstruction(false, null));

    if (Registry.is("idea.dfa.live.variables.analysis")) {
      new LiveVariablesAnalyzer(myCurrentFlow, myFactory).flushDeadVariablesOnStatementFinish();
    }

    return myCurrentFlow;
  }


  private PsiClassType createClassType(GlobalSearchScope scope, String fqn) {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(fqn, scope);
    if (aClass != null) return JavaPsiFacade.getElementFactory(myProject).createType(aClass);
    return JavaPsiFacade.getElementFactory(myProject).createTypeByFQClassName(fqn, scope);
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
    if (element instanceof PsiStatement && !(element instanceof PsiReturnStatement)) {
      addInstruction(new FinishElementInstruction(element));
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
      addInstruction(new BinopInstruction(JavaTokenType.PLUS, null, myProject));
    }
    else {
      generateDefaultAssignmentBinOp(lExpr, rExpr, type);
    }

    addInstruction(new AssignInstruction(rExpr, myFactory.createValue(lExpr)));

    flushArrayElementsOnUnknownIndexAssignment(lExpr);

    finishElement(expression);
  }

  private void flushArrayElementsOnUnknownIndexAssignment(PsiExpression lExpr) {
    if (lExpr instanceof PsiArrayAccessExpression &&
        !(myFactory.createValue(lExpr) instanceof DfaVariableValue) // check for unknown index, otherwise AssignInstruction will flush only that element
      ) {
      DfaValue arrayVar = myFactory.createValue(((PsiArrayAccessExpression)lExpr).getArrayExpression());
      if (arrayVar instanceof DfaVariableValue) {
        addInstruction(new FlushVariableInstruction((DfaVariableValue)arrayVar, true));
      }
    }
  }

  private void generateDefaultAssignmentBinOp(PsiExpression lExpr, PsiExpression rExpr, final PsiType exprType) {
    lExpr.accept(this);
    addInstruction(new DupInstruction());
    generateBoxingUnboxingInstructionFor(lExpr,exprType);
    rExpr.accept(this);
    generateBoxingUnboxingInstructionFor(rExpr, exprType);
    addInstruction(new BinopInstruction(null, null, myProject));
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

      CatchDescriptor cd = findNextCatch(false);
      initException(myAssertionError, cd);
      addThrowCode(cd, statement);
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
    addInstruction(new AssignInstruction(initializer, dfaVariable));
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
        for (PsiResourceListElement resource : list) {
          if (resource instanceof PsiResourceVariable) {
            myCurrentFlow.removeVariable((PsiVariable)resource);
          }
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
    final ArrayList<PsiElement> declaredVariables = new ArrayList<>();

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
      PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, true, PsiMember.class, PsiLambdaExpression.class);
      if (method != null) {
        generateBoxingUnboxingInstructionFor(returnValue, method.getReturnType());
      }
      else {
        final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(statement, PsiLambdaExpression.class, true, PsiMember.class);
        if (lambdaExpression != null) {
          generateBoxingUnboxingInstructionFor(returnValue, LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression));
        }
      }
      addInstruction(new CheckReturnValueInstruction(returnValue));
    }

    returnCheckingFinally(false, statement);
    finishElement(statement);
  }

  private void returnCheckingFinally(boolean viaException, @NotNull PsiElement anchor) {
    CatchDescriptor finallyDescriptor = findFinally();
    if (finallyDescriptor != null) {
      addInstruction(new PushInstruction(getExceptionHolder(finallyDescriptor), null));
      addInstruction(new PushInstruction(myString, null));
      addInstruction(new AssignInstruction(null, null));
      addInstruction(new PopInstruction());
      
      addInstruction(new GotoInstruction(finallyDescriptor.getJumpOffset(this)));
    } else {
      addInstruction(new ReturnInstruction(viaException, anchor));
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
      if (psiClass != null) {
        addInstruction(new FieldReferenceInstruction(caseExpression, "switch statement expression"));
        if (psiClass.isEnum()) {
          enumValues = new HashSet<>();
          for (PsiField f : psiClass.getFields()) {
            if (f instanceof PsiEnumConstant) {
              enumValues.add((PsiEnumConstant)f);
            }
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

              if (enumValues != null && caseValue instanceof PsiReferenceExpression) {
                //noinspection SuspiciousMethodCalls
                enumValues.remove(((PsiReferenceExpression)caseValue).resolve());
              }

              boolean alwaysTrue = enumValues != null && enumValues.isEmpty();
              if (alwaysTrue) {
                addInstruction(new PushInstruction(myFactory.getConstFactory().getTrue(), null));
              }
              else if (caseValue != null &&
                  caseExpression instanceof PsiReferenceExpression &&
                  ((PsiReferenceExpression)caseExpression).getQualifierExpression() == null) {
                
                addInstruction(new PushInstruction(myFactory.createValue(caseExpression), caseExpression));
                caseValue.accept(this);
                addInstruction(new BinopInstruction(JavaTokenType.EQEQ, null, myProject));
              }
              else {
                pushUnknown();
              }

              addInstruction(new ConditionalGotoInstruction(offset, false, statement));

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

  @Override
  public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
    startElement(expression);

    PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier != null) {
      qualifier.accept(this);
      addInstruction(new FieldReferenceInstruction(qualifier, "Method reference qualifier"));
    }

    addInstruction(new PushInstruction(myFactory.createTypeValue(expression.getFunctionalInterfaceType(), Nullness.NOT_NULL), expression));

    finishElement(expression);
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
      CatchDescriptor cd = findNextCatch(false);
      if (cd == null) {
        addInstruction(new ReturnInstruction(true, statement));
        finishElement(statement);
        return;
      }
      
      addConditionalRuntimeThrow();
      addInstruction(new DupInstruction());
      addInstruction(new PushInstruction(myFactory.getConstFactory().getNull(), null));
      addInstruction(new BinopInstruction(JavaTokenType.EQEQ, null, myProject));
      ConditionalGotoInstruction gotoInstruction = new ConditionalGotoInstruction(null, true, null);
      addInstruction(gotoInstruction);
      
      addInstruction(new PopInstruction());
      initException(myNpe, cd);
      addThrowCode(cd, statement);

      gotoInstruction.setOffset(myCurrentFlow.getInstructionCount());
      addInstruction(new PushInstruction(getExceptionHolder(cd), null));
      addInstruction(new SwapInstruction());
      addInstruction(new AssignInstruction(null, null));
      addInstruction(new PopInstruction());
      addThrowCode(cd, statement);
    }

    finishElement(statement);
  }

  private void addConditionalRuntimeThrow() {
    CatchDescriptor cd = findNextCatch(false);
    if (cd == null) {
      return;
    }
    
    pushUnknown();
    final ConditionalGotoInstruction ifNoException = addInstruction(new ConditionalGotoInstruction(null, false, null));
    addInstruction(new EmptyStackInstruction());

    addInstruction(new PushInstruction(getExceptionHolder(cd), null));

    pushUnknown();
    final ConditionalGotoInstruction ifError = addInstruction(new ConditionalGotoInstruction(null, false, null));
    addInstruction(new PushInstruction(myRuntimeException, null));
    GotoInstruction ifRuntime = addInstruction(new GotoInstruction(null));
    ifError.setOffset(myCurrentFlow.getInstructionCount());
    addInstruction(new PushInstruction(myError, null));
    ifRuntime.setOffset(myCurrentFlow.getInstructionCount());

    addInstruction(new AssignInstruction(null, null));
    addInstruction(new PopInstruction());
    
    addThrowCode(cd, null);

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
  private void addThrowCode(@Nullable CatchDescriptor cd, @Nullable PsiElement explicitThrower) {
    if (cd == null) {
      addInstruction(new ReturnInstruction(true, explicitThrower));
      return;
    }
    
    flushVariablesOnControlTransfer(cd.getBlock());
    addInstruction(new GotoInstruction(cd.getJumpOffset(this)));
  }

  @Nullable
  private CatchDescriptor findNextCatch(boolean catchRethrow) {
    if (myCatchStack.isEmpty()) {
      return null;
    }

    PsiElement currentElement = myElementStack.peek();

    CatchDescriptor cd = myCatchStack.get(myCatchStack.size() - 1);
    if (!cd.isFinally() && PsiTreeUtil.isAncestor(cd.getBlock().getParent(), currentElement, false)) {
      int i = myCatchStack.size() - 2;
      while (!catchRethrow && i >= 0 && !myCatchStack.get(i).isFinally() && myCatchStack.get(i).getTryStatement() == cd.getTryStatement()) {
        i--;
      }
      if (i < 0) {
        return null;
      }
      cd = myCatchStack.get(i);
    }

    return cd;
  }


  @Nullable
  private CatchDescriptor findFinally() {
    for (int i = myCatchStack.size() - 1; i >= 0; i--) {
      CatchDescriptor cd = myCatchStack.get(i);
      if (cd.isFinally()) return cd;
    }

    return null;
  }

  private static class ApplyNotNullInstruction extends Instruction {
    private final PsiMethodCallExpression myCall;

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
      CatchDescriptor finallyDescriptor = myCatchStack.pop();
      finallyBlock.accept(this);
      
      //if $exception$==null => continue normal execution
      addInstruction(new PushInstruction(getExceptionHolder(finallyDescriptor), null));
      addInstruction(new PushInstruction(myFactory.getConstFactory().getNull(), null));
      addInstruction(new BinopInstruction(JavaTokenType.EQEQ, null, myProject));
      addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), false, null));
      
      // else throw $exception$
      rethrowException(finallyDescriptor, false);
    }

    finishElement(statement);
  }

  @Override
  public void visitCatchSection(PsiCatchSection section) {
    startElement(section);
    PsiCodeBlock catchBlock = section.getCatchBlock();
    if (catchBlock != null) {
      CatchDescriptor currentDescriptor = new CatchDescriptor(section.getParameter(), catchBlock);
      DfaVariableValue exceptionHolder = getExceptionHolder(currentDescriptor);

      // exception is in exceptionHolder mock variable
      // check if it's assignable to catch parameter type
      PsiType declaredType = section.getCatchType();
      List<PsiType> flattened = declaredType instanceof PsiDisjunctionType ?
                                ((PsiDisjunctionType)declaredType).getDisjunctions() :
                                ContainerUtil.createMaybeSingletonList(declaredType);
      for (PsiType catchType : flattened) {
        addInstruction(new PushInstruction(exceptionHolder, null));
        addInstruction(new PushInstruction(myFactory.createTypeValue(catchType, Nullness.UNKNOWN), null));
        addInstruction(new BinopInstruction(JavaTokenType.INSTANCEOF_KEYWORD, null, myProject));
        addInstruction(new ConditionalGotoInstruction(ControlFlow.deltaOffset(getStartOffset(catchBlock), -5), false, null));
      }
      
      // not assignable => rethrow 
      rethrowException(currentDescriptor, true);

      // e = $exception$
      addInstruction(new PushInstruction(myFactory.getVarFactory().createVariableValue(section.getParameter(), false), null));
      addInstruction(new PushInstruction(exceptionHolder, null));
      addInstruction(new AssignInstruction(null, null));
      addInstruction(new PopInstruction());
      
      addInstruction(new FlushVariableInstruction(exceptionHolder));
      
      catchBlock.accept(this);
    }
    finishElement(section);
  }

  private void rethrowException(CatchDescriptor currentDescriptor, boolean catchRethrow) {
    CatchDescriptor nextCatch = findNextCatch(catchRethrow);
    if (nextCatch != null) {
      addInstruction(new PushInstruction(getExceptionHolder(nextCatch), null, false));
      addInstruction(new PushInstruction(getExceptionHolder(currentDescriptor), null, true));
      addInstruction(new AssignInstruction(null, null));
      addInstruction(new PopInstruction());
    }
    addThrowCode(nextCatch, null);
  }

  @Override
  public void visitResourceList(PsiResourceList resourceList) {
    for (PsiResourceListElement resource : resourceList) {
      if (resource instanceof PsiResourceVariable) {
        PsiResourceVariable variable = (PsiResourceVariable)resource;
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null) {
          initializeVariable(variable, initializer);
        }
      }
      else if (resource instanceof PsiResourceExpression) {
        ((PsiResourceExpression)resource).getExpression().accept(this);
      }

      final List<PsiClassType> closerExceptions = ExceptionUtil.getCloserExceptions(resource);
      if (!closerExceptions.isEmpty()) {
        addThrows(null, findNextCatch(false), closerExceptions.toArray(new PsiClassType[closerExceptions.size()]));
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

    DfaValue toPush = myFactory.createValue(expression);
    addInstruction(new PushInstruction(toPush != null ? toPush : myFactory.createTypeValue(expression.getType(), Nullness.UNKNOWN), null));
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
      addInstruction(new BinopInstruction(op, expression.isPhysical() ? expression : null, myProject));

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

  private void generateBoxingUnboxingInstructionFor(@NotNull PsiExpression expression, PsiType expectedType) {
    if (PsiType.VOID.equals(expectedType)) return;

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
      PsiElement psiAnchor = i == operands.length - 1 && expression.isPhysical() ? expression : null;
      addInstruction(new BinopInstruction(JavaTokenType.NE, psiAnchor, myProject));
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
    generateBoxingUnboxingInstructionFor(lExpression, exprType);
    addInstruction(new DupInstruction());

    rExpression.accept(this);
    generateBoxingUnboxingInstructionFor(rExpression, exprType);
    addInstruction(new SwapInstruction());

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
    List<ConditionalGotoInstruction> branchToFail = new ArrayList<>();
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
    addInstruction(new PushInstruction(myFactory.createTypeValue(expression.getType(), Nullness.NOT_NULL), expression));
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
      addInstruction(new ConditionalGotoInstruction(elseOffset, true, PsiUtil.skipParenthesizedExprDown(condition)));
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
      addInstruction(new InstanceofInstruction(expression, myProject, operand, type));
    }
    else {
      pushUnknown();
    }

    finishElement(expression);
  }

  private void addMethodThrows(PsiMethod method, @Nullable PsiElement explicitCall) {
    CatchDescriptor cd = findNextCatch(false);
    if (method != null) {
      PsiClassType[] refs = method.getThrowsList().getReferencedTypes();
      addThrows(explicitCall, cd, refs);
    }
  }

  private void addThrows(@Nullable PsiElement explicitCall, CatchDescriptor cd, PsiClassType[] refs) {
    for (PsiClassType ref : refs) {
      pushUnknown();
      ConditionalGotoInstruction cond = new ConditionalGotoInstruction(null, false, null);
      addInstruction(cond);
      addInstruction(new EmptyStackInstruction());
      initException(ref, cd);
      addThrowCode(cd, explicitCall);
      cond.setOffset(myCurrentFlow.getInstructionCount());
    }
  }

  private void initException(PsiType ref, @Nullable CatchDescriptor cd) {
    if (cd == null) return;
    addInstruction(new PushInstruction(getExceptionHolder(cd), null));
    addInstruction(new PushInstruction(myFactory.createTypeValue(ref, Nullness.NOT_NULL), null));
    addInstruction(new AssignInstruction(null, null));
    addInstruction(new PopInstruction());
  }

  private DfaVariableValue getExceptionHolder(CatchDescriptor cd) {
    return myExceptionHolders.get(cd.getTryStatement());
  }

  @Override public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    startElement(expression);

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
    boolean isEqualsCall = expressions.length == 1 && method instanceof PsiMethod &&
                           "equals".equals(((PsiMethod)method).getName()) && parameters.length == 1 &&
                           parameters[0].getType().equalsToText(JAVA_LANG_OBJECT) &&
                           PsiType.BOOLEAN.equals(((PsiMethod)method).getReturnType());

    for (int i = 0; i < expressions.length; i++) {
      PsiExpression paramExpr = expressions[i];
      paramExpr.accept(this);
      if (parameters != null && i < parameters.length) {
        generateBoxingUnboxingInstructionFor(paramExpr, parameters[i].getType());
      }
      if (i == 0 && isEqualsCall) {
        // stack: .., qualifier, arg1
        addInstruction(new SwapInstruction());
        // stack: .., arg1, qualifier
        addInstruction(new DupInstruction(2, 1));
        // stack: .., arg1, qualifier, arg1, qualifier
        addInstruction(new PopInstruction());
        // stack: .., arg1, qualifier, arg1
      }
    }

    addConditionalRuntimeThrow();
    List<MethodContract> contracts = method instanceof PsiMethod ? getMethodCallContracts((PsiMethod)method, expression) : Collections.emptyList();
    addInstruction(new MethodCallInstruction(expression, myFactory.createValue(expression), contracts));
    if (!contracts.isEmpty()) {
      // if a contract resulted in 'fail', handle it
      addInstruction(new DupInstruction());
      addInstruction(new PushInstruction(myFactory.getConstFactory().getContractFail(), null));
      addInstruction(new BinopInstruction(JavaTokenType.EQEQ, null, myProject));
      ConditionalGotoInstruction ifNotFail = new ConditionalGotoInstruction(null, true, null);
      addInstruction(ifNotFail);
      returnCheckingFinally(true, expression);
      ifNotFail.setOffset(myCurrentFlow.getInstructionCount());
    }

    if (!myCatchStack.isEmpty()) {
      addMethodThrows(expression.resolveMethod(), expression);
    }

    if (isEqualsCall) {
      // assume equals argument must be not-null if the result is true
      // don't assume the call result to be false if arg1==null
      
      // stack: .., arg1, call-result
      ConditionalGotoInstruction ifFalse = addInstruction(new ConditionalGotoInstruction(null, true, null));

      addInstruction(new ApplyNotNullInstruction(expression));
      addInstruction(new PushInstruction(myFactory.getConstFactory().getTrue(), null));
      addInstruction(new GotoInstruction(getEndOffset(expression)));

      ifFalse.setOffset(myCurrentFlow.getInstructionCount());
      addInstruction(new PopInstruction());
      addInstruction(new PushInstruction(myFactory.getConstFactory().getFalse(), null));
    }
    finishElement(expression);
  }

  private static List<MethodContract> getMethodCallContracts(@NotNull final PsiMethod method, @NotNull PsiMethodCallExpression call) {
    List<MethodContract> contracts = HardcodedContracts.getHardcodedContracts(method, call);
    return !contracts.isEmpty() ? contracts : getMethodContracts(method);
  }

  static List<MethodContract> getMethodContracts(@NotNull final PsiMethod method) {
    return CachedValuesManager.getCachedValue(method, () -> {
      final PsiAnnotation contractAnno = findContractAnnotation(method);
      if (contractAnno != null) {
        String text = AnnotationUtil.getStringAttributeValue(contractAnno, null);
        if (text != null) {
          try {
            final int paramCount = method.getParameterList().getParametersCount();
            List<MethodContract> applicable = ContainerUtil.filter(MethodContract.parseContract(text),
                                                                   contract -> contract.arguments.length == paramCount);
            return CachedValueProvider.Result.create(applicable, contractAnno, method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
          }
          catch (Exception ignored) {
          }
        }
      }
      return CachedValueProvider.Result.create(Collections.<MethodContract>emptyList(), method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  @Nullable
  public static PsiAnnotation findContractAnnotation(@NotNull PsiMethod method) {
    return AnnotationUtil.findAnnotationInHierarchy(method, Collections.singleton(ORG_JETBRAINS_ANNOTATIONS_CONTRACT));
  }

  public static boolean isPure(@NotNull PsiMethod method) {
    PsiAnnotation anno = findContractAnnotation(method);
    return anno != null && Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(anno, "pure"));
  }

  @Override
  public void visitEnumConstant(PsiEnumConstant enumConstant) {
    if (enumConstant.getArgumentList() == null) return;

    pushUnknown();
    pushConstructorArguments(enumConstant);
    addInstruction(new MethodCallInstruction(enumConstant, null, Collections.emptyList()));
    addInstruction(new PopInstruction());
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
      addInstruction(new MethodCallInstruction(expression, null, Collections.emptyList()));
    }
    else {
      PsiMethod constructor = pushConstructorArguments(expression);

      addConditionalRuntimeThrow();
      addInstruction(new MethodCallInstruction(expression, null, Collections.emptyList()));

      if (!myCatchStack.isEmpty()) {
        addMethodThrows(constructor, expression);
      }

    }

    finishElement(expression);
  }

  private PsiMethod pushConstructorArguments(PsiConstructorCall call) {
    PsiExpressionList args = call.getArgumentList();
    PsiMethod ctr = call.resolveConstructor();
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
    return ctr;
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

    PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());
    if (operand != null) {
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(operand, PsiType.INT);
    } else {
      pushUnknown();
    }

    addInstruction(new PopInstruction());
    pushUnknown();

    flushIncrementedValue(operand);

    finishElement(expression);
  }

  @Override public void visitPrefixExpression(PsiPrefixExpression expression) {
    startElement(expression);

    DfaValue dfaValue = myFactory.createValue(expression);
    if (dfaValue == null) {
      PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());

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

          flushIncrementedValue(operand);
        }
      }
    }
    else {
      addInstruction(new PushInstruction(dfaValue, expression));
    }

    finishElement(expression);
  }

  private void flushIncrementedValue(@Nullable PsiExpression operand) {
    DfaValue dfaVariable = operand == null ? null : myFactory.createValue(operand);
    if (dfaVariable instanceof DfaVariableValue && PsiUtil.isAccessedForWriting(operand)) {
      addInstruction(new FlushVariableInstruction((DfaVariableValue)dfaVariable));
      if (((DfaVariableValue)dfaVariable).getPsiVariable() instanceof PsiField) {
        addInstruction(new FlushVariableInstruction(null));
      }
    }
  }

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    startElement(expression);

    final PsiExpression qualifierExpression = expression.getQualifierExpression();
    if (qualifierExpression != null) {
      qualifierExpression.accept(this);
      addInstruction(expression.resolve() instanceof PsiField ? new FieldReferenceInstruction(expression, null) : new PopInstruction());
    }

    // complex assignments (e.g. "|=") are both reading and writing
    boolean writing = PsiUtil.isAccessedForWriting(expression) && !PsiUtil.isAccessedForReading(expression);
    addInstruction(new PushInstruction(myFactory.createValue(expression), expression, writing));

    finishElement(expression);
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
      addInstruction(new PushInstruction(myFactory.createTypeValue(castExpression.getType(), Nullness.UNKNOWN), null));
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

