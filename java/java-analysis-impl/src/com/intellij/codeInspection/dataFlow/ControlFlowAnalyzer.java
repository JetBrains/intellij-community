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

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInspection.dataFlow.ControlFlow.ControlFlowOffset;
import com.intellij.codeInspection.dataFlow.Trap.InsideFinally;
import com.intellij.codeInspection.dataFlow.Trap.TryCatch;
import com.intellij.codeInspection.dataFlow.Trap.TryFinally;
import com.intellij.codeInspection.dataFlow.Trap.TwrFinally;
import com.intellij.codeInspection.dataFlow.inliner.*;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction.MethodType;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.numeric.UnnecessaryExplicitNumericCastInspection;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

import static com.intellij.psi.CommonClassNames.*;

public class ControlFlowAnalyzer extends JavaElementVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer");
  private static final CallMatcher LIST_INITIALIZER = CallMatcher.anyOf(
    CallMatcher.staticCall(JAVA_UTIL_ARRAYS, "asList"),
    CallMatcher.staticCall(JAVA_UTIL_LIST, "of"));
  static final int MAX_UNROLL_SIZE = 3;
  private final PsiElement myCodeFragment;
  private final boolean myIgnoreAssertions;
  private final boolean myInlining;
  private final Project myProject;

  private static class CannotAnalyzeException extends RuntimeException { }

  private final DfaValueFactory myFactory;
  private ControlFlow myCurrentFlow;
  private FList<Trap> myTrapStack = FList.emptyList();
  private final ExceptionTransfer myRuntimeException;
  private final ExceptionTransfer myError;
  private final PsiType myAssertionError;
  private InlinedBlockContext myInlinedBlockContext;

  ControlFlowAnalyzer(final DfaValueFactory valueFactory, @NotNull PsiElement codeFragment, boolean ignoreAssertions, boolean inlining) {
    myInlining = inlining;
    myFactory = valueFactory;
    myCodeFragment = codeFragment;
    myProject = codeFragment.getProject();
    myIgnoreAssertions = ignoreAssertions;
    GlobalSearchScope scope = codeFragment.getResolveScope();
    myRuntimeException = new ExceptionTransfer(myFactory.createDfaType(createClassType(scope, JAVA_LANG_RUNTIME_EXCEPTION)));
    myError = new ExceptionTransfer(myFactory.createDfaType(createClassType(scope, JAVA_LANG_ERROR)));
    myAssertionError = createClassType(scope, JAVA_LANG_ASSERTION_ERROR);
  }

  private void buildClassInitializerFlow(PsiClass psiClass, boolean isStatic) {
    for (PsiElement element : psiClass.getChildren()) {
      if (element instanceof PsiField &&
          !((PsiField)element).hasInitializer() &&
          ((PsiField)element).hasModifierProperty(PsiModifier.STATIC) == isStatic) {
        visitField((PsiField)element);
      }
    }
    if (!isStatic &&
        Stream.of(Extensions.getExtensions(ImplicitUsageProvider.EP_NAME)).anyMatch(p -> p.isClassWithCustomizedInitialization(psiClass))) {
      addInstruction(new EscapeInstruction(Collections.singleton(getFactory().getVarFactory().createThisValue(psiClass))));
      addInstruction(new FlushFieldsInstruction());
    }
    for (PsiElement element : psiClass.getChildren()) {
      if (((element instanceof PsiField && ((PsiField)element).hasInitializer()) || element instanceof PsiClassInitializer) &&
          ((PsiMember)element).hasModifierProperty(PsiModifier.STATIC) == isStatic) {
        element.accept(this);
      }
    }
    addInstruction(new EndOfInitializerInstruction(isStatic));
    addInstruction(new FlushFieldsInstruction());
  }

  @Nullable
  public ControlFlow buildControlFlow() {
    myCurrentFlow = new ControlFlow(myFactory);
    try {
      if(myCodeFragment instanceof PsiClass) {
        // if(unknown) { staticInitializer(); } else { instanceInitializer(); }
        pushUnknown();
        ConditionalGotoInstruction conditionalGoto = new ConditionalGotoInstruction(null, false, null);
        addInstruction(conditionalGoto);
        buildClassInitializerFlow((PsiClass)myCodeFragment, true);
        GotoInstruction unconditionalGoto = new GotoInstruction(null);
        addInstruction(unconditionalGoto);
        conditionalGoto.setOffset(getInstructionCount());
        buildClassInitializerFlow((PsiClass)myCodeFragment, false);
        unconditionalGoto.setOffset(getInstructionCount());
      } else {
        myCodeFragment.accept(this);
      }
    }
    catch (CannotAnalyzeException e) {
      return null;
    }

    PsiElement parent = myCodeFragment.getParent();
    if (parent instanceof PsiLambdaExpression && myCodeFragment instanceof PsiExpression) {
      generateBoxingUnboxingInstructionFor((PsiExpression)myCodeFragment,
                                           LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)parent));
      addInstruction(new CheckReturnValueInstruction((PsiExpression)myCodeFragment));
    }

    addInstruction(new ReturnInstruction(myFactory.controlTransfer(ReturnTransfer.INSTANCE, FList.emptyList()), null));

    if (Registry.is("idea.dfa.live.variables.analysis")) {
      new LiveVariablesAnalyzer(myCurrentFlow, myFactory).flushDeadVariablesOnStatementFinish();
    }

    return myCurrentFlow;
  }

  DfaValueFactory getFactory() {
    return myFactory;
  }

  PsiElement getContext() {
    return myCodeFragment;
  }

  private PsiClassType createClassType(GlobalSearchScope scope, String fqn) {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(fqn, scope);
    if (aClass != null) return JavaPsiFacade.getElementFactory(myProject).createType(aClass);
    return JavaPsiFacade.getElementFactory(myProject).createTypeByFQClassName(fqn, scope);
  }

  <T extends Instruction> T addInstruction(T i) {
    myCurrentFlow.addInstruction(i);
    return i;
  }

  int getInstructionCount() {
    return myCurrentFlow.getInstructionCount();
  }

  private ControlFlowOffset getEndOffset(PsiElement element) {
    return myCurrentFlow.getEndOffset(element);
  }

  private ControlFlowOffset getStartOffset(PsiElement element) {
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
      addInstruction(new BinopInstruction(JavaTokenType.PLUS, null, type));
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

    finishElement(expression);
  }

  private void generateDefaultAssignmentBinOp(PsiExpression lExpr, PsiExpression rExpr, final PsiType exprType) {
    lExpr.accept(this);
    addInstruction(new DupInstruction());
    generateBoxingUnboxingInstructionFor(lExpr,exprType);
    rExpr.accept(this);
    generateBoxingUnboxingInstructionFor(rExpr, exprType);
    addInstruction(new BinopInstruction(null, null, exprType));
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
        handleEscapedVariables(element);
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
    else if (!field.hasModifierProperty(PsiModifier.FINAL) && !UnusedSymbolUtil.isImplicitWrite(field)) {
      // initialize with default value
      DfaVariableValue dfaVariable = myFactory.getVarFactory().createVariableValue(field);
      DfaConstValue value = myFactory.getConstFactory().createDefault(field.getType());
      new CFGBuilder(this).assignAndPop(dfaVariable, value);
    }
  }

  @Override
  public void visitClassInitializer(PsiClassInitializer initializer) {
    visitCodeBlock(initializer.getBody());
  }

  private void initializeVariable(PsiVariable variable, PsiExpression initializer) {
    if (DfaUtil.ignoreInitializer(variable)) return;
    DfaVariableValue dfaVariable = myFactory.getVarFactory().createVariableValue(variable);
    addInstruction(new PushInstruction(dfaVariable, initializer, true));
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

    if (exitedStatement != null && PsiTreeUtil.isAncestor(myCodeFragment, exitedStatement, false)) {
      controlTransfer(new InstructionTransfer(getEndOffset(exitedStatement), getVariablesInside(exitedStatement)),
                      getTrapsInsideElement(exitedStatement));
    } else {
      // Jumping out of analyzed code fragment
      controlTransfer(ReturnTransfer.INSTANCE, getTrapsInsideElement(myCodeFragment));
    }

    finishElement(statement);
  }

  private void controlTransfer(@NotNull TransferTarget target, FList<Trap> traps) {
    addInstruction(new ControlTransferInstruction(myFactory.controlTransfer(target, traps)));
  }

  @NotNull
  private FList<Trap> getTrapsInsideElement(PsiElement element) {
    return FList.createFromReversed(ContainerUtil.reverse(
      ContainerUtil.findAll(myTrapStack, cd -> PsiTreeUtil.isAncestor(element, cd.getAnchor(), true))));
  }

  @NotNull
  private List<DfaVariableValue> getVariablesInside(PsiElement exitedStatement) {
    return ContainerUtil.map(PsiTreeUtil.findChildrenOfType(exitedStatement, PsiVariable.class),
                             myFactory.getVarFactory()::createVariableValue);
  }

  @Override public void visitContinueStatement(PsiContinueStatement statement) {
    startElement(statement);
    PsiStatement continuedStatement = statement.findContinuedStatement();
    if (continuedStatement instanceof PsiLoopStatement && PsiTreeUtil.isAncestor(myCodeFragment, continuedStatement, false)) {
      PsiStatement body = ((PsiLoopStatement)continuedStatement).getBody();
      controlTransfer(new InstructionTransfer(getEndOffset(body), getVariablesInside(body)), getTrapsInsideElement(body));
    } else {
      // Jumping out of analyzed code fragment
      controlTransfer(ReturnTransfer.INSTANCE, getTrapsInsideElement(myCodeFragment));
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

  private DfaValue getIteratedElement(PsiExpression iteratedValue) {
    PsiExpression[] expressions = null;
    if (iteratedValue instanceof PsiNewExpression) {
      PsiArrayInitializerExpression initializer = ((PsiNewExpression)iteratedValue).getArrayInitializer();
      if (initializer != null) {
        expressions = initializer.getInitializers();
      }
    }
    else if (iteratedValue instanceof PsiReferenceExpression) {
      PsiElement arrayVar = ((PsiReferenceExpression)iteratedValue).resolve();
      if (arrayVar instanceof PsiVariable) {
        expressions = ExpressionUtils.getConstantArrayElements((PsiVariable)arrayVar);
      }
    }
    if (iteratedValue instanceof PsiMethodCallExpression && LIST_INITIALIZER.test((PsiMethodCallExpression)iteratedValue)) {
      expressions = ((PsiMethodCallExpression)iteratedValue).getArgumentList().getExpressions();
    }
    return expressions == null ? DfaUnknownValue.getInstance() : getFactory().createCommonValue(expressions);
  }

  @Override public void visitForeachStatement(PsiForeachStatement statement) {
    startElement(statement);
    final PsiParameter parameter = statement.getIterationParameter();
    final PsiExpression iteratedValue = PsiUtil.skipParenthesizedExprDown(statement.getIteratedValue());

    ControlFlowOffset loopEndOffset = getEndOffset(statement);
    boolean hasSizeCheck = false;

    if (iteratedValue != null) {
      iteratedValue.accept(this);
      addInstruction(new DereferenceInstruction(iteratedValue));
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
          addInstruction(new PushInstruction(myFactory.getInt(0), null));
          addInstruction(new BinopInstruction(JavaTokenType.EQEQ, iteratedValue, PsiType.BOOLEAN));
          addInstruction(new ConditionalGotoInstruction(loopEndOffset, false, null));
          hasSizeCheck = true;
        }
      }
    }

    ControlFlowOffset offset = myCurrentFlow.getNextOffset();
    DfaVariableValue dfaVariable = myFactory.getVarFactory().createVariableValue(parameter);
    new CFGBuilder(this).assignAndPop(dfaVariable, getIteratedElement(iteratedValue));

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

    if (!addCountingLoopBound(statement)) {
      PsiStatement update = statement.getUpdate();
      if (update != null) {
        update.accept(this);
      }
    }

    ControlFlowOffset offset = initialization != null ? getEndOffset(initialization) : getStartOffset(statement);

    addInstruction(new GotoInstruction(offset));
    finishElement(statement);

    for (PsiElement declaredVariable : declaredVariables) {
      PsiVariable psiVariable = (PsiVariable)declaredVariable;
      myCurrentFlow.removeVariable(psiVariable);
    }
  }

  @Nullable
  private static Long asLong(PsiExpression expression) {
    Object value = ExpressionUtils.computeConstantExpression(expression);
    if(value instanceof Integer || value instanceof Long) {
      return ((Number)value).longValue();
    }
    return null;
  }

  /**
   * Add known-to-be-true condition inside counting loop, effectively converting
   * {@code for(int i=origin; i<bound; i++)} to
   * {@code int i = origin; while(i < bound) {... i++; if(i <= origin) break;}}.
   * This adds a range knowledge to data flow analysis.
   * <p>
   * Does nothing if the statement is not a counting loop.
   *
   * @param statement counting loop candidate.
   */
  private boolean addCountingLoopBound(PsiForStatement statement) {
    CountingLoop loop = CountingLoop.from(statement);
    if (loop == null || loop.isDescending()) return false;
    PsiLocalVariable counter = loop.getCounter();
    Long start = asLong(loop.getInitializer());
    Long end = asLong(loop.getBound());
    if (loop.isIncluding() && !(PsiType.LONG.equals(counter.getType()) && PsiType.INT.equals(loop.getBound().getType()))) {
      // could be for(int i=0; i<=Integer.MAX_VALUE; i++) which will overflow: conservatively skip this
      if (end == null || end == Long.MAX_VALUE || end == Integer.MAX_VALUE) return false;
    }
    PsiExpression initializer = loop.getInitializer();
    PsiType type = initializer.getType();
    if (!PsiType.INT.equals(type) && !PsiType.LONG.equals(type)) return false;
    DfaValue origin = null;
    Object initialValue = ExpressionUtils.computeConstantExpression(initializer);
    if (initialValue instanceof Number) {
      origin = myFactory.getConstFactory().createFromValue(initialValue, type, null);
    }
    else if (initializer instanceof PsiReferenceExpression) {
      PsiVariable initialVariable = ObjectUtils.tryCast(((PsiReferenceExpression)initializer).resolve(), PsiVariable.class);
      if ((initialVariable instanceof PsiLocalVariable || initialVariable instanceof PsiParameter)
        && !VariableAccessUtils.variableIsAssigned(initialVariable, statement.getBody())) {
        origin = myFactory.getVarFactory().createVariableValue(initialVariable);
      }
    }
    if (origin == null) return false;
    long diff = start == null || end == null ? -1 : end - start;
    DfaVariableValue loopVar = myFactory.getVarFactory().createVariableValue(counter);
    if(diff >= 0 && diff <= MAX_UNROLL_SIZE) {
      // Unroll small loops
      addInstruction(new PushInstruction(loopVar, null, true));
      addInstruction(new PushInstruction(loopVar, null));
      addInstruction(new PushInstruction(myFactory.getConstFactory().createFromValue(1, PsiType.INT, null), null));
      addInstruction(new BinopInstruction(JavaTokenType.PLUS, null, loopVar.getVariableType()));
      addInstruction(new AssignInstruction(null, null));
      addInstruction(new PopInstruction());
    }
    else if (start != null) {
      long maxValue;
      if (end != null) {
        maxValue = loop.isIncluding() ? end + 1 : end;
      }
      else {
        maxValue = type.equals(PsiType.LONG) ? Long.MAX_VALUE : Integer.MAX_VALUE;
      }
      if (start >= maxValue) {
        addInstruction(new GotoInstruction(getEndOffset(statement)));
      }
      else {
        DfaValue range = myFactory.getFactValue(DfaFactType.RANGE, LongRangeSet.range(start + 1L, maxValue));
        new CFGBuilder(this).assignAndPop(loopVar, range);
      }
    } else {
      new CFGBuilder(this).assign(loopVar, DfaUnknownValue.getInstance())
                          .push(origin)
                          .compare(JavaTokenType.LE);
      addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), false, null));
    }
    return true;
  }

  @Override public void visitIfStatement(PsiIfStatement statement) {
    startElement(statement);

    PsiExpression condition = statement.getCondition();

    PsiStatement thenStatement = statement.getThenBranch();
    PsiStatement elseStatement = statement.getElseBranch();

    ControlFlowOffset offset = elseStatement != null ? getStartOffset(elseStatement) : getEndOffset(statement);

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
    handleEscapedVariables(expression);
    addInstruction(new LambdaInstruction(expression));
    finishElement(expression);
  }

  private void handleEscapedVariables(PsiElement element) {
    Set<PsiLocalVariable> variables = new HashSet<>();
    Set<DfaVariableValue> escapedVars = new HashSet<>();
    element.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement target = expression.resolve();
        if (target instanceof PsiLocalVariable) {
          variables.add((PsiLocalVariable)target);
        }
        if (target instanceof PsiMember && !((PsiMember)target).hasModifierProperty(PsiModifier.STATIC)) {
          DfaVariableValue qualifier = getFactory().getExpressionFactory().getQualifierOrThisVariable(expression);
          if (qualifier != null) {
            escapedVars.add(qualifier);
          }
        }
      }

      @Override
      public void visitThisExpression(PsiThisExpression expression) {
        super.visitThisExpression(expression);
        DfaValue value = getFactory().createValue(expression);
        if (value instanceof DfaVariableValue) {
          escapedVars.add((DfaVariableValue)value);
        }
      }
    });
    for (DfaValue value : getFactory().getValues()) {
      if(value instanceof DfaVariableValue && !((DfaVariableValue)value).isNegated()) {
        PsiModifierListOwner var = ((DfaVariableValue)value).getPsiVariable();
        if (var instanceof PsiLocalVariable && variables.contains(var)) {
          escapedVars.add((DfaVariableValue)value);
        }
      }
    }
    if (!escapedVars.isEmpty()) {
      addInstruction(new EscapeInstruction(escapedVars));
    }
  }

  @Override public void visitReturnStatement(PsiReturnStatement statement) {
    startElement(statement);

    PsiExpression returnValue = statement.getReturnValue();

    if (myInlinedBlockContext != null) {
      if (returnValue != null) {
        DfaVariableValue var = myInlinedBlockContext.myTarget;
        addInstruction(new PushInstruction(var, null, true));
        returnValue.accept(this);
        generateBoxingUnboxingInstructionFor(returnValue, var.getVariableType());
        if (myInlinedBlockContext.myForceNonNullBlockResult) {
          addInstruction(new CheckNotNullInstruction(NullabilityProblemKind.nullableFunctionReturn.problem(returnValue)));
        }
        addInstruction(new AssignInstruction(returnValue, null));
        addInstruction(new PopInstruction());
      }

      controlTransfer(new InstructionTransfer(getEndOffset(myInlinedBlockContext.myCodeBlock), getVariablesInside(
        myInlinedBlockContext.myCodeBlock)), getTrapsInsideElement(myInlinedBlockContext.myCodeBlock));
    } else {

      if (returnValue != null) {
        returnValue.accept(this);
        PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, true, PsiMember.class, PsiLambdaExpression.class);
        if (method != null) {
          generateBoxingUnboxingInstructionFor(returnValue, method.getReturnType());
        }
        else {
          final PsiLambdaExpression lambdaExpression =
            PsiTreeUtil.getParentOfType(statement, PsiLambdaExpression.class, true, PsiMember.class);
          if (lambdaExpression != null) {
            generateBoxingUnboxingInstructionFor(returnValue, LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression));
          }
        }
        addInstruction(new CheckReturnValueInstruction(returnValue));
      }

      addInstruction(new ReturnInstruction(myFactory.controlTransfer(ReturnTransfer.INSTANCE, myTrapStack), statement));
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
        addInstruction(new DereferenceInstruction(caseExpression));
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
              ControlFlowOffset offset = getStartOffset(statement);
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
                addInstruction(new BinopInstruction(JavaTokenType.EQEQ, null, PsiType.BOOLEAN));
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
        ControlFlowOffset offset = defaultLabel != null ? getStartOffset(defaultLabel) : getEndOffset(body);
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
      addInstruction(new DereferenceInstruction(qualifier));
    }

    addInstruction(new PushInstruction(myFactory.createTypeValue(expression.getFunctionalInterfaceType(), Nullness.NOT_NULL), expression));

    finishElement(expression);
  }

  @Override public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
    startElement(statement);

    PsiExpression lock = statement.getLockExpression();
    if (lock != null) {
      lock.accept(this);
      addInstruction(new DereferenceInstruction(lock));
    }

    addInstruction(new FlushFieldsInstruction());

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

      addInstruction(new DereferenceInstruction(exception));
      throwException(exception.getType(), statement);
    }

    finishElement(statement);
  }

  private void addConditionalRuntimeThrow() {
    if (!shouldHandleException()) {
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

  private boolean shouldHandleException() {
    for (Trap trap : myTrapStack) {
      if (trap instanceof TryCatch || trap instanceof TryFinally || trap instanceof TwrFinally) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void visitTryStatement(PsiTryStatement statement) {
    startElement(statement);

    PsiResourceList resourceList = statement.getResourceList();
    PsiCodeBlock tryBlock = statement.getTryBlock();
    PsiCodeBlock finallyBlock = statement.getFinallyBlock();

    TryFinally finallyDescriptor = finallyBlock != null ? new TryFinally(finallyBlock, getStartOffset(finallyBlock)) : null;
    if (finallyDescriptor != null) {
      myTrapStack = myTrapStack.prepend(finallyDescriptor);
    }

    PsiCatchSection[] sections = statement.getCatchSections();
    if (sections.length > 0) {
      LinkedHashMap<PsiCatchSection, ControlFlowOffset> clauses = new LinkedHashMap<>();
      for (PsiCatchSection section : sections) {
        PsiCodeBlock catchBlock = section.getCatchBlock();
        if (catchBlock != null) {
          clauses.put(section, getStartOffset(catchBlock));
        }
      }
      myTrapStack = myTrapStack.prepend(new TryCatch(statement, clauses));
    }

    processTryWithResources(resourceList, tryBlock);

    InstructionTransfer gotoEnd = new InstructionTransfer(getEndOffset(statement), getVariablesInside(tryBlock));
    FList<Trap> singleFinally = FList.createFromReversed(ContainerUtil.createMaybeSingletonList(finallyDescriptor));
    controlTransfer(gotoEnd, singleFinally);

    if (sections.length > 0) {
      popTrap(TryCatch.class);
    }

    for (PsiCatchSection section : sections) {
      PsiCodeBlock catchBlock = section.getCatchBlock();
      if (catchBlock != null) {
        visitCodeBlock(catchBlock);
      }
      controlTransfer(gotoEnd, singleFinally);
    }

    if (finallyBlock != null) {
      popTrap(TryFinally.class);
      myTrapStack = myTrapStack.prepend(new InsideFinally(finallyBlock));

      finallyBlock.accept(this);
      controlTransfer(new ExitFinallyTransfer(finallyDescriptor), FList.emptyList());

      popTrap(InsideFinally.class);
    }

    finishElement(statement);
  }

  private void popTrap(Class<? extends Trap> aClass) {
    if (!aClass.isInstance(myTrapStack.getHead())) {
      throw new IllegalStateException("Unexpected trap-stack head (wanted: "+aClass.getSimpleName()+"); stack: "+myTrapStack);
    }
    myTrapStack = myTrapStack.getTail();
  }

  private void processTryWithResources(@Nullable PsiResourceList resourceList, @Nullable PsiCodeBlock tryBlock) {
    Set<PsiClassType> closerExceptions = Collections.emptySet();
    TwrFinally twrFinallyDescriptor = null;
    if (resourceList != null) {
      resourceList.accept(this);

      closerExceptions = StreamEx.of(resourceList.iterator()).flatCollection(ExceptionUtil::getCloserExceptions).toSet();
      if (!closerExceptions.isEmpty()) {
        twrFinallyDescriptor = new TwrFinally(resourceList, getStartOffset(resourceList));
        myTrapStack = myTrapStack.prepend(twrFinallyDescriptor);
      }
    }

    if (tryBlock != null) {
      tryBlock.accept(this);
    }

    if (twrFinallyDescriptor != null) {
      InstructionTransfer gotoEnd = new InstructionTransfer(getEndOffset(resourceList), getVariablesInside(tryBlock));
      controlTransfer(gotoEnd, FList.createFromReversed(ContainerUtil.createMaybeSingletonList(twrFinallyDescriptor)));
      popTrap(TwrFinally.class);
      myTrapStack = myTrapStack.prepend(new InsideFinally(resourceList));
      startElement(resourceList);
      addThrows(null, closerExceptions.toArray(PsiClassType.EMPTY_ARRAY));
      controlTransfer(new ExitFinallyTransfer(twrFinallyDescriptor), FList.emptyList()); // DfaControlTransferValue is on stack
      finishElement(resourceList);
      popTrap(InsideFinally.class);
    }
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
        addInstruction(new PopInstruction());
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

  @Nullable
  private DfaVariableValue getTargetVariable(PsiExpression expression) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (expression instanceof PsiArrayInitializerExpression && parent instanceof PsiNewExpression) {
      parent = PsiUtil.skipParenthesizedExprUp(parent.getParent());
    }
    if (parent instanceof PsiVariable) {
      // initialization
      return getFactory().getVarFactory().createVariableValue((PsiVariable)parent);
    }
    if (parent instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      if (assignmentExpression.getOperationTokenType().equals(JavaTokenType.EQ) &&
          PsiTreeUtil.isAncestor(assignmentExpression.getRExpression(), expression, false)) {
        DfaValue value = getFactory().createValue(assignmentExpression.getLExpression());
        if (value instanceof DfaVariableValue) {
          return (DfaVariableValue)value;
        }
      }
    }
    return null;
  }

  @Override
  public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
    startElement(expression);
    initializeArray(expression, expression);
    finishElement(expression);
  }

  private void initializeArray(PsiArrayInitializerExpression expression, PsiExpression originalExpression) {
    PsiType type = expression.getType();
    PsiType componentType = type instanceof PsiArrayType ? ((PsiArrayType)type).getComponentType() : null;
    DfaVariableValue var = getTargetVariable(expression);
    DfaVariableValue arrayWriteTarget = var;
    if (var == null) {
      var = createTempVariable(type);
    }
    PsiExpression[] initializers = expression.getInitializers();
    DfaExpressionFactory expressionFactory = myFactory.getExpressionFactory();
    if (arrayWriteTarget != null) {
      PsiVariable arrayVariable = (PsiVariable)arrayWriteTarget.getPsiVariable();
      if (arrayWriteTarget.isFlushableByCalls() ||
          arrayVariable == null ||
          VariableAccessUtils.variableIsUsed(arrayVariable, expression) ||
          ExpressionUtils.getConstantArrayElements(arrayVariable) != null ||
          !(expressionFactory.getArrayElementValue(arrayWriteTarget, 0) instanceof DfaVariableValue)) {
        arrayWriteTarget = null;
      }
    }
    DfaValue arrayValue = myFactory.withFact(myFactory.createTypeValue(type, Nullness.NOT_NULL), DfaFactType.LOCALITY, true);
    if (arrayWriteTarget != null) {
      addInstruction(new PushInstruction(arrayWriteTarget, null, true));
      addInstruction(new PushInstruction(arrayValue, expression));
      addInstruction(new AssignInstruction(originalExpression, arrayWriteTarget));
      int index = 0;
      for (PsiExpression initializer : initializers) {
        DfaValue target = Objects.requireNonNull(expressionFactory.getArrayElementValue(arrayWriteTarget, index++));
        addInstruction(new PushInstruction(target, null, true));
        initializer.accept(this);
        if (componentType != null) {
          generateBoxingUnboxingInstructionFor(initializer, componentType);
        }
        addInstruction(new AssignInstruction(initializer, null));
        addInstruction(new PopInstruction());
      }
    }
    else {
      Nullness nullability = Nullness.UNKNOWN;
      if (componentType != null) {
        nullability = DfaPsiUtil.getTypeNullability(componentType);
        if (nullability == Nullness.UNKNOWN && originalExpression != expression) {
          PsiType expectedType = ExpectedTypeUtils.findExpectedType(originalExpression, false);
          if (expectedType instanceof PsiArrayType) {
            nullability = DfaPsiUtil.getTypeNullability(((PsiArrayType)expectedType).getComponentType());
          }
        }
      }
      for (PsiExpression initializer : initializers) {
        initializer.accept(this);
        if (componentType != null) {
          generateBoxingUnboxingInstructionFor(initializer, componentType);
          if (nullability == Nullness.NOT_NULL) {
            addInstruction(new CheckNotNullInstruction(NullabilityProblemKind.storingToNotNullArray.problem(initializer)));
          }
        }
        addInstruction(new PopInstruction());
      }
      addInstruction(new PushInstruction(var, null, true));
      addInstruction(new PushInstruction(arrayValue, expression));
      addInstruction(new AssignInstruction(originalExpression, var));
    }
    // Declaration: write array length
    DfaConstValue lengthValue = getFactory().getInt(expression.getInitializers().length);
    new CFGBuilder(this).assignAndPop(SpecialField.ARRAY_LENGTH.createValue(getFactory(), var), lengthValue);
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
    addInstruction(new BinopInstruction(expression.getOperationTokenType(), expression.isPhysical() ? expression : null, type));
  }

  private void checkZeroDivisor() {
    addInstruction(new DupInstruction());
    addInstruction(new PushInstruction(myFactory.getConstFactory().createFromValue(0, PsiType.LONG, null), null));
    addInstruction(new BinopInstruction(JavaTokenType.NE, null, PsiType.BOOLEAN));
    ConditionalGotoInstruction ifNonZero = new ConditionalGotoInstruction(null, false, null);
    addInstruction(ifNonZero);
    throwException(JavaPsiFacade.getElementFactory(myProject).createTypeByFQClassName(ArithmeticException.class.getName()), null);
    ifNonZero.setOffset(myCurrentFlow.getInstructionCount());
  }

  private void generateOther(PsiPolyadicExpression expression, IElementType op, PsiExpression[] operands, PsiType type) {
    op = substituteBinaryOperation(expression, op);

    PsiExpression lExpr = operands[0];
    lExpr.accept(this);
    PsiType lType = lExpr.getType();

    for (int i = 1; i < operands.length; i++) {
      PsiExpression rExpr = operands[i];
      PsiType rType = rExpr.getType();

      acceptBinaryRightOperand(op, type, lExpr, lType, rExpr, rType);
      addInstruction(new BinopInstruction(op, expression.isPhysical() ? expression : null, type));

      lExpr = rExpr;
      lType = rType;
    }
  }

  @Nullable
  private IElementType substituteBinaryOperation(PsiPolyadicExpression expression, IElementType op) {
    if (JavaTokenType.PLUS == op) {
      if (TypeUtils.isJavaLangString(expression.getType()) || isAcceptableContextForMathOperation(expression)) return op;
      return null;
    }
    if (JavaTokenType.MINUS == op && !isAcceptableContextForMathOperation(expression)) return null;
    return op;
  }

  private boolean isAcceptableContextForMathOperation(PsiExpression expression) {
    PsiElement parent = expression.getParent();
    while (parent != null && parent != myCodeFragment) {
      if (parent instanceof PsiExpressionList ||
          parent instanceof PsiArrayInitializerExpression ||
          parent instanceof PsiArrayAccessExpression) {
        return true;
      }
      if (parent instanceof PsiBinaryExpression && RelationType.fromElementType(((PsiBinaryExpression)parent).getOperationTokenType()) != null) {
        return true;
      }
      if (parent instanceof PsiLoopStatement) return false;
      parent = parent.getParent();
    }
    return true;
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

  void generateBoxingUnboxingInstructionFor(@NotNull PsiExpression expression, PsiType expectedType) {
    generateBoxingUnboxingInstructionFor(expression, expression.getType(), expectedType);
  }

  void generateBoxingUnboxingInstructionFor(@NotNull PsiExpression context, PsiType actualType, PsiType expectedType) {
    if (PsiType.VOID.equals(expectedType)) return;

    if (TypeConversionUtil.isPrimitiveAndNotNull(expectedType) && TypeConversionUtil.isPrimitiveWrapper(actualType)) {
      addInstruction(new MethodCallInstruction(context, MethodType.UNBOXING, expectedType));
    }
    else if (TypeConversionUtil.isPrimitiveAndNotNull(actualType) && TypeConversionUtil.isAssignableFromPrimitiveWrapper(expectedType)) {
      addConditionalRuntimeThrow();
      addInstruction(new MethodCallInstruction(context, MethodType.BOXING, expectedType));
    }
    else if (actualType != expectedType &&
             TypeConversionUtil.isPrimitiveAndNotNull(actualType) &&
             TypeConversionUtil.isPrimitiveAndNotNull(expectedType) &&
             TypeConversionUtil.isNumericType(actualType) &&
             TypeConversionUtil.isNumericType(expectedType)) {
      addInstruction(new MethodCallInstruction(context, MethodType.CAST, expectedType) {
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
      addInstruction(new BinopInstruction(JavaTokenType.NE, psiAnchor, exprType));
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
    PsiTypeElement operand = expression.getOperand();
    DfaConstValue classConstant = myFactory.getConstFactory().createFromValue(operand.getType(), expression.getType(), null);
    addInstruction(new PushInstruction(classConstant, expression));
    finishElement(expression);
  }

  @Override public void visitConditionalExpression(PsiConditionalExpression expression) {
    startElement(expression);

    PsiExpression condition = expression.getCondition();

    PsiExpression thenExpression = expression.getThenExpression();
    PsiExpression elseExpression = expression.getElseExpression();

    final ControlFlowOffset elseOffset = elseExpression == null ? ControlFlow.deltaOffset(getEndOffset(expression), -1) : getStartOffset(elseExpression);
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

  void pushUnknown() {
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
      addInstruction(new PushInstruction(myFactory.createTypeValue(type, Nullness.NOT_NULL), null));
      addInstruction(new InstanceofInstruction(expression, operand, type));
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

  private void throwException(@Nullable PsiType ref, @Nullable PsiElement anchor) {
    if (ref != null) {
      throwException(new ExceptionTransfer(myFactory.createDfaType(ref)), anchor);
    }
  }

  private void throwException(ExceptionTransfer kind, @Nullable PsiElement anchor) {
    addInstruction(new ReturnInstruction(myFactory.controlTransfer(kind, myTrapStack), anchor));
  }

  @Override public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    startElement(expression);

    if (myInlining) {
      for (CallInliner inliner : INLINERS) {
        if (inliner.tryInlineCall(new CFGBuilder(this), expression)) {
          finishElement(expression);
          return;
        }
      }
    }

    PsiReferenceExpression methodExpression = expression.getMethodExpression();
    PsiExpression qualifierExpression = methodExpression.getQualifierExpression();

    if (qualifierExpression != null) {
      qualifierExpression.accept(this);
    }
    else {
      DfaValue thisVariable = myFactory.getExpressionFactory().getQualifierOrThisVariable(methodExpression);
      if (thisVariable != null) {
        addInstruction(new PushInstruction(thisVariable, null));
      }
      else {
        pushUnknown();
      }
    }

    PsiExpression[] expressions = expression.getArgumentList().getExpressions();
    JavaResolveResult result = methodExpression.advancedResolve(false);
    PsiElement method = result.getElement();
    PsiParameter[] parameters = method instanceof PsiMethod ? ((PsiMethod)method).getParameterList().getParameters() : null;

    for (int i = 0; i < expressions.length; i++) {
      PsiExpression paramExpr = expressions[i];
      paramExpr.accept(this);
      if (parameters != null && i < parameters.length) {
        generateBoxingUnboxingInstructionFor(paramExpr, result.getSubstitutor().substitute(parameters[i].getType()));
      }
    }

    addBareCall(expression, expression.getMethodExpression());
    finishElement(expression);
  }

  void addBareCall(@Nullable PsiMethodCallExpression expression, @NotNull PsiReferenceExpression reference) {
    addConditionalRuntimeThrow();
    PsiMethod method = ObjectUtils.tryCast(reference.resolve(), PsiMethod.class);
    List<? extends MethodContract> contracts = method == null ? Collections.emptyList() : JavaMethodContractUtil
      .getMethodCallContracts(method, expression);
    MethodCallInstruction instruction;
    PsiExpression anchor;
    if (expression == null) {
      assert reference instanceof PsiMethodReferenceExpression;
      instruction = new MethodCallInstruction((PsiMethodReferenceExpression)reference, contracts);
      anchor = reference;
    }
    else {
      instruction = new MethodCallInstruction(expression, myFactory.createValue(expression), contracts);
      anchor = expression;
    }
    addInstruction(instruction);
    if (contracts.stream().anyMatch(c -> c.getReturnValue().isFail())) {
      // if a contract resulted in 'fail', handle it
      addInstruction(new DupInstruction());
      addInstruction(new PushInstruction(myFactory.getConstFactory().getContractFail(), null));
      addInstruction(new BinopInstruction(JavaTokenType.EQEQ, null, PsiType.BOOLEAN));
      ConditionalGotoInstruction ifNotFail = new ConditionalGotoInstruction(null, true, null);
      addInstruction(ifNotFail);
      addInstruction(
        new ReturnInstruction(myFactory.controlTransfer(new ExceptionTransfer(null), myTrapStack), anchor));

      ifNotFail.setOffset(myCurrentFlow.getInstructionCount());
    }

    if (shouldHandleException()) {
      addMethodThrows(method, anchor);
    }
  }

  /**
   * @deprecated use {@link JavaMethodContractUtil#findContractAnnotation(PsiMethod)}.
   */
  @Deprecated
  @Nullable
  public static PsiAnnotation findContractAnnotation(@NotNull PsiMethod method) {
    return JavaMethodContractUtil.findContractAnnotation(method);
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

    PsiExpression qualifier = expression.getQualifier();
    if (qualifier != null) {
      qualifier.accept(this);
      addInstruction(new CheckNotNullInstruction(NullabilityProblemKind.innerClassNPE.problem(expression)));
      addInstruction(new PopInstruction());
    }

    PsiType type = expression.getType();
    if (type instanceof PsiArrayType) {
      PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
      if (arrayInitializer != null) {
        initializeArray(arrayInitializer, expression);
        return;
      }
      DfaVariableValue var = getTargetVariable(expression);
      if (var == null) {
        var = createTempVariable(type);
      }
      DfaValue length = SpecialField.ARRAY_LENGTH.createValue(getFactory(), var);
      addInstruction(new PushInstruction(length, null, true));
      // stack: ... var.length
      final PsiExpression[] dimensions = expression.getArrayDimensions();
      if (dimensions.length > 0) {
        boolean sizeOnStack = false;
        for (final PsiExpression dimension : dimensions) {
          dimension.accept(this);
          if (sizeOnStack) {
            addInstruction(new PopInstruction());
          }
          sizeOnStack = true;
        }
      }
      else {
        pushUnknown();
      }
      // stack: ... var.length actual_size
      addInstruction(new PushInstruction(var, null, true));
      DfaValue arrayValue = myFactory.withFact(myFactory.createTypeValue(type, Nullness.NOT_NULL), DfaFactType.LOCALITY, true);
      addInstruction(new PushInstruction(arrayValue, expression));
      addInstruction(new AssignInstruction(expression, var));
      // stack: ... var.length actual_size var
      addInstruction(new SpliceInstruction(3, 0, 2, 1));
      // stack: ... var var.length actual_size
      addInstruction(new AssignInstruction(null, length));
      addInstruction(new PopInstruction());
      // stack: ... var

      initializeSmallArray((PsiArrayType)type, var, dimensions);
    }
    else {
      pushUnknown(); // qualifier
      PsiMethod constructor = pushConstructorArguments(expression);
      PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
      if (anonymousClass != null) {
        handleEscapedVariables(anonymousClass);
      }

      addConditionalRuntimeThrow();
      addInstruction(new MethodCallInstruction(expression, null, constructor == null ? Collections.emptyList() : JavaMethodContractUtil
        .getMethodContracts(constructor)));

      if (shouldHandleException()) {
        addMethodThrows(constructor, expression);
      }
      setEmptyCollectionSize(expression);
    }

    finishElement(expression);
  }

  private void setEmptyCollectionSize(PsiNewExpression expression) {
    DfaVariableValue var = getTargetVariable(expression);
    if (var != null && ConstructionUtils.isEmptyCollectionInitializer(expression)) {
      DfaValue collectionValue =
        myFactory.withFact(myFactory.createTypeValue(expression.getType(), Nullness.NOT_NULL), DfaFactType.LOCALITY, true);
      SpecialField sizeField =
        InheritanceUtil.isInheritor(expression.getType(), JAVA_UTIL_MAP) ? SpecialField.MAP_SIZE : SpecialField.COLLECTION_SIZE;
      new CFGBuilder(this).pop()
                          .assign(var, collectionValue)
                          .assignAndPop(sizeField.createValue(myFactory, var), myFactory.getInt(0));
    }
  }

  private void initializeSmallArray(PsiArrayType type, DfaVariableValue var, PsiExpression[] dimensions) {
    if (dimensions.length != 1) return;
    PsiType componentType = type.getComponentType();
    // Ignore objects as they may produce false NPE warnings due to non-perfect loop handling
    if (!(componentType instanceof PsiPrimitiveType)) return;
    Object val = ExpressionUtils.computeConstantExpression(dimensions[0]);
    if (val instanceof Integer) {
      int lengthValue = (Integer)val;
      if (lengthValue > 0 && lengthValue <= MAX_UNROLL_SIZE) {
        for (int i = 0; i < lengthValue; i++) {
          DfaValue value = getFactory().getExpressionFactory().getArrayElementValue(var, i);
          addInstruction(new PushInstruction(value, null, true));
        }
        addInstruction(new PushInstruction(getFactory().getConstFactory().createDefault(componentType), null));
        for (int i = lengthValue - 1; i >= 0; i--) {
          DfaValue value = getFactory().getExpressionFactory().getArrayElementValue(var, i);
          addInstruction(new AssignInstruction(null, value));
        }
        addInstruction(new PopInstruction());
      }
    }
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
      pushUnknown();
      addInstruction(new AssignInstruction(operand, null, myFactory.createValue(operand)));
      addInstruction(new PopInstruction());
    }
    pushUnknown();

    finishElement(expression);
  }

  @Override public void visitPrefixExpression(PsiPrefixExpression expression) {
    startElement(expression);

    DfaValue dfaValue = myFactory.createValue(expression);
    if (dfaValue != null) {
      // Constant expression is computed: just push the result
      addInstruction(new PushInstruction(dfaValue, expression));
    }
    else {
      PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());

      if (operand == null) {
        pushUnknown();
      }
      else {
        operand.accept(this);
        PsiType type = expression.getType();
        PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(type);
        generateBoxingUnboxingInstructionFor(operand, unboxed == null ? type : unboxed);
        if (PsiUtil.isIncrementDecrementOperation(expression)) {
          pushUnknown();
          addInstruction(new AssignInstruction(operand, null, myFactory.createValue(operand)));
        }
        else if (expression.getOperationTokenType() == JavaTokenType.EXCL) {
          addInstruction(new NotInstruction());
        }
        else if (expression.getOperationTokenType() == JavaTokenType.MINUS && (PsiType.INT.equals(type) || PsiType.LONG.equals(type))) {
          addInstruction(new PushInstruction(myFactory.getConstFactory().createDefault(type), null));
          addInstruction(new SwapInstruction());
          addInstruction(new BinopInstruction(expression.getOperationTokenType(), expression, type));
        }
        else {
          addInstruction(new PopInstruction());
          pushUnknown();
        }
      }
    }

    finishElement(expression);
  }

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    startElement(expression);

    final PsiExpression qualifierExpression = expression.getQualifierExpression();
    if (qualifierExpression != null) {
      PsiElement target = expression.resolve();
      if (!(target instanceof PsiMember) || !((PsiMember)target).hasModifierProperty(PsiModifier.STATIC)) {
        qualifierExpression.accept(this);
        addInstruction(target instanceof PsiField ? new DereferenceInstruction(qualifierExpression) : new PopInstruction());
      }
    }

    // complex assignments (e.g. "|=") are both reading and writing
    boolean writing = PsiUtil.isAccessedForWriting(expression) && !PsiUtil.isAccessedForReading(expression);
    addInstruction(new PushInstruction(myFactory.createValue(expression), expression, writing));

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
          !UnnecessaryExplicitNumericCastInspection.isUnnecessaryPrimitiveNumericCast(castExpression)) {
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

  /**
   * Inline code block (lambda or method body) into this CFG. Incoming parameters are assumed to be handled already (if necessary)
   *
   * @param block block to inline
   * @param resultNullness desired nullness returned by block return statement
   * @param target a variable to store the block result (returned via {@code return} statement)
   */
  void inlineBlock(@NotNull PsiCodeBlock block, @NotNull Nullness resultNullness, @NotNull DfaVariableValue target) {
    InlinedBlockContext oldBlock = myInlinedBlockContext;
    // Transfer value is pushed to avoid emptying stack beyond this point
    myTrapStack = myTrapStack.prepend(new Trap.InsideInlinedBlock(block));
    addInstruction(new PushInstruction(myFactory.controlTransfer(ReturnTransfer.INSTANCE, FList.emptyList()), null));
    myInlinedBlockContext = new InlinedBlockContext(block, resultNullness == Nullness.NOT_NULL, target);
    startElement(block);
    block.accept(this);
    finishElement(block);
    myInlinedBlockContext = oldBlock;
    popTrap(Trap.InsideInlinedBlock.class);
    // Pop transfer value
    addInstruction(new PopInstruction());
  }

  /**
   * Create a synthetic variable (not declared in the original code) to be used within this control flow.
   *
   * @param type a type of variable to create
   * @return newly created variable
   */
  @NotNull
  DfaVariableValue createTempVariable(@Nullable PsiType type) {
    if(type == null) {
      type = PsiType.VOID;
    }
    return getFactory().getVarFactory().createVariableValue(new Synthetic(getInstructionCount()), type);
  }

  /**
   * Checks whether supplied variable is a temporary variable created previously via {@link #createTempVariable(PsiType)}
   *
   * @param variable to check
   * @return true if supplied variable is a temp variable.
   */
  public static boolean isTempVariable(@NotNull DfaVariableValue variable) {
    return variable.getSource() instanceof Synthetic;
  }

  /**
   * @param expression expression to test
   * @return true if some inliner may add constraints on the precise type of given expression
   */
  public static boolean inlinerMayInferPreciseType(PsiExpression expression) {
    return Arrays.stream(INLINERS).anyMatch(inliner -> inliner.mayInferPreciseType(expression));
  }

  private static final class Synthetic implements DfaVariableSource {
    private final int myLocation;

    public Synthetic(int location) {
      myLocation = location;
    }

    @NotNull
    @Override
    public String toString() {
      return "tmp$" + myLocation;
    }

    @Override
    public boolean isStable() {
      return true;
    }
  }

  public static class InlinedBlockContext {
    final PsiCodeBlock myCodeBlock;
    final boolean myForceNonNullBlockResult;
    final DfaVariableValue myTarget;

    public InlinedBlockContext(PsiCodeBlock codeBlock, boolean forceNonNullBlockResult, DfaVariableValue target) {
      myCodeBlock = codeBlock;
      myForceNonNullBlockResult = forceNonNullBlockResult;
      myTarget = target;
    }
  }

  static final CallInliner[] INLINERS = {new OptionalChainInliner(), new LambdaInliner(), new CollectionFactoryInliner(),
    new StreamChainInliner(), new MapUpdateInliner(), new AssumeInliner(), new ClassMethodsInliner()};
}

