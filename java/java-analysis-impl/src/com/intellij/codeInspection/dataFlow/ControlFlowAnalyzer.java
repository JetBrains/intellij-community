/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInspection.dataFlow.inliner.CallInliner;
import com.intellij.codeInspection.dataFlow.inliner.LambdaInliner;
import com.intellij.codeInspection.dataFlow.inliner.OptionalChainInliner;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import com.siyeh.ig.numeric.UnnecessaryExplicitNumericCastInspection;
import com.siyeh.ig.psiutils.CountingLoop;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.StreamEx;
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
  private FList<Trap> myTrapStack = FList.emptyList();
  private final ExceptionTransfer myRuntimeException;
  private final ExceptionTransfer myError;
  private final PsiType myAssertionError;
  private PsiLambdaExpression myLambdaExpression = null;

  ControlFlowAnalyzer(final DfaValueFactory valueFactory, @NotNull PsiElement codeFragment, boolean ignoreAssertions) {
    myFactory = valueFactory;
    myCodeFragment = codeFragment;
    myProject = codeFragment.getProject();
    myIgnoreAssertions = ignoreAssertions;
    GlobalSearchScope scope = codeFragment.getResolveScope();
    myRuntimeException = new ExceptionTransfer(myFactory.createTypeValue(createClassType(scope, JAVA_LANG_RUNTIME_EXCEPTION), Nullness.NOT_NULL));
    myError = new ExceptionTransfer(myFactory.createTypeValue(createClassType(scope, JAVA_LANG_ERROR), Nullness.NOT_NULL));
    myAssertionError = createClassType(scope, JAVA_LANG_ASSERTION_ERROR);
  }

  @Nullable
  public ControlFlow buildControlFlow() {
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

    addInstruction(new ReturnInstruction(myFactory.controlTransfer(ReturnTransfer.INSTANCE, FList.emptyList()), null));

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
  }

  private void finishElement(PsiElement element) {
    myCurrentFlow.finishElement(element);
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
    else if (op == JavaTokenType.ANDEQ && isBoolean) {
      generateBooleanAssignmentExpression(true, lExpr, rExpr, type);
    }
    else if (op == JavaTokenType.OREQ && isBoolean) {
      generateBooleanAssignmentExpression(false, lExpr, rExpr, type);
    }
    else if (op == JavaTokenType.XOREQ && isBoolean) {
      generateXorExpression(expression, new PsiExpression[]{lExpr, rExpr}, type, true);
    }
    else if (op == JavaTokenType.PLUSEQ && type != null && type.equalsToText(JAVA_LANG_STRING)) {
      lExpr.accept(this);
      addInstruction(new DupInstruction());
      rExpr.accept(this);
      addInstruction(new BinopInstruction(JavaTokenType.PLUS, null, myProject));
    }
    else if (isAssignmentDivision(op) && type != null && PsiType.LONG.isAssignableFrom(type)) {
      lExpr.accept(this);
      generateBoxingUnboxingInstructionFor(lExpr, type);
      rExpr.accept(this);
      generateBoxingUnboxingInstructionFor(rExpr, type);
      checkZeroDivisor();
      addInstruction(new PopInstruction());
      pushUnknown();
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

      throwException(myAssertionError, statement);
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
      controlTransfer(new InstructionTransfer(getEndOffset(exitedStatement), getVariablesInside(exitedStatement)),
                      getTrapsInsideStatement(exitedStatement));
    }

    finishElement(statement);
  }

  private void controlTransfer(InstructionTransfer target, FList<Trap> traps) {
    addInstruction(new ControlTransferInstruction(myFactory.controlTransfer(target, traps)));
  }

  @NotNull
  private FList<Trap> getTrapsInsideStatement(PsiStatement statement) {
    return FList.createFromReversed(ContainerUtil.reverse(
      ContainerUtil.findAll(myTrapStack, cd -> PsiTreeUtil.isAncestor(statement, cd.getAnchor(), true))));
  }

  @NotNull
  private List<DfaVariableValue> getVariablesInside(PsiElement exitedStatement) {
    return ContainerUtil.map(PsiTreeUtil.findChildrenOfType(exitedStatement, PsiVariable.class),
                             var -> myFactory.getVarFactory().createVariableValue(var, false));
  }

  @Override public void visitContinueStatement(PsiContinueStatement statement) {
    startElement(statement);
    PsiStatement continuedStatement = statement.findContinuedStatement();
    if (continuedStatement instanceof PsiLoopStatement) {
      PsiStatement body = ((PsiLoopStatement)continuedStatement).getBody();
      controlTransfer(new InstructionTransfer(getEndOffset(body), getVariablesInside(body)), getTrapsInsideStatement(body));

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

    ControlFlow.ControlFlowOffset loopEndOffset = getEndOffset(statement);
    boolean hasSizeCheck = false;

    if (iteratedValue != null) {
      iteratedValue.accept(this);
      addInstruction(new FieldReferenceInstruction(iteratedValue, "Collection iterator or array.length"));
      DfaValue qualifier = myFactory.createValue(iteratedValue);

      if (qualifier instanceof DfaVariableValue) {
        PsiType type = iteratedValue.getType();
        SpecialField length = null;
        if (type instanceof PsiArrayType) {
          length = SpecialField.ARRAY_LENGTH;
        }
        else if (InheritanceUtil.isInheritor(type, JAVA_UTIL_COLLECTION)) {
          length = SpecialField.COLLECTION_SIZE;
        }
        if (length != null) {
          addInstruction(new PushInstruction(length.createValue(myFactory, qualifier), null));
          addInstruction(new PushInstruction(myFactory.getConstFactory().createFromValue(0, PsiType.INT, null), null));
          addInstruction(new BinopInstruction(JavaTokenType.EQEQ, iteratedValue, myProject));
          addInstruction(new ConditionalGotoInstruction(loopEndOffset, false, null));
          hasSizeCheck = true;
        }
      }
    }

    ControlFlow.ControlFlowOffset offset = myCurrentFlow.getNextOffset();
    DfaVariableValue dfaVariable = myFactory.getVarFactory().createVariableValue(parameter, false);
    addInstruction(new FlushVariableInstruction(dfaVariable));

    if (!hasSizeCheck) {
      pushUnknown();
      addInstruction(new ConditionalGotoInstruction(loopEndOffset, true, null));
    }

    final PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    if (hasSizeCheck) {
      pushUnknown();
      addInstruction(new ConditionalGotoInstruction(loopEndOffset, true, null));
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

    addCountingLoopBound(statement);

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

  /**
   * Add known-to-be-true condition inside counting loop, effectively converting
   * {@code for(int i=origin; i<bound; i++)} to {@code for(int i=origin; i>=origin && i<bound; i++)}.
   * This adds a range knowledge to data flow analysis.
   * <p>
   * Does nothing if the statement is not a counting loop.
   *
   * @param statement counting loop candidate.
   */
  private void addCountingLoopBound(PsiForStatement statement) {
    CountingLoop loop = CountingLoop.from(statement);
    if (loop == null) return;
    PsiLocalVariable counter = loop.getCounter();
    if (loop.isIncluding() && !(PsiType.LONG.equals(counter.getType()) && PsiType.INT.equals(loop.getBound().getType()))) {
      Object bound = ExpressionUtils.computeConstantExpression(loop.getBound());
      // could be for(int i=0; i<=Integer.MAX_VALUE; i++) which will overflow: conservatively skip this
      if (!(bound instanceof Number)) return;
      if (bound.equals(Long.MAX_VALUE) || bound.equals(Integer.MAX_VALUE)) return;
    }
    PsiExpression initializer = loop.getInitializer();
    if (!PsiType.INT.equals(initializer.getType()) && !PsiType.LONG.equals(initializer.getType())) return;
    DfaValue origin = null;
    Object initialValue = ExpressionUtils.computeConstantExpression(initializer);
    if (initialValue instanceof Number) {
      origin = myFactory.getConstFactory().createFromValue(initialValue, initializer.getType(), null);
    }
    else if (initializer instanceof PsiReferenceExpression) {
      PsiVariable initialVariable = ObjectUtils.tryCast(((PsiReferenceExpression)initializer).resolve(), PsiVariable.class);
      if ((initialVariable instanceof PsiLocalVariable || initialVariable instanceof PsiParameter)
        && !VariableAccessUtils.variableIsAssigned(initialVariable, statement.getBody())) {
        origin = myFactory.getVarFactory().createVariableValue(initialVariable, false);
      }
    }
    if (origin == null || VariableAccessUtils.variableIsAssigned(counter, statement.getBody())) return;
    addInstruction(new PushInstruction(myFactory.getVarFactory().createVariableValue(counter, false), null));
    addInstruction(new PushInstruction(origin, null));
    addInstruction(new BinopInstruction(JavaTokenType.LT, null, myProject));
    addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), false, null));
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
    }

    if (myLambdaExpression == null) {
      if (returnValue != null) {
        addInstruction(new CheckReturnValueInstruction(returnValue));
      }
      addInstruction(new ReturnInstruction(myFactory.controlTransfer(ReturnTransfer.INSTANCE, myTrapStack), statement));
    }
    else {
      if (returnValue == null) {
        pushUnknown();
      }
      controlTransfer(new InstructionTransfer(getEndOffset(myLambdaExpression), getVariablesInside(myLambdaExpression)), myTrapStack);
    }
    finishElement(statement);
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

      addConditionalRuntimeThrow();
      addInstruction(new FieldReferenceInstruction(exception, "thrown exception"));
      throwException(exception.getType(), statement);
    }

    finishElement(statement);
  }

  private void addConditionalRuntimeThrow() {
    if (myTrapStack.isEmpty()) {
      return;
    }
    
    pushUnknown();
    final ConditionalGotoInstruction ifNoException = addInstruction(new ConditionalGotoInstruction(null, false, null));

    pushUnknown();
    final ConditionalGotoInstruction ifError = addInstruction(new ConditionalGotoInstruction(null, false, null));
    throwException(myRuntimeException, null);
    ifError.setOffset(myCurrentFlow.getInstructionCount());
    throwException(myError, null);

    ifNoException.setOffset(myCurrentFlow.getInstructionCount());
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
      if (state.applyCondition(factory.createCondition(value, RelationType.NE, factory.getConstFactory().getNull()))) {
        return nextInstruction(runner, state);
      }
      if (visitor instanceof StandardInstructionVisitor) {
        ((StandardInstructionVisitor)visitor).skipConstantConditionReporting(myCall);
      }
      return DfaInstructionState.EMPTY_ARRAY;
    }

    @Override
    public String toString() {
      return "APPLY NOT NULL";
    }
  }

  @Override
  public void visitTryStatement(PsiTryStatement statement) {
    startElement(statement);

    PsiResourceList resourceList = statement.getResourceList();
    PsiCodeBlock tryBlock = statement.getTryBlock();
    PsiCodeBlock finallyBlock = statement.getFinallyBlock();

    Trap.TryFinally finallyDescriptor = finallyBlock != null ? new Trap.TryFinally(finallyBlock, getStartOffset(finallyBlock)) : null;
    if (finallyDescriptor != null) {
      myTrapStack = myTrapStack.prepend(finallyDescriptor);
    }

    PsiCatchSection[] sections = statement.getCatchSections();
    if (sections.length > 0) {
      LinkedHashMap<PsiCatchSection, ControlFlow.ControlFlowOffset> clauses = new LinkedHashMap<>();
      for (PsiCatchSection section : sections) {
        PsiCodeBlock catchBlock = section.getCatchBlock();
        if (catchBlock != null) {
          clauses.put(section, getStartOffset(catchBlock));
        }
      }
      myTrapStack = myTrapStack.prepend(new Trap.TryCatch(statement, clauses));
    }

    if (resourceList != null) {
      resourceList.accept(this);
    }

    if (tryBlock != null) {
      tryBlock.accept(this);
    }

    InstructionTransfer gotoEnd = new InstructionTransfer(getEndOffset(statement), getVariablesInside(tryBlock));
    FList<Trap> singleFinally = FList.createFromReversed(ContainerUtil.createMaybeSingletonList(finallyDescriptor));
    controlTransfer(gotoEnd, singleFinally);

    if (sections.length > 0) {
      assert myTrapStack.getHead() instanceof Trap.TryCatch;
      myTrapStack = myTrapStack.getTail();
    }

    for (PsiCatchSection section : sections) {
      PsiCodeBlock catchBlock = section.getCatchBlock();
      if (catchBlock != null) {
        visitCodeBlock(catchBlock);
      }
      controlTransfer(gotoEnd, singleFinally);
    }

    if (finallyBlock != null) {
      assert myTrapStack.getHead() instanceof Trap.TryFinally;
      myTrapStack = myTrapStack.getTail().prepend(new Trap.InsideFinally(finallyBlock));

      finallyBlock.accept(this);
      addInstruction(new ControlTransferInstruction(null)); // DfaControlTransferValue is on stack

      assert myTrapStack.getHead() instanceof Trap.InsideFinally;
      myTrapStack = myTrapStack.getTail();
    }

    finishElement(statement);
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
        addThrows(null, closerExceptions.toArray(new PsiClassType[closerExceptions.size()]));
      }
    }
  }

  @Override public void visitWhileStatement(PsiWhileStatement statement) {
    startElement(statement);

    PsiExpression condition = statement.getCondition();

    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
    } else {
      pushUnknown();
    }
    addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), true, condition));

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    addInstruction(new GotoInstruction(getStartOffset(statement)));

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

    PsiExpression indexExpression = expression.getIndexExpression();
    if (indexExpression != null) {
      indexExpression.accept(this);
      generateBoxingUnboxingInstructionFor(indexExpression, PsiType.INT);
    } else {
      addInstruction(new PushInstruction(DfaUnknownValue.getInstance(), null));
    }

    DfaValue toPush = myFactory.createValue(expression);
    if (toPush == null) {
      toPush = myFactory.createTypeValue(expression.getType(), Nullness.UNKNOWN);
    }
    addInstruction(new ArrayAccessInstruction(toPush, expression));
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
    else if (isBinaryDivision(op) && operands.length == 2 &&
             type != null && PsiType.LONG.isAssignableFrom(type)) {
      generateDivMod(expression, type, operands[0], operands[1]);
    }
    else {
      generateOther(expression, op, operands, type);
    }
    finishElement(expression);
  }

  static boolean isBinaryDivision(IElementType binaryOp) {
    return binaryOp == JavaTokenType.DIV || binaryOp == JavaTokenType.PERC;
  }

  static boolean isAssignmentDivision(IElementType op) {
    return op == JavaTokenType.PERCEQ || op == JavaTokenType.DIVEQ;
  }

  private void generateDivMod(PsiPolyadicExpression expression, PsiType type, PsiExpression left, PsiExpression right) {
    left.accept(this);
    generateBoxingUnboxingInstructionFor(left, type);
    right.accept(this);
    generateBoxingUnboxingInstructionFor(right, type);
    checkZeroDivisor();
    addInstruction(new BinopInstruction(expression.getOperationTokenType(), expression.isPhysical() ? expression : null, myProject));
  }

  private void checkZeroDivisor() {
    addInstruction(new DupInstruction());
    addInstruction(new PushInstruction(myFactory.getConstFactory().createFromValue(0, PsiType.LONG, null), null));
    addInstruction(new BinopInstruction(JavaTokenType.NE, null, myProject));
    ConditionalGotoInstruction ifNonZero = new ConditionalGotoInstruction(null, false, null);
    addInstruction(ifNonZero);
    throwException(JavaPsiFacade.getElementFactory(myProject).createTypeByFQClassName(ArithmeticException.class.getName()), null);
    ifNonZero.setOffset(myCurrentFlow.getInstructionCount());
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
    generateBoxingUnboxingInstructionFor(expression, expression.getType(), expectedType);
  }

  private void generateBoxingUnboxingInstructionFor(@NotNull PsiExpression context, PsiType actualType, PsiType expectedType) {
    if (PsiType.VOID.equals(expectedType)) return;

    if (TypeConversionUtil.isPrimitiveAndNotNull(expectedType) && TypeConversionUtil.isPrimitiveWrapper(actualType)) {
      addInstruction(new MethodCallInstruction(context, MethodCallInstruction.MethodType.UNBOXING, expectedType));
    }
    else if (TypeConversionUtil.isPrimitiveAndNotNull(actualType) && TypeConversionUtil.isAssignableFromPrimitiveWrapper(expectedType)) {
      addConditionalRuntimeThrow();
      addInstruction(new MethodCallInstruction(context, MethodCallInstruction.MethodType.BOXING, expectedType));
    }
    else if (actualType != expectedType &&
             TypeConversionUtil.isPrimitiveAndNotNull(actualType) &&
             TypeConversionUtil.isPrimitiveAndNotNull(expectedType) &&
             TypeConversionUtil.isNumericType(actualType) &&
             TypeConversionUtil.isNumericType(expectedType)) {
      addInstruction(new MethodCallInstruction(context, MethodCallInstruction.MethodType.CAST, expectedType) {
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
    DfaConstValue constValue = myFactory.getBoolean(!and);
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
    if (method != null) {
      addThrows(explicitCall, method.getThrowsList().getReferencedTypes());
    }
  }

  private void addThrows(@Nullable PsiElement explicitCall, PsiClassType[] refs) {
    for (PsiClassType ref : refs) {
      pushUnknown();
      ConditionalGotoInstruction cond = new ConditionalGotoInstruction(null, false, null);
      addInstruction(cond);
      throwException(ref, explicitCall);
      cond.setOffset(myCurrentFlow.getInstructionCount());
    }
  }

  private void throwException(PsiType ref, @Nullable PsiElement anchor) {
    throwException(new ExceptionTransfer(myFactory.createTypeValue(ref, Nullness.NOT_NULL)), anchor);
  }

  private void throwException(ExceptionTransfer kind, @Nullable PsiElement anchor) {
    addInstruction(new EmptyStackInstruction());
    addInstruction(new ReturnInstruction(myFactory.controlTransfer(kind, myTrapStack), anchor));
  }

  @Override public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    startElement(expression);

    for (CallInliner inliner : INLINERS) {
      if (inliner.tryInlineCall(new CFGBuilder(this), expression)) {
        finishElement(expression);
        return;
      }
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
    JavaResolveResult result = methodExpression.advancedResolve(false);
    PsiElement method = result.getElement();
    PsiParameter[] parameters = method instanceof PsiMethod ? ((PsiMethod)method).getParameterList().getParameters() : null;
    boolean isEqualsCall = expressions.length == 1 && method instanceof PsiMethod &&
                           "equals".equals(((PsiMethod)method).getName()) && parameters.length == 1 &&
                           parameters[0].getType().equalsToText(JAVA_LANG_OBJECT) &&
                           PsiType.BOOLEAN.equals(((PsiMethod)method).getReturnType());

    for (int i = 0; i < expressions.length; i++) {
      PsiExpression paramExpr = expressions[i];
      paramExpr.accept(this);
      if (parameters != null && i < parameters.length) {
        generateBoxingUnboxingInstructionFor(paramExpr, result.getSubstitutor().substitute(parameters[i].getType()));
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

    addBareCall(expression);

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

  private void addBareCall(PsiMethodCallExpression expression) {
    addConditionalRuntimeThrow();
    PsiMethod method = expression.resolveMethod();
    List<? extends MethodContract> contracts = method == null ? Collections.emptyList() : getMethodCallContracts(method, expression);
    addInstruction(new MethodCallInstruction(expression, myFactory.createValue(expression), contracts));
    if (contracts.stream().anyMatch(c -> c.getReturnValue() == MethodContract.ValueConstraint.THROW_EXCEPTION)) {
      // if a contract resulted in 'fail', handle it
      addInstruction(new DupInstruction());
      addInstruction(new PushInstruction(myFactory.getConstFactory().getContractFail(), null));
      addInstruction(new BinopInstruction(JavaTokenType.EQEQ, null, myProject));
      ConditionalGotoInstruction ifNotFail = new ConditionalGotoInstruction(null, true, null);
      addInstruction(ifNotFail);
      addInstruction(new EmptyStackInstruction());
      addInstruction(new ReturnInstruction(myFactory.controlTransfer(new ExceptionTransfer(DfaUnknownValue.getInstance()), myTrapStack), expression));

      ifNotFail.setOffset(myCurrentFlow.getInstructionCount());
    }

    if (!myTrapStack.isEmpty()) {
      addMethodThrows(expression.resolveMethod(), expression);
    }
  }

  public static List<? extends MethodContract> getMethodCallContracts(@NotNull final PsiMethod method,
                                                                      @Nullable PsiMethodCallExpression call) {
    List<MethodContract> contracts = HardcodedContracts.getHardcodedContracts(method, call);
    return !contracts.isEmpty() ? contracts : getMethodContracts(method);
  }

  public static List<StandardMethodContract> getMethodContracts(@NotNull final PsiMethod method) {
    return CachedValuesManager.getCachedValue(method, () -> {
      final PsiAnnotation contractAnno = findContractAnnotation(method);
      if (contractAnno != null) {
        String text = AnnotationUtil.getStringAttributeValue(contractAnno, null);
        if (text != null) {
          try {
            final int paramCount = method.getParameterList().getParametersCount();
            List<StandardMethodContract> applicable = ContainerUtil.filter(StandardMethodContract.parseContract(text),
                                                                           contract -> contract.arguments.length == paramCount);
            return CachedValueProvider.Result.create(applicable, contractAnno, method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
          }
          catch (Exception ignored) {
          }
        }
      }
      return CachedValueProvider.Result
        .create(Collections.<StandardMethodContract>emptyList(), method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
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
      addInstruction(new MethodCallInstruction(expression, null, constructor == null ? Collections.emptyList() : getMethodContracts(constructor)));

      if (!myTrapStack.isEmpty()) {
        addMethodThrows(constructor, expression);
      }

    }

    finishElement(expression);
  }

  @Nullable
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

  static final CallInliner[] INLINERS = {new OptionalChainInliner(), new LambdaInliner()};

  /**
   * A facade for building control flow graph used by {@link CallInliner} implementations
   */
  public static class CFGBuilder {
    private final ControlFlowAnalyzer myAnalyzer;
    private final Deque<JumpInstruction> myBranches = new ArrayDeque<>();

    CFGBuilder(ControlFlowAnalyzer analyzer) {
      myAnalyzer = analyzer;
    }

    public CFGBuilder pushUnknown() {
      myAnalyzer.pushUnknown();
      return this;
    }

    public CFGBuilder pushNull() {
      myAnalyzer.addInstruction(new PushInstruction(myAnalyzer.myFactory.getConstFactory().getNull(), null));
      return this;
    }

    public CFGBuilder pushExpression(PsiExpression expression) {
      expression.accept(myAnalyzer);
      return this;
    }

    public CFGBuilder pushVariable(PsiVariable variable) {
      myAnalyzer.addInstruction(
        new PushInstruction(myAnalyzer.myFactory.getVarFactory().createVariableValue(variable, false), null, true));
      return this;
    }

    public CFGBuilder push(DfaValue value) {
      myAnalyzer.addInstruction(new PushInstruction(value, null));
      return this;
    }

    public CFGBuilder pop() {
      myAnalyzer.addInstruction(new PopInstruction());
      return this;
    }

    public CFGBuilder dup() {
      myAnalyzer.addInstruction(new DupInstruction());
      return this;
    }

    public CFGBuilder splice(int count, int... replacement) {
      myAnalyzer.addInstruction(new SpliceInstruction(count, replacement));
      return this;
    }

    public CFGBuilder swap() {
      myAnalyzer.addInstruction(new SwapInstruction());
      return this;
    }

    public CFGBuilder invoke(PsiMethodCallExpression call) {
      myAnalyzer.addBareCall(call);
      return this;
    }

    public CFGBuilder ifConditionIs(boolean value) {
      ConditionalGotoInstruction gotoInstruction = new ConditionalGotoInstruction(null, value, null);
      myBranches.add(gotoInstruction);
      myAnalyzer.addInstruction(gotoInstruction);
      return this;
    }

    public CFGBuilder endIf() {
      myBranches.removeLast().setOffset(myAnalyzer.myCurrentFlow.getInstructionCount());
      return this;
    }

    private CFGBuilder compare(IElementType relation) {
      myAnalyzer.addInstruction(new BinopInstruction(relation, null, myAnalyzer.myProject));
      return this;
    }

    public CFGBuilder elseBranch() {
      GotoInstruction gotoInstruction = new GotoInstruction(null);
      myAnalyzer.addInstruction(gotoInstruction);
      endIf();
      myBranches.add(gotoInstruction);
      return this;
    }

    public CFGBuilder ifCondition(IElementType relation) {
      return compare(relation).ifConditionIs(true);
    }

    public CFGBuilder ifNotNull() {
      return pushNull().ifCondition(JavaTokenType.NE);
    }

    public CFGBuilder ifNull() {
      return pushNull().ifCondition(JavaTokenType.EQEQ);
    }

    public CFGBuilder boxUnbox(PsiExpression expression, PsiType expectedType) {
      myAnalyzer.generateBoxingUnboxingInstructionFor(expression, expectedType);
      return this;
    }

    public CFGBuilder assign() {
      myAnalyzer.addInstruction(new AssignInstruction(null, null));
      return this;
    }

    public CFGBuilder assignTo(PsiVariable var) {
      return pushVariable(var).swap().assign();
    }

    public DfaValueFactory getFactory() {
      return myAnalyzer.myFactory;
    }

    /**
     * Generates instructions to invoke functional expression (inlining it if possible) which
     * consumes given amount of stack arguments
     *
     * @param argCount             number of stack arguments to consume
     * @param functionalExpression a functional expression to invoke
     * @return this builder
     */
    public CFGBuilder invokeFunction(int argCount, @Nullable PsiExpression functionalExpression) {
      PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(functionalExpression);
      if (stripped instanceof PsiTypeCastExpression) {
        stripped = ((PsiTypeCastExpression)stripped).getOperand();
      }
      if (stripped instanceof PsiLambdaExpression) {
        PsiLambdaExpression lambda = (PsiLambdaExpression)stripped;
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if (parameters.length == argCount && lambda.getBody() != null) {
          StreamEx.ofReversed(parameters).forEach(p -> assignTo(p).pop());
          return inlineLambda(lambda);
        }
      }
      if (stripped instanceof PsiMethodReferenceExpression) {
        PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)stripped;
        JavaResolveResult resolveResult = methodRef.advancedResolve(false);
        PsiMethod method = ObjectUtils.tryCast(resolveResult.getElement(), PsiMethod.class);
        if (method != null) {
          // TODO: advanced method references support, including contracts
          splice(argCount);
          pushExpression(methodRef);
          pop();
          PsiSubstitutor substitutor = resolveResult.getSubstitutor();
          PsiType returnType = substitutor.substitute(method.getReturnType());
          if (returnType != null) {
            push(getFactory().createTypeValue(returnType, DfaPsiUtil.getElementNullability(returnType, method)));
            myAnalyzer.generateBoxingUnboxingInstructionFor(methodRef, returnType, LambdaUtil.getFunctionalInterfaceReturnType(methodRef));
          }
          else {
            pushUnknown();
          }
          return this;
        }
      }
      splice(argCount);
      if (functionalExpression == null) {
        pushUnknown();
        return this;
      }
      pushExpression(functionalExpression);
      pop(); // TODO: handle deference
      PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalExpression.getType());
      if (returnType != null) {
        push(getFactory().createTypeValue(returnType, DfaPsiUtil.getTypeNullability(returnType)));
      }
      else {
        pushUnknown();
      }
      return this;
    }

    public CFGBuilder inlineLambda(PsiLambdaExpression lambda) {
      PsiLambdaExpression oldLambda = myAnalyzer.myLambdaExpression;
      myAnalyzer.myLambdaExpression = lambda;
      myAnalyzer.startElement(lambda);
      try {
        PsiElement body = lambda.getBody();
        Objects.requireNonNull(body).accept(myAnalyzer);
        if (body instanceof PsiCodeBlock) {
          pushUnknown(); // return value for void or incomplete lambda
        }
        else if (body instanceof PsiExpression) {
          boxUnbox((PsiExpression)body, LambdaUtil.getFunctionalInterfaceReturnType(lambda));
        }
      }
      finally {
        myAnalyzer.finishElement(lambda);
        myAnalyzer.myLambdaExpression = oldLambda;
      }
      return this;
    }

    public PsiParameter createTempVariable(PsiType type) {
      return JavaPsiFacade.getElementFactory(myAnalyzer.myProject)
        .createParameter("tmp$" + myAnalyzer.myCurrentFlow.getInstructionCount(), type);
    }
  }
}

