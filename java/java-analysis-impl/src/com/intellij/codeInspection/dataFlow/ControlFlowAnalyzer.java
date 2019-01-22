// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInspection.dataFlow.ControlFlow.ControlFlowOffset;
import com.intellij.codeInspection.dataFlow.Trap.InsideFinally;
import com.intellij.codeInspection.dataFlow.Trap.TryCatch;
import com.intellij.codeInspection.dataFlow.Trap.TryFinally;
import com.intellij.codeInspection.dataFlow.Trap.TwrFinally;
import com.intellij.codeInspection.dataFlow.inliner.*;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.util.containers.FactoryMap;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.CommonClassNames.*;

public class ControlFlowAnalyzer extends JavaElementVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer");
  private static final CallMatcher LIST_INITIALIZER = CallMatcher.anyOf(
    CallMatcher.staticCall(JAVA_UTIL_ARRAYS, "asList"),
    CallMatcher.staticCall(JAVA_UTIL_LIST, "of"));
  static final int MAX_UNROLL_SIZE = 3;
  private static final int MAX_ARRAY_INDEX_FOR_INITIALIZER = 32;
  private final PsiElement myCodeFragment;
  private final boolean myIgnoreAssertions;
  private final boolean myInlining;
  private final Project myProject;

  private static class CannotAnalyzeException extends RuntimeException { }

  private final DfaValueFactory myFactory;
  private ControlFlow myCurrentFlow;
  private FList<Trap> myTrapStack = FList.emptyList();
  private final Map<PsiExpression, NullabilityProblemKind<? super PsiExpression>> myCustomNullabilityProblems = new HashMap<>();
  private final Map<String, ExceptionTransfer> myExceptionCache;
  private ExpressionBlockContext myExpressionBlockContext;

  ControlFlowAnalyzer(final DfaValueFactory valueFactory, @NotNull PsiElement codeFragment, boolean ignoreAssertions, boolean inlining) {
    myInlining = inlining;
    myFactory = valueFactory;
    myCodeFragment = codeFragment;
    myProject = codeFragment.getProject();
    myIgnoreAssertions = ignoreAssertions;
    GlobalSearchScope scope = codeFragment.getResolveScope();
    myExceptionCache = FactoryMap.create(fqn -> new ExceptionTransfer(myFactory.createDfaType(createClassType(scope, fqn))));
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
        ImplicitUsageProvider.EP_NAME.getExtensionList().stream().anyMatch(p -> p.isClassWithCustomizedInitialization(psiClass))) {
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
    assert !(element instanceof PsiExpression) || element instanceof PsiSwitchExpression; 
    return myCurrentFlow.getEndOffset(element);
  }

  private ControlFlowOffset getStartOffset(PsiElement element) {
    assert !(element instanceof PsiExpression) || element instanceof PsiSwitchExpression;
    return myCurrentFlow.getStartOffset(element);
  }

  private void startElement(PsiElement element) {
    myCurrentFlow.startElement(element);
  }

  private void finishElement(PsiElement element) {
    myCurrentFlow.finishElement(element);
    if (element instanceof PsiField || (element instanceof PsiStatement && !(element instanceof PsiReturnStatement) && 
        !(element instanceof PsiSwitchLabeledRuleStatement))) {
      List<DfaVariableValue> synthetics = getSynthetics(element);
      FinishElementInstruction instruction = new FinishElementInstruction(element);
      instruction.getVarsToFlush().addAll(synthetics);
      addInstruction(instruction);
    }
  }

  @NotNull
  private List<DfaVariableValue> getSynthetics(PsiElement element) {
    int startOffset = getStartOffset(element).getInstructionOffset();
    List<DfaVariableValue> synthetics = new ArrayList<>();
    for (DfaValue value : myFactory.getValues()) {
      if (value instanceof DfaVariableValue) {
        DfaVariableValue var = (DfaVariableValue)value;
        VariableDescriptor descriptor = var.getDescriptor();
        if (descriptor instanceof Synthetic) {
          if (((Synthetic)descriptor).myLocation >= startOffset) {
            synthetics.add(var);
          }
        }
      }
    }
    return synthetics;
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
    else {
      IElementType sign = TypeConversionUtil.convertEQtoOperation(op);
      PsiType resType = TypeConversionUtil.calcTypeForBinaryExpression(lExpr.getType(), rExpr.getType(), sign, true);
      lExpr.accept(this);
      addInstruction(new DupInstruction());
      generateBoxingUnboxingInstructionFor(lExpr, resType);
      rExpr.accept(this);
      generateBoxingUnboxingInstructionFor(rExpr, resType);
      sign = substituteBinaryOperation(rExpr, sign);
      if (isAssignmentDivision(op) && resType != null && PsiType.LONG.isAssignableFrom(resType)) {
        checkZeroDivisor();
      }
      addInstruction(new BinopInstruction(sign, expression.isPhysical() ? expression : null, resType));
      generateBoxingUnboxingInstructionFor(rExpr, resType, type);
    }

    addInstruction(new AssignInstruction(rExpr, myFactory.createValue(lExpr)));

    finishElement(expression);
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

      throwException(myExceptionCache.get(JAVA_LANG_ASSERTION_ERROR), statement);
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
      startElement(field);
      initializeVariable(field, initializer);
      finishElement(field);
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
    PsiElement exitedElement = statement.findExitedElement();
    if (exitedElement instanceof PsiSwitchExpression &&
        myExpressionBlockContext != null && myExpressionBlockContext.myCodeBlock == ((PsiSwitchExpression)exitedElement).getBody()) {
      myExpressionBlockContext.generateReturn(statement.getExpression(), this);
    } else {
      jumpOut(exitedElement);
    }
    finishElement(statement);
  }

  void addNullCheck(PsiExpression expression) {
    addNullCheck(NullabilityProblemKind.fromContext(expression, myCustomNullabilityProblems));
  }

  void addNullCheck(NullabilityProblemKind.NullabilityProblem<?> problem) {
    if (problem != null) {
      DfaControlTransferValue transfer = shouldHandleException() && problem.thrownException() != null 
                                         ? myFactory.controlTransfer(myExceptionCache.get(problem.thrownException()), myTrapStack) : null;
      addInstruction(new CheckNotNullInstruction(problem, transfer));
    }
  }

  private void jumpOut(PsiElement exitedStatement) {
    if (exitedStatement != null && PsiTreeUtil.isAncestor(myCodeFragment, exitedStatement, false)) {
      controlTransfer(new InstructionTransfer(getEndOffset(exitedStatement), getVariablesInside(exitedStatement)),
                      getTrapsInsideElement(exitedStatement));
    } else {
      // Jumping out of analyzed code fragment
      controlTransfer(ReturnTransfer.INSTANCE, getTrapsInsideElement(myCodeFragment));
    }
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

  private DfaValue getIteratedElement(PsiType type, PsiExpression iteratedValue) {
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
    return expressions == null ? DfaUnknownValue.getInstance() : getFactory().createCommonValue(expressions, type);
  }

  @Override public void visitForeachStatement(PsiForeachStatement statement) {
    startElement(statement);
    final PsiParameter parameter = statement.getIterationParameter();
    final PsiExpression iteratedValue = PsiUtil.skipParenthesizedExprDown(statement.getIteratedValue());

    ControlFlowOffset loopEndOffset = getEndOffset(statement);
    boolean hasSizeCheck = false;

    if (iteratedValue != null) {
      iteratedValue.accept(this);

      PsiType type = iteratedValue.getType();
      SpecialField length = null;
      if (type instanceof PsiArrayType) {
        length = SpecialField.ARRAY_LENGTH;
      }
      else if (InheritanceUtil.isInheritor(type, JAVA_UTIL_COLLECTION)) {
        length = SpecialField.COLLECTION_SIZE;
      }
      if (length != null) {
        addInstruction(new UnwrapSpecialFieldInstruction(length));
        addInstruction(new PushInstruction(myFactory.getInt(0), null));
        addInstruction(new BinopInstruction(JavaTokenType.EQEQ, null, PsiType.BOOLEAN));
        addInstruction(new ConditionalGotoInstruction(loopEndOffset, false, null));
        hasSizeCheck = true;
      } else {
        addInstruction(new PopInstruction());
      }
    }

    ControlFlowOffset offset = myCurrentFlow.getNextOffset();
    DfaVariableValue dfaVariable = myFactory.getVarFactory().createVariableValue(parameter);
    new CFGBuilder(this).assignAndPop(dfaVariable, getIteratedElement(parameter.getType(), iteratedValue));

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
    PsiType type = loop.getCounter().getType();
    if (!PsiType.INT.equals(type) && !PsiType.LONG.equals(type)) return false;
    DfaValue origin = null;
    Object initialValue = ExpressionUtils.computeConstantExpression(initializer);
    if (initialValue instanceof Number) {
      origin = myFactory.getConstFactory().createFromValue(initialValue, type);
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
      addInstruction(new PushInstruction(myFactory.getConstFactory().createFromValue(1, PsiType.INT), null));
      addInstruction(new BinopInstruction(JavaTokenType.PLUS, null, loopVar.getType()));
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
      if(value instanceof DfaVariableValue) {
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

    if (myExpressionBlockContext != null) {
      // We treat return inside switch expression (which is disallowed syntax) as break-with-value
      myExpressionBlockContext.generateReturn(returnValue, this);
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
        addInstruction(new PopInstruction());
      }

      addInstruction(new ReturnInstruction(myFactory.controlTransfer(ReturnTransfer.INSTANCE, myTrapStack), statement));
    }
    finishElement(statement);
  }

  @Override public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    startElement(statement);
    finishElement(statement);
  }

  @Override
  public void visitSwitchLabeledRuleStatement(PsiSwitchLabeledRuleStatement statement) {
    PsiSwitchBlock switchBlock = statement.getEnclosingSwitchBlock();
    if (switchBlock == null) return;
    startElement(statement);
    PsiStatement body = statement.getBody();
    PsiCodeBlock switchBody = switchBlock.getBody();
    boolean expressionSwitch = myExpressionBlockContext != null && myExpressionBlockContext.myCodeBlock == switchBody;
    if (expressionSwitch && body instanceof PsiExpressionStatement) {
      myExpressionBlockContext.generateReturn(((PsiExpressionStatement)body).getExpression(), this);
    } else {
      if (body != null) {
        body.accept(this);
      }
      if (ControlFlowUtils.statementMayCompleteNormally(body)) {
        jumpOut(expressionSwitch ? switchBody : switchBlock);
      }
    }
    finishElement(statement);
  }

  @Override public void visitSwitchStatement(PsiSwitchStatement switchStmt) {
    startElement(switchStmt);
    processSwitch(switchStmt);
    finishElement(switchStmt);
  }

  @Override
  public void visitSwitchExpression(PsiSwitchExpression expression) {
    PsiCodeBlock body = expression.getBody();
    if (body == null) {
      processSwitch(expression);
      pushUnknown();
    } else {
      startElement(expression);
      DfaVariableValue resultVariable = createTempVariable(expression.getType());
      enterExpressionBlock(body, Nullability.UNKNOWN, resultVariable);
      processSwitch(expression);
      exitExpressionBlock();
      addInstruction(new PushInstruction(resultVariable, expression));
      finishElement(expression);
    }
  }

  private void processSwitch(@NotNull PsiSwitchBlock switchBlock) {
    PsiExpression selector = PsiUtil.skipParenthesizedExprDown(switchBlock.getExpression());
    Set<PsiEnumConstant> enumValues = null;
    DfaVariableValue expressionValue = null;
    boolean syntheticVar = true;
    if (selector != null) {
      PsiType targetType = selector.getType();
      PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(targetType);
      if (unboxedType != null) {
        targetType = unboxedType;
      } else {
        DfaValue selectorValue = myFactory.createValue(selector);
        if (selectorValue instanceof DfaVariableValue && !((DfaVariableValue)selectorValue).isFlushableByCalls()) {
          expressionValue = (DfaVariableValue)selectorValue;
          syntheticVar = false;
        }
      }
      if (syntheticVar) {
        expressionValue = createTempVariable(targetType);
        addInstruction(new PushInstruction(expressionValue, null, true));
      }
      selector.accept(this);
      generateBoxingUnboxingInstructionFor(selector, targetType);
      final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(targetType);
      if (psiClass != null) {
        if (psiClass.isEnum()) {
          enumValues = new HashSet<>();
          for (PsiField f : psiClass.getFields()) {
            if (f instanceof PsiEnumConstant) {
              enumValues.add((PsiEnumConstant)f);
            }
          }
        }
      }
      if (syntheticVar) {
        addInstruction(new AssignInstruction(null, null));
      }
      addInstruction(new PopInstruction());
    }

    PsiCodeBlock body = switchBlock.getBody();

    if (body != null) {
      PsiStatement[] statements = body.getStatements();
      ControlFlowOffset offset = null;
      PsiSwitchLabelStatementBase defaultLabel = null;
      for (PsiStatement statement : statements) {
        if (statement instanceof PsiSwitchLabelStatementBase) {
          PsiSwitchLabelStatementBase psiLabelStatement = (PsiSwitchLabelStatementBase)statement;
          if (psiLabelStatement.isDefaultCase()) {
            defaultLabel = psiLabelStatement;
          }
          else {
            try {
              offset = getStartOffset(statement);
              PsiExpressionList values = psiLabelStatement.getCaseValues();
              if (values != null) {
                for (PsiExpression caseValue : values.getExpressions()) {

                  if (enumValues != null && caseValue instanceof PsiReferenceExpression) {
                    //noinspection SuspiciousMethodCalls
                    enumValues.remove(((PsiReferenceExpression)caseValue).resolve());
                  }

                  if (caseValue != null && expressionValue != null) {
                    addInstruction(new PushInstruction(expressionValue, null));
                    caseValue.accept(this);
                    addInstruction(new BinopInstruction(JavaTokenType.EQEQ, null, PsiType.BOOLEAN));
                  }
                  else {
                    pushUnknown();
                  }

                  addInstruction(new ConditionalGotoInstruction(offset, false, caseValue));
                }
              }
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }

      if (offset == null || enumValues == null || !enumValues.isEmpty()) {
        offset = defaultLabel != null ? getStartOffset(defaultLabel) : getEndOffset(body);
      } // else default label goes to the last switch label making it always true
      addInstruction(new GotoInstruction(offset));

      body.accept(this);
    }

    if (syntheticVar && expressionValue != null) {
      addInstruction(new FlushVariableInstruction(expressionValue));
    }
  }

  @Override
  public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
    startElement(expression);

    PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier != null) {
      qualifier.accept(this);
    } else {
      pushUnknown();
    }

    addInstruction(new MethodReferenceInstruction(expression));

    finishElement(expression);
  }

  @Override public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
    startElement(statement);

    PsiExpression lock = statement.getLockExpression();
    if (lock != null) {
      lock.accept(this);
      addInstruction(new PopInstruction());
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

      addInstruction(new PopInstruction());
      throwException(exception.getType(), statement);
    }

    finishElement(statement);
  }

  void addConditionalRuntimeThrow() {
    if (!shouldHandleException()) {
      return;
    }

    pushUnknown();
    final ConditionalGotoInstruction ifNoException = addInstruction(new ConditionalGotoInstruction(null, false, null));

    pushUnknown();
    final ConditionalGotoInstruction ifError = addInstruction(new ConditionalGotoInstruction(null, false, null));
    throwException(myExceptionCache.get(JAVA_LANG_RUNTIME_EXCEPTION), null);
    ifError.setOffset(myCurrentFlow.getInstructionCount());
    throwException(myExceptionCache.get(JAVA_LANG_ERROR), null);

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
      pushTrap(finallyDescriptor);
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
      pushTrap(new TryCatch(statement, clauses));
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
      pushTrap(new InsideFinally(finallyBlock));

      finallyBlock.accept(this);
      controlTransfer(new ExitFinallyTransfer(finallyDescriptor), FList.emptyList());

      popTrap(InsideFinally.class);
    }

    finishElement(statement);
  }

  void pushTrap(Trap elem) {
    myTrapStack = myTrapStack.prepend(elem);
  }

  void popTrap(Class<? extends Trap> aClass) {
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
        pushTrap(twrFinallyDescriptor);
      }
    }

    if (tryBlock != null) {
      tryBlock.accept(this);
    }

    if (twrFinallyDescriptor != null) {
      InstructionTransfer gotoEnd = new InstructionTransfer(getEndOffset(resourceList), getVariablesInside(tryBlock));
      controlTransfer(gotoEnd, FList.createFromReversed(ContainerUtil.createMaybeSingletonList(twrFinallyDescriptor)));
      popTrap(TwrFinally.class);
      pushTrap(new InsideFinally(resourceList));
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
      toPush = myFactory.createTypeValue(expression.getType(), Nullability.UNKNOWN);
    }
    addInstruction(new ArrayAccessInstruction(toPush, expression));
    addNullCheck(expression);
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
      PsiVariable arrayVariable = ObjectUtils.tryCast(arrayWriteTarget.getPsiVariable(), PsiVariable.class);
      if (arrayWriteTarget.isFlushableByCalls() ||
          arrayVariable == null ||
          VariableAccessUtils.variableIsUsed(arrayVariable, expression) ||
          ExpressionUtils.getConstantArrayElements(arrayVariable) != null ||
          !(expressionFactory.getArrayElementValue(arrayWriteTarget, 0) instanceof DfaVariableValue)) {
        arrayWriteTarget = null;
      }
    }
    DfaFactMap arrayFacts = DfaFactMap.EMPTY
      .with(DfaFactType.TYPE_CONSTRAINT, type == null ? null : TypeConstraint.exact(myFactory.createDfaType(type)))
      .with(DfaFactType.NULLABILITY, DfaNullability.NOT_NULL)
      .with(DfaFactType.LOCALITY, true)
      .with(DfaFactType.SPECIAL_FIELD_VALUE, SpecialField.ARRAY_LENGTH.withValue(myFactory.getInt(expression.getInitializers().length)));
    DfaValue arrayValue = myFactory.getFactFactory().createValue(arrayFacts);
    if (arrayWriteTarget != null) {
      addInstruction(new PushInstruction(arrayWriteTarget, null, true));
      addInstruction(new PushInstruction(arrayValue, expression));
      addInstruction(new AssignInstruction(originalExpression, arrayWriteTarget));
      int index = 0;
      for (PsiExpression initializer : initializers) {
        if (index < MAX_ARRAY_INDEX_FOR_INITIALIZER) {
          DfaValue target = Objects.requireNonNull(expressionFactory.getArrayElementValue(arrayWriteTarget, index));
          addInstruction(new PushInstruction(target, null, true));
        }
        initializer.accept(this);
        if (componentType != null) {
          generateBoxingUnboxingInstructionFor(initializer, componentType);
        }
        if (index < MAX_ARRAY_INDEX_FOR_INITIALIZER) {
          addInstruction(new AssignInstruction(initializer, null));
        }
        index++;
        addInstruction(new PopInstruction());
      }
    }
    else {
      for (PsiExpression initializer : initializers) {
        initializer.accept(this);
        if (componentType != null) {
          generateBoxingUnboxingInstructionFor(initializer, componentType);
        }
        addInstruction(new PopInstruction());
      }
      addInstruction(new PushInstruction(var, null, true));
      addInstruction(new PushInstruction(arrayValue, expression));
      addInstruction(new AssignInstruction(originalExpression, var));
    }
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
      generateShortCircuitAndOr(expression, operands, type, true);
    }
    else if (op == JavaTokenType.OROR) {
      generateShortCircuitAndOr(expression, operands, type, false);
    }
    else if (op == JavaTokenType.XOR && PsiType.BOOLEAN.equals(type)) {
      generateXorExpression(expression, operands, type, false);
    }
    else if (op == JavaTokenType.AND && PsiType.BOOLEAN.equals(type)) {
      generateAndOr(operands, type, true);
    }
    else if (op == JavaTokenType.OR && PsiType.BOOLEAN.equals(type)) {
      generateAndOr(operands, type, false);
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
    Object divisorValue = ExpressionUtils.computeConstantExpression(right);
    if (!(divisorValue instanceof Number) || (((Number)divisorValue).longValue() == 0)) {
      checkZeroDivisor();
    }
    addInstruction(new BinopInstruction(expression.getOperationTokenType(), expression.isPhysical() ? expression : null, type));
  }

  private void checkZeroDivisor() {
    addInstruction(new DupInstruction());
    addInstruction(new PushInstruction(myFactory.getConstFactory().createFromValue(0, PsiType.LONG), null));
    addInstruction(new BinopInstruction(JavaTokenType.NE, null, PsiType.BOOLEAN));
    ConditionalGotoInstruction ifNonZero = new ConditionalGotoInstruction(null, false, null);
    addInstruction(ifNonZero);
    throwException(myExceptionCache.get(ArithmeticException.class.getName()), null);
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
      addInstruction(new BinopInstruction(op, expression, type, i));

      lExpr = rExpr;
      lType = rType;
    }
  }

  @Nullable
  private IElementType substituteBinaryOperation(PsiExpression expression, IElementType op) {
    if (JavaTokenType.PLUS == op) {
      if (TypeUtils.isJavaLangString(expression.getType()) || isAcceptableContextForMathOperation(expression)) return op;
      return null;
    }
    if ((JavaTokenType.MINUS == op || JavaTokenType.ASTERISK == op) && !isAcceptableContextForMathOperation(expression)) return null;
    return op;
  }

  private boolean isAcceptableContextForMathOperation(PsiExpression expression) {
    PsiElement parent = expression.getParent();
    while (parent != null && parent != myCodeFragment) {
      if ((parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCallExpression) ||
          parent instanceof PsiArrayInitializerExpression ||
          parent instanceof PsiArrayAccessExpression) {
        return true;
      }
      if (parent instanceof PsiBinaryExpression && RelationType.fromElementType(((PsiBinaryExpression)parent).getOperationTokenType()) != null) {
        return true;
      }
      if (parent instanceof PsiLoopStatement &&
          !(parent instanceof PsiForStatement &&
            PsiTreeUtil.isAncestor(((PsiForStatement)parent).getInitialization(), expression, false))) {
        return false;
      }
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

    PsiType castType = comparingPrimitiveNumeric ? TypeConversionUtil.unboxAndBalanceTypes(lType, rType) : type;

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
      addInstruction(new UnwrapSpecialFieldInstruction(SpecialField.UNBOX));
    }
    else if (TypeConversionUtil.isPrimitiveAndNotNull(actualType) && TypeConversionUtil.isAssignableFromPrimitiveWrapper(expectedType)) {
      addConditionalRuntimeThrow();
      PsiType boxedType = ((PsiPrimitiveType)actualType).getBoxedType(context);
      addInstruction(new BoxingInstruction(boxedType));
    }
    else if (actualType != expectedType &&
             TypeConversionUtil.isPrimitiveAndNotNull(actualType) &&
             TypeConversionUtil.isPrimitiveAndNotNull(expectedType) &&
             TypeConversionUtil.isNumericType(actualType) &&
             TypeConversionUtil.isNumericType(expectedType)) {
      addInstruction(new PrimitiveConversionInstruction((PsiPrimitiveType)expectedType, context));
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
      PsiExpression psiAnchor = expression.isPhysical() ? expression : null;
      addInstruction(new BinopInstruction(JavaTokenType.NE, psiAnchor, exprType, i));
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

  private void generateAndOr(PsiExpression[] operands, PsiType exprType, boolean and) {
    for (int i = 0; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(operand, exprType);
      if (i > 0) {
        combineStackBooleans(and, operand);
      }
    }
  }

  private void generateShortCircuitAndOr(PsiExpression expression, PsiExpression[] operands, PsiType exprType, boolean and) {
    ControlFlow.DeferredOffset endOffset = new ControlFlow.DeferredOffset();
    for (int i = 0; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(operand, exprType);

      PsiExpression nextOperand = i == operands.length - 1 ? null : operands[i + 1];
      if (nextOperand != null) {
        ControlFlow.DeferredOffset nextOffset = new ControlFlow.DeferredOffset();
        addInstruction(new ConditionalGotoInstruction(nextOffset, !and, operand));
        addInstruction(new PushInstruction(myFactory.getBoolean(!and), expression));
        addInstruction(new GotoInstruction(endOffset));
        nextOffset.setOffset(getInstructionCount());
        addInstruction(new FinishElementInstruction(null));
      }
    }
    endOffset.setOffset(getInstructionCount());
    addInstruction(new ResultOfInstruction(expression));
  }

  @Override public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    startElement(expression);
    PsiTypeElement operand = expression.getOperand();
    DfaConstValue classConstant = myFactory.getConstFactory().createFromValue(operand.getType(), expression.getType());
    addInstruction(new PushInstruction(classConstant, expression));
    finishElement(expression);
  }

  @Override public void visitConditionalExpression(PsiConditionalExpression expression) {
    startElement(expression);

    PsiExpression condition = expression.getCondition();

    PsiExpression thenExpression = expression.getThenExpression();
    PsiExpression elseExpression = expression.getElseExpression();

    ControlFlow.DeferredOffset elseOffset = new ControlFlow.DeferredOffset();
    if (thenExpression != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
      PsiType type = expression.getType();
      addInstruction(new ConditionalGotoInstruction(elseOffset, true, PsiUtil.skipParenthesizedExprDown(condition)));
      thenExpression.accept(this);
      generateBoxingUnboxingInstructionFor(thenExpression,type);

      ControlFlow.DeferredOffset endOffset = new ControlFlow.DeferredOffset();
      addInstruction(new GotoInstruction(endOffset));

      elseOffset.setOffset(getInstructionCount());
      if (elseExpression != null) {
        elseExpression.accept(this);
        generateBoxingUnboxingInstructionFor(elseExpression,type);
      }
      else {
        pushUnknown();
      }
      endOffset.setOffset(getInstructionCount());
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
      addInstruction(new PushInstruction(myFactory.createTypeValue(type, Nullability.NOT_NULL), null));
      addInstruction(new InstanceofInstruction(expression, operand, type));
    }
    else {
      pushUnknown();
    }

    finishElement(expression);
  }

  void addMethodThrows(PsiMethod method, @Nullable PsiElement explicitCall) {
    if (method != null && shouldHandleException()) {
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

  void throwException(@Nullable PsiType ref, @Nullable PsiElement anchor) {
    if (ref != null) {
      throwException(new ExceptionTransfer(myFactory.createDfaType(ref)), anchor);
    }
  }

  private void throwException(ExceptionTransfer kind, @Nullable PsiElement anchor) {
    addInstruction(new ReturnInstruction(myFactory.controlTransfer(kind, myTrapStack), anchor));
  }

  @Override
  public void visitMethodCallExpression(PsiMethodCallExpression call) {
    ArrayDeque<PsiMethodCallExpression> calls = new ArrayDeque<>();
    while (true) {
      calls.addFirst(call);
      startElement(call);

      if (tryInline(call)) {
        finishElement(call);
        calls.removeFirst();
        break;
      }

      PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();

      if (qualifierExpression == null) {
        DfaValue thisVariable = myFactory.getExpressionFactory().getQualifierOrThisVariable(call.getMethodExpression());
        if (thisVariable != null) {
          addInstruction(new PushInstruction(thisVariable, null));
        }
        else {
          pushUnknown();
        }
        break;
      }
      call = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(qualifierExpression), PsiMethodCallExpression.class);
      if (call == null) {
        qualifierExpression.accept(this);
        break;
      }
    }

    calls.forEach(this::finishCall);
  }

  private boolean tryInline(PsiMethodCallExpression call) {
    if (myInlining) {
      for (CallInliner inliner: INLINERS) {
        if (inliner.tryInlineCall(new CFGBuilder(this), call)) {
          addNullCheck(call);
          return true;
        }
      }
    }
    return false;
  }

  private void finishCall(PsiMethodCallExpression call) {
    PsiExpression[] expressions = call.getArgumentList().getExpressions();
    JavaResolveResult result = call.getMethodExpression().advancedResolve(false);
    PsiElement method = result.getElement();
    PsiParameter[] parameters = method instanceof PsiMethod ? ((PsiMethod)method).getParameterList().getParameters() : null;

    for (int i = 0; i < expressions.length; i++) {
      PsiExpression paramExpr = expressions[i];
      paramExpr.accept(this);
      if (parameters != null && i < parameters.length) {
        generateBoxingUnboxingInstructionFor(paramExpr, result.getSubstitutor().substitute(parameters[i].getType()));
      }
    }

    addBareCall(call, call.getMethodExpression());
    finishElement(call);
  }

  void addBareCall(@Nullable PsiMethodCallExpression expression, @NotNull PsiReferenceExpression reference) {
    addConditionalRuntimeThrow();
    PsiMethod method = ObjectUtils.tryCast(reference.resolve(), PsiMethod.class);
    List<? extends MethodContract> contracts = method == null ? Collections.emptyList() : JavaMethodContractUtil
      .getMethodCallContracts(method, expression);
    PsiExpression anchor;
    if (expression == null) {
      assert reference instanceof PsiMethodReferenceExpression;
      addInstruction(new MethodCallInstruction((PsiMethodReferenceExpression)reference, contracts));
      anchor = reference;
    }
    else {
      addInstruction(new MethodCallInstruction(expression, myFactory.createValue(expression), contracts));
      addNullCheck(expression);
      anchor = expression;
    }
    if (contracts.stream().anyMatch(c -> c.getReturnValue().isFail())) {
      // if a contract resulted in 'fail', handle it
      addInstruction(new DupInstruction());
      addInstruction(new PushInstruction(myFactory.getConstFactory().getContractFail(), null));
      addInstruction(new BinopInstruction(JavaTokenType.EQEQ, null, PsiType.BOOLEAN));
      ConditionalGotoInstruction ifNotFail = new ConditionalGotoInstruction(null, true, null);
      addInstruction(ifNotFail);
      addInstruction(new ReturnInstruction(myFactory.controlTransfer(new ExceptionTransfer(null), myTrapStack), anchor));

      ifNotFail.setOffset(myCurrentFlow.getInstructionCount());
    }

    addMethodThrows(method, anchor);
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
      DfaValue arrayValue = myFactory.withFact(myFactory.createExactTypeValue(type), DfaFactType.LOCALITY, true);
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
      DfaValue precalculatedNewValue = getPrecalculatedNewValue(expression);
      List<? extends MethodContract> contracts = constructor == null ? Collections.emptyList() : JavaMethodContractUtil.getMethodContracts(constructor);
      addInstruction(new MethodCallInstruction(expression, precalculatedNewValue, contracts));

      addMethodThrows(constructor, expression);
    }

    finishElement(expression);
  }
  
  private DfaValue getPrecalculatedNewValue(PsiNewExpression expression) {
    PsiType type = expression.getType();
    if (type != null && ConstructionUtils.isEmptyCollectionInitializer(expression)) {
      DfaFactMap facts = DfaFactMap.EMPTY
        .with(DfaFactType.TYPE_CONSTRAINT, TypeConstraint.exact(myFactory.createDfaType(type)))
        .with(DfaFactType.NULLABILITY, DfaNullability.NOT_NULL)
        .with(DfaFactType.LOCALITY, true)
        .with(DfaFactType.SPECIAL_FIELD_VALUE, SpecialField.COLLECTION_SIZE.withValue(myFactory.getInt(0)));
      return myFactory.getFactFactory().createValue(facts);
    }
    return null;
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

    DfaValue dfaValue = expression.getOperationTokenType() == JavaTokenType.EXCL ? null : myFactory.createValue(expression);
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
          addInstruction(new NotInstruction(expression));
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
        addInstruction(new PopInstruction());
      }
    }

    // complex assignments (e.g. "|=") are both reading and writing
    boolean writing = PsiUtil.isAccessedForWriting(expression) && !PsiUtil.isAccessedForReading(expression);
    addInstruction(new PushInstruction(myFactory.createValue(expression), expression, writing));
    addNullCheck(expression);
    
    finishElement(expression);
  }

  @Override public void visitLiteralExpression(PsiLiteralExpression expression) {
    startElement(expression);

    DfaValue dfaValue = myFactory.createLiteralValue(expression);
    addInstruction(new PushInstruction(dfaValue, expression));
    if (dfaValue == myFactory.getConstFactory().getNull()) {
      addNullCheck(expression);
    }

    finishElement(expression);
  }

  @Override public void visitTypeCastExpression(PsiTypeCastExpression castExpression) {
    startElement(castExpression);
    PsiExpression operand = castExpression.getOperand();

    if (operand != null) {
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(castExpression, operand.getType(), castExpression.getType());
    }
    else {
      addInstruction(new PushInstruction(myFactory.createTypeValue(castExpression.getType(), Nullability.UNKNOWN), null));
    }

    final PsiTypeElement typeElement = castExpression.getCastType();
    if (typeElement != null && operand != null && operand.getType() != null && !(typeElement.getType() instanceof PsiPrimitiveType)) {
      addInstruction(new TypeCastInstruction(castExpression, operand, typeElement.getType()));
    }
    finishElement(castExpression);
  }

  @Override public void visitClass(PsiClass aClass) {
  }

  /**
   * Inline code block (lambda or method body) into this CFG. Incoming parameters are assumed to be handled already (if necessary)
   *
   * @param block block to inline
   * @param resultNullability desired nullability returned by block return statement
   * @param target a variable to store the block result (returned via {@code return} statement)
   */
  void inlineBlock(@NotNull PsiCodeBlock block, @NotNull Nullability resultNullability, @NotNull DfaVariableValue target) {
    enterExpressionBlock(block, resultNullability, target);
    block.accept(this);
    exitExpressionBlock();
  }

  private void enterExpressionBlock(@NotNull PsiCodeBlock block, @NotNull Nullability resultNullability, @NotNull DfaVariableValue target) {
    // Transfer value is pushed to avoid emptying stack beyond this point
    pushTrap(new Trap.InsideInlinedBlock(block));
    addInstruction(new PushInstruction(myFactory.controlTransfer(ReturnTransfer.INSTANCE, FList.emptyList()), null));
    myExpressionBlockContext =
      new ExpressionBlockContext(myExpressionBlockContext, block, resultNullability == Nullability.NOT_NULL, target);
    startElement(block);
  }

  private void exitExpressionBlock() {
    finishElement(myExpressionBlockContext.myCodeBlock);
    myExpressionBlockContext = myExpressionBlockContext.myPreviousBlock;
    popTrap(Trap.InsideInlinedBlock.class);
    // Pop transfer value
    addInstruction(new PopInstruction());
  }

  /**
   * Register custom nullability problem for given expression or its subexpressions (e.g. ternary branches). 
   * This would override default problem detection.
   * 
   * @param expression an expression which nullness may cause the problem
   * @param problem a problem to check. Use {@link NullabilityProblemKind#noProblem} to suppress the problem which 
   *                would be detected by default.
   */
  void addCustomNullabilityProblem(@NotNull PsiExpression expression, @NotNull NullabilityProblemKind<? super PsiExpression> problem) {
    myCustomNullabilityProblems.put(expression, problem);
  }

  /**
   * Unregister custom nullability problem previously registered via {@link #addCustomNullabilityProblem(PsiExpression, NullabilityProblemKind)}. 
   * Call this after given expression is fully analyzed.
   * 
   * @param expression an expression to deregister.
   */
  void removeCustomNullabilityProblem(@NotNull PsiExpression expression) {
    myCustomNullabilityProblems.remove(expression);
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
    return getFactory().getVarFactory().createVariableValue(new Synthetic(getInstructionCount(), type));
  }

  /**
   * Checks whether supplied variable is a temporary variable created previously via {@link #createTempVariable(PsiType)}
   *
   * @param variable to check
   * @return true if supplied variable is a temp variable.
   */
  public static boolean isTempVariable(@NotNull DfaVariableValue variable) {
    return variable.getDescriptor() instanceof Synthetic;
  }

  /**
   * @param expression expression to test
   * @return true if some inliner may add constraints on the precise type of given expression
   */
  public static boolean inlinerMayInferPreciseType(PsiExpression expression) {
    return Arrays.stream(INLINERS).anyMatch(inliner -> inliner.mayInferPreciseType(expression));
  }

  private static final class Synthetic implements VariableDescriptor {
    private final int myLocation;
    private final PsiType myType;

    private Synthetic(int location, PsiType type) {
      myLocation = location;
      myType = type;
    }

    @NotNull
    @Override
    public String toString() {
      return "tmp$" + myLocation;
    }

    @Nullable
    @Override
    public PsiType getType(@Nullable DfaVariableValue qualifier) {
      return myType;
    }

    @Override
    public boolean isStable() {
      return true;
    }
  }

  static class ExpressionBlockContext {
    final @Nullable ExpressionBlockContext myPreviousBlock;
    final @NotNull PsiCodeBlock myCodeBlock;
    final boolean myForceNonNullBlockResult;
    final @NotNull DfaVariableValue myTarget;

    ExpressionBlockContext(@Nullable ExpressionBlockContext previousBlock,
                           @NotNull PsiCodeBlock codeBlock,
                           boolean forceNonNullBlockResult,
                           @NotNull DfaVariableValue target) {
      myPreviousBlock = previousBlock;
      myCodeBlock = codeBlock;
      myForceNonNullBlockResult = forceNonNullBlockResult;
      myTarget = target;
    }

    boolean isSwitch() {
      return myCodeBlock.getParent() instanceof PsiSwitchExpression;
    }

    void generateReturn(PsiExpression returnValue, ControlFlowAnalyzer analyzer) {
      if (returnValue != null) {
        analyzer.addInstruction(new PushInstruction(myTarget, null, true));
        if (!isSwitch()) {
          analyzer.addCustomNullabilityProblem(returnValue, myForceNonNullBlockResult ? 
                                                            NullabilityProblemKind.nullableFunctionReturn : NullabilityProblemKind.noProblem);
        }
        returnValue.accept(analyzer);
        analyzer.removeCustomNullabilityProblem(returnValue);
        analyzer.generateBoxingUnboxingInstructionFor(returnValue, myTarget.getType());
        analyzer.addInstruction(new AssignInstruction(returnValue, null));
        analyzer.addInstruction(new PopInstruction());
      }

      analyzer.jumpOut(myCodeBlock);
    }
  }

  static final CallInliner[] INLINERS = {
    new OptionalChainInliner(), new LambdaInliner(), new CollectionFactoryInliner(),
    new StreamChainInliner(), new MapUpdateInliner(), new AssumeInliner(), new ClassMethodsInliner(),
    new AssertAllInliner(), new BoxingInliner(), new SimpleMethodInliner(), new CollectionMethodInliner()
  };
}

