// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaPolyadicPartAnchor;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaSwitchLabelTakenAnchor;
import com.intellij.codeInspection.dataFlow.java.inliner.*;
import com.intellij.codeInspection.dataFlow.java.inst.*;
import com.intellij.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.jvm.TrapTracker;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.ArrayElementDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.AssertionDisabledDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.ThisDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.problems.ArrayIndexProblem;
import com.intellij.codeInspection.dataFlow.jvm.problems.ContractFailureProblem;
import com.intellij.codeInspection.dataFlow.jvm.problems.NegativeArraySizeProblem;
import com.intellij.codeInspection.dataFlow.jvm.transfer.*;
import com.intellij.codeInspection.dataFlow.jvm.transfer.EnterFinallyTrap.TwrFinally;
import com.intellij.codeInspection.dataFlow.jvm.transfer.TryCatchTrap.CatchClauseDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.transfer.TryCatchTrap.JavaCatchClauseDescriptor;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.*;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.ControlFlowOffset;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow.DeferredOffset;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue.Trap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.psi.CommonClassNames.*;

public class ControlFlowAnalyzer extends JavaElementVisitor {
  private static final Logger LOG = Logger.getInstance(ControlFlowAnalyzer.class);
  private static final CallMatcher LIST_INITIALIZER = CallMatcher.anyOf(
    CallMatcher.staticCall(JAVA_UTIL_ARRAYS, "asList"),
    CallMatcher.staticCall(JAVA_UTIL_LIST, "of"));
  static final int MAX_UNROLL_SIZE = 3;
  private static final int MAX_ARRAY_INDEX_FOR_INITIALIZER = 32;
  private final PsiElement myCodeFragment;
  private final boolean myInlining;
  private final DfaValueFactory myFactory;
  private final TrapTracker myTrapTracker;
  private ControlFlow myCurrentFlow;
  private final Map<PsiExpression, NullabilityProblemKind<? super PsiExpression>> myCustomNullabilityProblems = new HashMap<>();
  private ExpressionBlockContext myExpressionBlockContext;

  /**
   * @param valueFactory factory to create values
   * @param codeFragment code fragment to analyze:
   *                     normally a PsiCodeBlock or PsiExpression.
   *                     If PsiWhileStatement or PsiDoWhileStatement then only one loop iteration will be analyzed
   *                     (similar to analyzing the loop body, but condition is analyzed as well).
   *                     If PsiClass then class initializers + field initializers will be analyzed
   * @param inlining if true inlining is performed for known method calls
   */
  ControlFlowAnalyzer(@NotNull DfaValueFactory valueFactory, @NotNull PsiElement codeFragment, boolean inlining) {
    myInlining = inlining;
    myFactory = valueFactory;
    myCodeFragment = codeFragment;
    myTrapTracker = new TrapTracker(valueFactory, codeFragment);
  }

  private void buildClassInitializerFlow(PsiClass psiClass, boolean isStatic) {
    for (PsiElement element = psiClass.getFirstChild(); element != null; element = element.getNextSibling()) {
      if (element instanceof PsiField &&
          !((PsiField)element).hasInitializer() &&
          ((PsiField)element).hasModifierProperty(PsiModifier.STATIC) == isStatic) {
        visitField((PsiField)element);
      }
    }
    if (!isStatic &&
        ImplicitUsageProvider.EP_NAME.getExtensionList().stream().anyMatch(p -> p.isClassWithCustomizedInitialization(psiClass))) {
      addInstruction(new EscapeInstruction(Collections.singleton(
        ThisDescriptor.createThisValue(getFactory(), psiClass))));
      addInstruction(new FlushFieldsInstruction());
    }
    for (PsiElement element = psiClass.getFirstChild(); element != null; element = element.getNextSibling()) {
      if (((element instanceof PsiField && ((PsiField)element).hasInitializer()) || element instanceof PsiClassInitializer) &&
          ((PsiMember)element).hasModifierProperty(PsiModifier.STATIC) == isStatic) {
        element.accept(this);
      }
    }
    if (!isStatic) {
      addInstruction(new EndOfInstanceInitializerInstruction());
    }
    addInstruction(new FlushFieldsInstruction());
  }

  private @Nullable ControlFlow buildControlFlow() {
    myCurrentFlow = new ControlFlow(myFactory, myCodeFragment);
    addInstruction(new FinishElementInstruction(null)); // to initialize LVA
    try {
      if(myCodeFragment instanceof PsiClass) {
        // if(unknown) { staticInitializer(); } else { instanceInitializer(); }
        pushUnknown();
        ConditionalGotoInstruction conditionalGoto = new ConditionalGotoInstruction(null, DfType.TOP);
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

    myCurrentFlow.finish();
    return myCurrentFlow;
  }

  DfaValueFactory getFactory() {
    return myFactory;
  }

  private void removeVariable(@Nullable PsiVariable variable) {
    if (variable == null) return;
    myCurrentFlow.addInstruction(new FlushVariableInstruction(PlainDescriptor.createVariableValue(getFactory(), variable)));
  }

  void addInstruction(Instruction i) {
    myCurrentFlow.addInstruction(i);
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
      List<DfaVariableValue> synthetics = myCurrentFlow.getSynthetics(element);
      FinishElementInstruction instruction = new FinishElementInstruction(element);
      instruction.getVarsToFlush().addAll(synthetics);
      addInstruction(instruction);
    }
  }

  @Override
  public void visitErrorElement(@NotNull PsiErrorElement element) {
    throw new CannotAnalyzeException();
  }

  @Override public void visitAssignmentExpression(PsiAssignmentExpression expression) {
    PsiExpression rExpr = expression.getRExpression();

    startElement(expression);
    if (rExpr == null) {
      pushUnknown();
      finishElement(expression);
      return;
    }

    IElementType op = expression.getOperationTokenType();
    PsiType type = expression.getType();
    PsiExpression lExpr = expression.getLExpression();
    PsiArrayAccessExpression arrayStore = ObjectUtils.tryCast(lExpr, PsiArrayAccessExpression.class);
    if (arrayStore != null) {
      arrayStore.getArrayExpression().accept(this);
      PsiExpression index = arrayStore.getIndexExpression();
      if (index != null) {
        index.accept(this);
        generateBoxingUnboxingInstructionFor(index, PsiType.INT);
      } else {
        pushUnknown();
      }
    } else {
      lExpr.accept(this);
    }
    if (op == JavaTokenType.EQ) {
      rExpr.accept(this);
      generateBoxingUnboxingInstructionFor(rExpr, type);
    }
    else {
      if (arrayStore != null) {
        // duplicate array and index on the stack
        addInstruction(new SpliceInstruction(2, 1, 0, 1, 0));
        DfaControlTransferValue transfer = createTransfer("java.lang.ArrayIndexOutOfBoundsException");
        DfaVariableValue staticValue =
          ObjectUtils.tryCast(JavaDfaValueFactory.getExpressionDfaValue(myFactory, arrayStore), DfaVariableValue.class);
        addInstruction(new ArrayAccessInstruction(
          new JavaExpressionAnchor(arrayStore), new ArrayIndexProblem(arrayStore), transfer, staticValue));
      } else {
        addInstruction(new DupInstruction());
      }
      IElementType sign = TypeConversionUtil.convertEQtoOperation(op);
      PsiType resType = TypeConversionUtil.calcTypeForBinaryExpression(lExpr.getType(), rExpr.getType(), sign, true);
      generateBoxingUnboxingInstructionFor(lExpr, resType);
      rExpr.accept(this);
      generateBoxingUnboxingInstructionFor(rExpr, resType);
      generateBinOp(new JavaExpressionAnchor(expression), sign, rExpr, resType);
      generateBoxingUnboxingInstructionFor(rExpr, resType, type, false);
    }

    if (arrayStore != null) {
      DfaControlTransferValue transfer = createTransfer("java.lang.ArrayIndexOutOfBoundsException");
      var staticVariable = ObjectUtils.tryCast(JavaDfaValueFactory.getExpressionDfaValue(myFactory, arrayStore), DfaVariableValue.class);
      addInstruction(new JavaArrayStoreInstruction(arrayStore, rExpr, transfer, staticVariable));
    } else {
      addInstruction(new AssignInstruction(rExpr, JavaDfaValueFactory.getExpressionDfaValue(myFactory, lExpr)));
    }
    addNullCheck(expression);

    finishElement(expression);
  }

  @Override public void visitAssertStatement(PsiAssertStatement statement) {
    startElement(statement);
    addInstruction(new PushInstruction(AssertionDisabledDescriptor.createAssertionsDisabledVar(myFactory), null));
    ConditionalGotoInstruction jump = new ConditionalGotoInstruction(null, DfTypes.TRUE);
    addInstruction(jump);
    final PsiExpression condition = statement.getAssertCondition();
    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
      addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), DfTypes.TRUE, condition));
      final PsiExpression description = statement.getAssertDescription();
      if (description != null) {
        description.accept(this);
      }

      addInstruction(new ThrowInstruction(myTrapTracker.transferValue(JAVA_LANG_ASSERTION_ERROR), statement));
    }
    jump.setOffset(getInstructionCount());
    finishElement(statement);
  }

  @Override public void visitDeclarationStatement(PsiDeclarationStatement statement) {
    startElement(statement);

    PsiElement[] elements = statement.getDeclaredElements();
    for (PsiElement element : elements) {
      if (element instanceof PsiClass) {
        handleClosure(element);
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
      DfaVariableValue dfaVariable = PlainDescriptor.createVariableValue(myFactory, field);
      new CFGBuilder(this).assignAndPop(dfaVariable, DfTypes.defaultValue(field.getType()));
    }
  }

  @Override
  public void visitClassInitializer(PsiClassInitializer initializer) {
    visitCodeBlock(initializer.getBody());
  }

  private void initializeVariable(PsiVariable variable, PsiExpression initializer) {
    if (JavaDfaValueFactory.ignoreInitializer(variable)) return;
    DfaVariableValue dfaVariable = PlainDescriptor.createVariableValue(myFactory, variable);
    addInstruction(new JvmPushInstruction(dfaVariable, null, true));
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

    PsiStatement[] blockStatements = block.getStatements();
    for (PsiStatement statement : blockStatements) {
      statement.accept(this);
    }

    flushCodeBlockVariables(blockStatements, block.getParent());

    finishElement(block);
  }

  private void flushCodeBlockVariables(PsiStatement @NotNull [] statements, @Nullable PsiElement parent) {
    for (PsiStatement statement : statements) {
      if (statement instanceof PsiDeclarationStatement) {
        for (PsiElement declaration : ((PsiDeclarationStatement)statement).getDeclaredElements()) {
          if (declaration instanceof PsiVariable) {
            removeVariable((PsiVariable)declaration);
          }
        }
      }
    }
    if (parent instanceof PsiCatchSection) {
      removeVariable(((PsiCatchSection)parent).getParameter());
    }
    else if (parent instanceof PsiForeachStatement) {
      removeVariable(((PsiForeachStatement)parent).getIterationParameter());
    }
    else if (parent instanceof PsiForStatement) {
      PsiStatement statement = ((PsiForStatement)parent).getInitialization();
      if (statement instanceof PsiDeclarationStatement) {
        for (PsiElement declaration : ((PsiDeclarationStatement)statement).getDeclaredElements()) {
          if (declaration instanceof PsiVariable) {
            removeVariable((PsiVariable)declaration);
          }
        }
      }
    }
    else if (parent instanceof PsiTryStatement) {
      PsiResourceList list = ((PsiTryStatement)parent).getResourceList();
      if (list != null) {
        for (PsiResourceListElement resource : list) {
          if (resource instanceof PsiResourceVariable) {
            removeVariable((PsiVariable)resource);
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
    jumpOut(statement.findExitedStatement());
    finishElement(statement);
  }

  @Override
  public void visitYieldStatement(PsiYieldStatement statement) {
    startElement(statement);
    PsiSwitchExpression enclosing = statement.findEnclosingExpression();
    PsiExpression expression = statement.getExpression();
    if (enclosing != null && myExpressionBlockContext != null && myExpressionBlockContext.myCodeBlock == enclosing.getBody()) {
      myExpressionBlockContext.generateReturn(expression, this);
    } else {
      // yield in incorrect location or only part of switch is analyzed
      if (expression != null) {
        expression.accept(this);
        addInstruction(new PopInstruction());
      }
      jumpOut(enclosing);
    }
    finishElement(statement);
  }

  private void addNullCheck(@NotNull PsiExpression expression) {
    addNullCheck(NullabilityProblemKind.fromContext(expression, myCustomNullabilityProblems));
  }

  void addNullCheck(@Nullable NullabilityProblemKind.NullabilityProblem<?> problem) {
    if (problem != null) {
      DfaControlTransferValue transfer =
        problem.thrownException() != null ? myTrapTracker.maybeTransferValue(problem.thrownException()) : null;
      addInstruction(new CheckNotNullInstruction(problem, transfer));
    }
  }

  private void jumpOut(PsiElement exitedStatement) {
    if (exitedStatement != null && PsiTreeUtil.isAncestor(myCodeFragment, exitedStatement, false)) {
      controlTransfer(createTransfer(exitedStatement, exitedStatement),
                      myTrapTracker.getTrapsInsideElement(exitedStatement));
    } else {
      // Jumping out of analyzed code fragment
      controlTransfer(DfaControlTransferValue.RETURN_TRANSFER, myTrapTracker.getTrapsInsideElement(myCodeFragment));
    }
  }

  private void controlTransfer(@NotNull DfaControlTransferValue.TransferTarget target, FList<Trap> traps) {
    addInstruction(new ControlTransferInstruction(myFactory.controlTransfer(target, traps)));
  }

  @Override public void visitContinueStatement(PsiContinueStatement statement) {
    startElement(statement);
    PsiStatement continuedStatement = statement.findContinuedStatement();
    if (continuedStatement instanceof PsiLoopStatement && PsiTreeUtil.isAncestor(myCodeFragment, continuedStatement, true)) {
      PsiStatement body = ((PsiLoopStatement)continuedStatement).getBody();
      controlTransfer(createTransfer(body, body), myTrapTracker.getTrapsInsideElement(body));
    } else {
      // Jumping out of analyzed code fragment
      controlTransfer(DfaControlTransferValue.RETURN_TRANSFER, myTrapTracker.getTrapsInsideElement(myCodeFragment));
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
        if (statement == myCodeFragment) {
          addInstruction(new PopInstruction());
        } else {
          addInstruction(new ConditionalGotoInstruction(getStartOffset(statement), DfTypes.TRUE, condition));
        }
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
    return expressions == null ? getFactory().getUnknown() : JavaDfaValueFactory.createCommonValue(getFactory(), expressions, type);
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
        addInstruction(new UnwrapDerivedVariableInstruction(length));
        addInstruction(new ConditionalGotoInstruction(loopEndOffset, DfTypes.intValue(0)));
        hasSizeCheck = true;
      } else {
        addInstruction(new PopInstruction());
      }
    }

    ControlFlowOffset offset = myCurrentFlow.getNextOffset();
    DfaVariableValue dfaVariable = PlainDescriptor.createVariableValue(myFactory, parameter);
    DfaValue commonValue = getIteratedElement(parameter.getType(), iteratedValue);
    if (DfaTypeValue.isUnknown(commonValue)) {
      addInstruction(new FlushVariableInstruction(dfaVariable));
    } else {
      new CFGBuilder(this).pushForWrite(dfaVariable).push(commonValue).assign().pop();
    }

    if (!hasSizeCheck) {
      pushUnknown();
      addInstruction(new ConditionalGotoInstruction(loopEndOffset, DfType.TOP));
    }

    final PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    if (hasSizeCheck) {
      pushUnknown();
      addInstruction(new ConditionalGotoInstruction(loopEndOffset, DfType.TOP));
    }

    addInstruction(new GotoInstruction(offset));

    finishElement(statement);
    removeVariable(parameter);
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
      addInstruction(new PushValueInstruction(statement.getRParenth() == null ? DfTypes.BOOLEAN : DfTypes.TRUE));
    }
    addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), DfTypes.FALSE, condition));

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    ControlFlowOffset offset = initialization != null ? getEndOffset(initialization) : getStartOffset(statement);

    if (!addCountingLoopBound(statement, offset)) {
      PsiStatement update = statement.getUpdate();
      if (update != null) {
        update.accept(this);
      }
      addInstruction(new GotoInstruction(offset));
    }

    finishElement(statement);

    for (PsiElement declaredVariable : declaredVariables) {
      PsiVariable psiVariable = (PsiVariable)declaredVariable;
      removeVariable(psiVariable);
    }
  }

  private static @Nullable Long asLong(PsiExpression expression) {
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
   * @param startOffset loop start offset (jump target for back-branch)
   */
  private boolean addCountingLoopBound(PsiForStatement statement, ControlFlowOffset startOffset) {
    CountingLoop loop = CountingLoop.from(statement);
    if (loop == null || loop.isDescending()) return false;
    PsiLocalVariable counter = loop.getCounter();
    PsiType counterType = counter.getType();
    Long end = asLong(loop.getBound());
    if (loop.isIncluding() && !(PsiType.LONG.equals(counterType) && PsiType.INT.equals(loop.getBound().getType()))) {
      // could be for(int i=0; i<=Integer.MAX_VALUE; i++) which will overflow: conservatively skip this
      if (end == null || end == Long.MAX_VALUE || end == Integer.MAX_VALUE) return false;
    }
    PsiExpression initializer = loop.getInitializer();
    if (!PsiType.INT.equals(counterType) && !PsiType.LONG.equals(counterType)) return false;
    DfaValue origin = null;
    Object initialValue = ExpressionUtils.computeConstantExpression(initializer);
    if (initialValue instanceof Number) {
      origin = myFactory.fromDfType(DfTypes.constant(initialValue, counterType));
    }
    else if (initializer instanceof PsiReferenceExpression) {
      PsiVariable initialVariable = ObjectUtils.tryCast(((PsiReferenceExpression)initializer).resolve(), PsiVariable.class);
      if ((PsiUtil.isJvmLocalVariable(initialVariable))
        && !VariableAccessUtils.variableIsAssigned(initialVariable, statement.getBody())) {
        origin = PlainDescriptor.createVariableValue(myFactory, initialVariable);
      }
    }
    if (origin == null) return false;
    Long start = asLong(initializer);
    long diff = start == null || end == null ? -1 : end - start;
    DfaVariableValue loopVar = PlainDescriptor.createVariableValue(myFactory, counter);
    if(diff >= 0 && diff <= MAX_UNROLL_SIZE) {
      // Unroll small loops
      Objects.requireNonNull(statement.getUpdate()).accept(this);
      addInstruction(new GotoInstruction(startOffset, false));
      return true;
    }
    else if (start != null) {
      long maxValue = end == null ? Long.MAX_VALUE : loop.isIncluding() ? end + 1 : end;
      if (start >= maxValue && !loop.mayOverflow()) {
        addInstruction(new GotoInstruction(getEndOffset(statement)));
      }
      else {
        LongRangeSet rangeSet = start >= maxValue
                                ? LongRangeSet.all().subtract(LongRangeSet.range(maxValue + 1, start))
                                : LongRangeSet.range(start + 1L, maxValue);
        DfType range = DfTypes.rangeClamped(rangeSet, JvmPsiRangeSetUtil.getLongRangeType(counterType));
        new CFGBuilder(this).assignAndPop(loopVar, range);
      }
    } else {
      // loop like for(int i = start; i != end; i++)
      if (loop.mayOverflow()) return false;
      new CFGBuilder(this).assign(loopVar, DfType.TOP)
                          .push(origin)
                          .compare(RelationType.LE);
      addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), DfTypes.TRUE, null));
    }
    addInstruction(new GotoInstruction(startOffset));
    return true;
  }

  @Override public void visitIfStatement(PsiIfStatement statement) {
    startElement(statement);

    PsiExpression condition = statement.getCondition();

    PsiStatement thenStatement = statement.getThenBranch();
    PsiStatement elseStatement = statement.getElseBranch();

    DeferredOffset skipThenOffset = new DeferredOffset();

    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
      addInstruction(new ConditionalGotoInstruction(skipThenOffset, DfTypes.FALSE, condition));
    }

    if (thenStatement != null) {
      addInstruction(new FinishElementInstruction(null));
      thenStatement.accept(this);
    }

    if (elseStatement != null) {
      DeferredOffset skipElseOffset = new DeferredOffset();
      Instruction instruction = new GotoInstruction(skipElseOffset);
      addInstruction(instruction);
      skipThenOffset.setOffset(getInstructionCount());
      addInstruction(new FinishElementInstruction(null));
      elseStatement.accept(this);
      skipElseOffset.setOffset(getInstructionCount());
    } else {
      skipThenOffset.setOffset(getInstructionCount());
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
    DfaValue dfaValue = JavaDfaValueFactory.getExpressionDfaValue(myFactory, expression);
    push(dfaValue == null ? myFactory.getUnknown() : dfaValue, expression);
    handleClosure(expression);
    finishElement(expression);
  }

  private void handleClosure(PsiElement closure) {
    Set<PsiVariable> variables = new HashSet<>();
    Set<DfaVariableValue> escapedVars = new HashSet<>();
    closure.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement target = expression.resolve();
        if (PsiUtil.isJvmLocalVariable(target)) {
          variables.add((PsiVariable)target);
        }
        if (target instanceof PsiMember) {
          DfaValue escapedVar = JavaDfaValueFactory.getQualifierOrThisValue(getFactory(), expression);
          if (escapedVar == null) {
            escapedVar = JavaDfaValueFactory.getExpressionDfaValue(getFactory(), expression);
          }
          if (escapedVar instanceof DfaVariableValue) {
            escapedVars.add((DfaVariableValue)escapedVar);
          }
        }
      }

      @Override
      public void visitThisExpression(PsiThisExpression expression) {
        super.visitThisExpression(expression);
        DfaValue value = JavaDfaValueFactory.getExpressionDfaValue(getFactory(), expression);
        if (value instanceof DfaVariableValue) {
          escapedVars.add((DfaVariableValue)value);
        }
      }
    });
    for (DfaValue value : getFactory().getValues()) {
      if(value instanceof DfaVariableValue) {
        PsiElement var = ((DfaVariableValue)value).getPsiVariable();
        if (var instanceof PsiVariable && variables.contains(var)) {
          escapedVars.add((DfaVariableValue)value);
        }
      }
    }
    if (!escapedVars.isEmpty()) {
      addInstruction(new EscapeInstruction(escapedVars));
    }
    List<PsiElement> closures;
    if (closure instanceof PsiClass) {
      closures = getClosures((PsiClass)closure);
    }
    else if (closure instanceof PsiLambdaExpression) {
      closures = ContainerUtil.createMaybeSingletonList(((PsiLambdaExpression)closure).getBody());
    } else {
      closures = List.of();
    }
    if (!closures.isEmpty()) {
      addInstruction(new ClosureInstruction(closures));
    }
  }

  @Override public void visitReturnStatement(PsiReturnStatement statement) {
    startElement(statement);

    PsiExpression returnValue = statement.getReturnValue();

    if (myExpressionBlockContext != null) {
      // We treat return inside switch expression (which is disallowed syntax) as yield
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

      addInstruction(new ReturnInstruction(myFactory, myTrapTracker.trapStack(), statement));
    }
    finishElement(statement);
  }

  @Override
  public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    startElement(statement);
    PsiCaseLabelElementList labelElementList = statement.getCaseLabelElementList();
    if (labelElementList != null) {
      for (PsiCaseLabelElement element : labelElementList.getElements()) {
        if (element instanceof PsiDefaultCaseLabelElement) {
          element.accept(this);
        }
      }
    }
    finishElement(statement);
  }

  @Override
  public void visitDefaultCaseLabelElement(PsiDefaultCaseLabelElement element) {
    startElement(element);
    finishElement(element);
  }

  @Override
  public void visitSwitchLabeledRuleStatement(PsiSwitchLabeledRuleStatement statement) {
    PsiSwitchBlock switchBlock = statement.getEnclosingSwitchBlock();
    if (switchBlock == null) return;
    startElement(statement);
    PsiCaseLabelElementList labelElementList = statement.getCaseLabelElementList();
    if (labelElementList != null) {
      for (PsiCaseLabelElement element : labelElementList.getElements()) {
        if (element instanceof PsiDefaultCaseLabelElement) {
          element.accept(this);
        }
      }
    }
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
      PsiType expressionType = expression.getType();
      DfaVariableValue resultVariable = createTempVariable(expressionType);
      enterExpressionBlock(body, Nullability.UNKNOWN, resultVariable, expressionType);
      processSwitch(expression);
      exitExpressionBlock();
      push(resultVariable, expression);
      finishElement(expression);
    }
  }

  private void push(@NotNull DfaValue value, @NotNull PsiExpression expression) {
    addInstruction(new JvmPushInstruction(value, new JavaExpressionAnchor(expression)));
  }

  private void push(@NotNull DfType dfType, @NotNull PsiExpression expression) {
    addInstruction(new PushValueInstruction(dfType, new JavaExpressionAnchor(expression)));
  }

  private void processSwitch(@NotNull PsiSwitchBlock switchBlock) {
    PsiExpression selector = PsiUtil.skipParenthesizedExprDown(switchBlock.getExpression());
    DfaVariableValue expressionValue = null;
    boolean syntheticVar = true;
    PsiType targetType = null;
    if (selector != null) {
      targetType = selector.getType();
      DfaValue selectorValue = JavaDfaValueFactory.getExpressionDfaValue(myFactory, selector);
      if (selectorValue instanceof DfaVariableValue && !((DfaVariableValue)selectorValue).isFlushableByCalls()) {
        expressionValue = (DfaVariableValue)selectorValue;
        syntheticVar = false;
      }
      selector.accept(this);
      if (syntheticVar) {
        expressionValue = createTempVariable(targetType);
        addInstruction(new SimpleAssignmentInstruction(null, expressionValue));
      }
      addInstruction(new PopInstruction());
    }

    PsiCodeBlock body = switchBlock.getBody();

    if (body != null) {
      PsiStatement[] statements = body.getStatements();
      PsiElement defaultLabel = null;
      for (PsiStatement statement : statements) {
        if (!(statement instanceof PsiSwitchLabelStatementBase)) {
          continue;
        }
        PsiSwitchLabelStatementBase psiLabelStatement = (PsiSwitchLabelStatementBase)statement;
        if (psiLabelStatement.isDefaultCase()) {
          defaultLabel = psiLabelStatement;
          continue;
        }
        try {
          ControlFlowOffset offset = getStartOffset(statement);
          PsiCaseLabelElementList labelElementList = psiLabelStatement.getCaseLabelElementList();
          if (labelElementList != null) {
            for (PsiCaseLabelElement labelElement : labelElementList.getElements()) {
              if (labelElement instanceof PsiDefaultCaseLabelElement) {
                defaultLabel = labelElement;
                continue;
              }
              if (labelElement instanceof PsiExpression) {
                PsiExpression expr = ((PsiExpression)labelElement);
                boolean enumConstant = expr instanceof PsiReferenceExpression &&
                                       ((PsiReferenceExpression)expr).resolve() instanceof PsiEnumConstant;
                if (expressionValue != null && (enumConstant || PsiUtil.isConstantExpression(expr))) {
                  if (PsiPrimitiveType.getUnboxedType(targetType) == null) {
                    addInstruction(new JvmPushInstruction(expressionValue, null));
                    expr.accept(this);
                    addInstruction(new BooleanBinaryInstruction(RelationType.EQ, true, new JavaSwitchLabelTakenAnchor(expr)));
                    addInstruction(new ConditionalGotoInstruction(offset, DfTypes.TRUE));
                  }
                  else {
                    addInstruction(new JvmPushInstruction(expressionValue, null));
                    DeferredOffset condGotoOffset = new DeferredOffset();
                    addInstruction(new ConditionalGotoInstruction(condGotoOffset, DfTypes.NULL));

                    addInstruction(new JvmPushInstruction(expressionValue, null));
                    generateBoxingUnboxingInstructionFor(selector, PsiPrimitiveType.getUnboxedType(targetType));
                    expr.accept(this);
                    addInstruction(new BooleanBinaryInstruction(RelationType.EQ, true, new JavaSwitchLabelTakenAnchor(expr)));
                    DeferredOffset gotoOffset = new DeferredOffset();
                    addInstruction(new GotoInstruction(gotoOffset));

                    PushValueInstruction pushValInstr = new PushValueInstruction(DfTypes.FALSE, new JavaSwitchLabelTakenAnchor(expr));
                    addInstruction(pushValInstr);
                    condGotoOffset.setOffset(pushValInstr.getIndex());

                    ConditionalGotoInstruction exitFromSwitchBranchInstr = new ConditionalGotoInstruction(offset, DfTypes.TRUE);
                    addInstruction(exitFromSwitchBranchInstr);
                    gotoOffset.setOffset(exitFromSwitchBranchInstr.getIndex());
                  }
                }
                else if (expressionValue != null && ExpressionUtils.isNullLiteral((PsiExpression)labelElement)) {
                  addInstruction(new JvmPushInstruction(expressionValue, null));
                  expr.accept(this);
                  addInstruction(new BooleanBinaryInstruction(RelationType.EQ, true, new JavaSwitchLabelTakenAnchor(expr)));
                  addInstruction(new ConditionalGotoInstruction(offset, DfTypes.TRUE));
                }
                else {
                  pushUnknown();
                  addInstruction(new ConditionalGotoInstruction(offset, DfTypes.TRUE));
                }
              }
              else if (expressionValue != null && targetType != null && labelElement instanceof PsiPattern) {
                processPatternInSwitch(((PsiPattern)labelElement), expressionValue, targetType);
                addInstruction(new ConditionalGotoInstruction(offset, DfTypes.TRUE));
              }
            }
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      if (defaultLabel != null) {
        addInstruction(new GotoInstruction(getStartOffset(defaultLabel)));
      }
      else if (switchBlock instanceof PsiSwitchExpression) {
        addInstruction(new ThrowInstruction(myTrapTracker.transferValue("java.lang.IncompatibleClassChangeError"), null));
      }
      else {
        addInstruction(new GotoInstruction(getEndOffset(body)));
      }

      body.accept(this);
    }

    if (syntheticVar && expressionValue != null) {
      addInstruction(new FlushVariableInstruction(expressionValue));
    }
  }

  private void processPatternInSwitch(@NotNull PsiPattern pattern, @NotNull DfaVariableValue expressionValue, @NotNull PsiType checkType) {
    DeferredOffset endPatternOffset = new DeferredOffset();
    processPattern(pattern, pattern, expressionValue, checkType, null, endPatternOffset);
    endPatternOffset.setOffset(getInstructionCount());
    addInstruction(new ResultOfInstruction(new JavaSwitchLabelTakenAnchor(pattern)));
  }

  private void processPatternInInstanceof(@NotNull PsiPattern pattern, @NotNull PsiInstanceOfExpression expression,
                                          @NotNull DfaVariableValue expressionValue, @NotNull PsiType checkType) {
    boolean instanceofCanBePotentiallyRedundant = pattern instanceof PsiTypeTestPattern ||
                                                  JavaPsiPatternUtil.skipParenthesizedPatternDown(pattern) instanceof PsiTypeTestPattern;
    DfaAnchor instanceofAnchor = instanceofCanBePotentiallyRedundant ? new JavaExpressionAnchor(expression) : null;
    DeferredOffset endPatternOffset = new DeferredOffset();
    processPattern(pattern, pattern, expressionValue, checkType, instanceofAnchor, endPatternOffset);
    endPatternOffset.setOffset(getInstructionCount());
    if (!instanceofCanBePotentiallyRedundant) {
      addInstruction(new ResultOfInstruction(new JavaExpressionAnchor(expression)));
    }
  }

  private void processPattern(@NotNull PsiPattern sourcePattern, @Nullable PsiPattern innerPattern,
                              @NotNull DfaVariableValue expressionValue, @NotNull PsiType checkType,
                              @Nullable DfaAnchor instanceofAnchor, @NotNull DeferredOffset endPatternOffset) {
    if (innerPattern == null) return;
    if (innerPattern instanceof PsiGuardedPattern) {
      PsiPrimaryPattern primaryPattern = ((PsiGuardedPattern)innerPattern).getPrimaryPattern();
      processPattern(sourcePattern, primaryPattern, expressionValue, checkType, instanceofAnchor, endPatternOffset);
      PsiExpression expression = ((PsiGuardedPattern)innerPattern).getGuardingExpression();
      if (expression != null) {
        expression.accept(this);
      }
      DeferredOffset condGotoOffset = new DeferredOffset();
      addInstruction(new ConditionalGotoInstruction(condGotoOffset, DfTypes.TRUE));

      addInstruction(new PushValueInstruction(DfTypes.FALSE));
      addInstruction(new GotoInstruction(endPatternOffset));

      condGotoOffset.setOffset(getInstructionCount());
    }
    else if (innerPattern instanceof PsiParenthesizedPattern) {
      PsiPattern unwrappedPattern = JavaPsiPatternUtil.skipParenthesizedPatternDown(innerPattern);
      processPattern(sourcePattern, unwrappedPattern, expressionValue, checkType, instanceofAnchor, endPatternOffset);
    }
    else if (innerPattern instanceof PsiTypeTestPattern) {
      PsiPatternVariable variable = ((PsiTypeTestPattern)innerPattern).getPatternVariable();
      if (variable == null) return;

      addInstruction(new JvmPushInstruction(expressionValue, null));

      DeferredOffset condGotoOffset = null;
      if (!JavaPsiPatternUtil.isTotalForType(sourcePattern, checkType)) {
        addInstruction(new DupInstruction());
        addInstruction(new PushValueInstruction(DfTypes.typedObject(JavaPsiPatternUtil.getPatternType(innerPattern), Nullability.NOT_NULL)));

        addInstruction(new InstanceofInstruction(instanceofAnchor, false));

        condGotoOffset = new DeferredOffset();
        addInstruction(new ConditionalGotoInstruction(condGotoOffset, DfTypes.TRUE));

        addInstruction(new PopInstruction());
        addInstruction(new PushValueInstruction(DfTypes.FALSE));
        addInstruction(new GotoInstruction(endPatternOffset));
      }

      DfaVariableValue patternDfaVar = PlainDescriptor.createVariableValue(getFactory(), variable);
      SimpleAssignmentInstruction assignmentInstr = new SimpleAssignmentInstruction(null, patternDfaVar);
      addInstruction(assignmentInstr);
      if (condGotoOffset != null) {
        condGotoOffset.setOffset(assignmentInstr.getIndex());
      }

      addInstruction(new PopInstruction());
    }

    if (sourcePattern == innerPattern) {
      addInstruction(new PushValueInstruction(DfTypes.TRUE));
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

  void addConditionalErrorThrow() {
    if (!myTrapTracker.shouldHandleException()) {
      return;
    }
    DfaControlTransferValue transfer = myTrapTracker.transferValue(JAVA_LANG_ERROR);
    addInstruction(new EnsureInstruction(null, RelationType.EQ, DfType.TOP, transfer));
  }

  @Override
  public void visitTryStatement(PsiTryStatement statement) {
    startElement(statement);

    PsiResourceList resourceList = statement.getResourceList();
    PsiCodeBlock tryBlock = statement.getTryBlock();
    PsiCodeBlock finallyBlock = statement.getFinallyBlock();

    EnterFinallyTrap finallyDescriptor = finallyBlock != null ? new EnterFinallyTrap(finallyBlock, getStartOffset(finallyBlock)) : null;
    if (finallyDescriptor != null) {
      pushTrap(finallyDescriptor);
    }

    PsiCatchSection[] sections = statement.getCatchSections();
    if (sections.length > 0) {
      LinkedHashMap<CatchClauseDescriptor, ControlFlowOffset> clauses = new LinkedHashMap<>();
      for (PsiCatchSection section : sections) {
        PsiCodeBlock catchBlock = section.getCatchBlock();
        if (catchBlock != null) {
          clauses.put(new JavaCatchClauseDescriptor(section), getStartOffset(catchBlock));
        }
      }
      pushTrap(new TryCatchTrap(statement, clauses));
    }

    processTryWithResources(resourceList, tryBlock);

    InstructionTransfer gotoEnd = createTransfer(statement, tryBlock);
    FList<Trap> singleFinally = FList.createFromReversed(ContainerUtil.createMaybeSingletonList(finallyDescriptor));
    controlTransfer(gotoEnd, singleFinally);

    if (sections.length > 0) {
      popTrap(TryCatchTrap.class);
    }

    for (PsiCatchSection section : sections) {
      PsiCodeBlock catchBlock = section.getCatchBlock();
      if (catchBlock != null) {
        visitCodeBlock(catchBlock);
      }
      controlTransfer(gotoEnd, singleFinally);
    }

    if (finallyBlock != null) {
      popTrap(EnterFinallyTrap.class);
      pushTrap(new InsideFinallyTrap(finallyBlock));

      finallyBlock.accept(this);
      controlTransfer(new ExitFinallyTransfer(finallyDescriptor), FList.emptyList());

      popTrap(InsideFinallyTrap.class);
    }

    finishElement(statement);
  }

  @NotNull
  private InstructionTransfer createTransfer(PsiElement exitedStatement, PsiElement blockToFlush) {
    List<VariableDescriptor> varsToFlush = ContainerUtil.map(PsiTreeUtil.findChildrenOfType(blockToFlush, PsiVariable.class),
                                                             variable -> new PlainDescriptor(variable));
    return new InstructionTransfer(getEndOffset(exitedStatement), varsToFlush);
  }

  void pushTrap(Trap elem) {
    myTrapTracker.pushTrap(elem);
  }

  void popTrap(Class<? extends Trap> aClass) {
    myTrapTracker.popTrap(aClass);
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
      InstructionTransfer gotoEnd = createTransfer(resourceList, tryBlock);
      controlTransfer(gotoEnd, FList.createFromReversed(ContainerUtil.createMaybeSingletonList(twrFinallyDescriptor)));
      popTrap(TwrFinally.class);
      pushTrap(new InsideFinallyTrap(resourceList));
      startElement(resourceList);
      addInstruction(new FlushFieldsInstruction());
      addThrows(closerExceptions);
      controlTransfer(new ExitFinallyTransfer(twrFinallyDescriptor), FList.emptyList()); // DfaControlTransferValue is on stack
      finishElement(resourceList);
      popTrap(InsideFinallyTrap.class);
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
    addInstruction(new ConditionalGotoInstruction(getEndOffset(statement), DfTypes.FALSE, condition));

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    if (statement != myCodeFragment) {
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
    DfaValue dfaValue = JavaDfaValueFactory.getExpressionDfaValue(myFactory, expression);
    push(dfaValue == null ? myFactory.getUnknown() : dfaValue, expression);
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
      pushUnknown();
    }

    DfaControlTransferValue transfer = createTransfer("java.lang.ArrayIndexOutOfBoundsException");
    DfaVariableValue staticValue =
      ObjectUtils.tryCast(JavaDfaValueFactory.getExpressionDfaValue(myFactory, expression), DfaVariableValue.class);
    addInstruction(new ArrayAccessInstruction(new JavaExpressionAnchor(expression), new ArrayIndexProblem(expression), transfer,
                                              staticValue));
    addNullCheck(expression);
    finishElement(expression);
  }

  private @Nullable DfaVariableValue getTargetVariable(PsiExpression expression) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (expression instanceof PsiArrayInitializerExpression && parent instanceof PsiNewExpression) {
      parent = PsiUtil.skipParenthesizedExprUp(parent.getParent());
    }
    if (parent instanceof PsiVariable) {
      // initialization
      return PlainDescriptor.createVariableValue(getFactory(), (PsiVariable)parent);
    }
    if (parent instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      if (assignmentExpression.getOperationTokenType().equals(JavaTokenType.EQ) &&
          PsiTreeUtil.isAncestor(assignmentExpression.getRExpression(), expression, false)) {
        DfaValue value = JavaDfaValueFactory.getExpressionDfaValue(getFactory(), assignmentExpression.getLExpression());
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
    if (arrayWriteTarget != null) {
      PsiVariable arrayVariable = ObjectUtils.tryCast(arrayWriteTarget.getPsiVariable(), PsiVariable.class);
      if (arrayWriteTarget.isFlushableByCalls() ||
          arrayVariable == null ||
          VariableAccessUtils.variableIsUsed(arrayVariable, expression) ||
          ExpressionUtils.getConstantArrayElements(arrayVariable) != null ||
          !(ArrayElementDescriptor.getArrayElementValue(getFactory(), arrayWriteTarget, 0) instanceof DfaVariableValue)) {
        arrayWriteTarget = null;
      }
    }
    DfType arrayType = SpecialField.ARRAY_LENGTH.asDfType(DfTypes.intValue(initializers.length))
      .meet(type == null ? DfTypes.OBJECT_OR_NULL : TypeConstraints.exact(type).asDfType())
      .meet(DfTypes.LOCAL_OBJECT);
    if (arrayWriteTarget != null) {
      addInstruction(new JvmPushInstruction(arrayWriteTarget, null, true));
      push(arrayType, expression);
      addInstruction(new AssignInstruction(originalExpression, arrayWriteTarget));
      int index = 0;
      for (PsiExpression initializer : initializers) {
        DfaValue target = null;
        if (index < MAX_ARRAY_INDEX_FOR_INITIALIZER) {
          target = Objects.requireNonNull(ArrayElementDescriptor.getArrayElementValue(getFactory(), arrayWriteTarget, index));
        }
        index++;
        addInstruction(new JvmPushInstruction(target == null ? myFactory.getUnknown() : target, null, true));
        initializer.accept(this);
        if (componentType != null) {
          generateBoxingUnboxingInstructionFor(initializer, componentType);
        }
        addInstruction(new AssignInstruction(initializer, null));
        addInstruction(new PopInstruction());
      }
    }
    else {
      for (PsiExpression initializer : initializers) {
        addInstruction(new JvmPushInstruction(myFactory.getUnknown(), null, true));
        initializer.accept(this);
        if (componentType != null) {
          generateBoxingUnboxingInstructionFor(initializer, componentType);
        }
        addInstruction(new AssignInstruction(initializer, null));
        addInstruction(new PopInstruction());
      }
      addInstruction(new JvmPushInstruction(var, null, true));
      push(arrayType, expression);
      addInstruction(new AssignInstruction(originalExpression, var));
    }
  }

  @Override
  public void visitPolyadicExpression(PsiPolyadicExpression expression) {
    startElement(expression);

    DfaValue dfaValue = JavaDfaValueFactory.getExpressionDfaValue(myFactory, expression);
    if (dfaValue != null) {
      push(dfaValue, expression);
      finishElement(expression);
      return;
    }

    PsiExpression[] operands = expression.getOperands();
    if (operands.length <= 1) {
      pushUnknown();
      finishElement(expression);
      return;
    }
    IElementType op = expression.getOperationTokenType();
    if (op == JavaTokenType.ANDAND) {
      generateShortCircuitAndOr(expression, operands, expression.getType(), true);
    }
    else if (op == JavaTokenType.OROR) {
      generateShortCircuitAndOr(expression, operands, expression.getType(), false);
    }
    else {
      generateBinOpChain(expression, op, operands);
    }
    finishElement(expression);
  }

  private void checkZeroDivisor(PsiType resType) {
    DfaControlTransferValue transfer = createTransfer("java.lang.ArithmeticException");
    addInstruction(new EnsureInstruction(null, RelationType.NE, PsiType.LONG.equals(resType) ? DfTypes.longValue(0) : DfTypes.intValue(0),
                                         transfer, true));
  }

  private void generateBinOpChain(PsiPolyadicExpression expression, @NotNull IElementType op, PsiExpression[] operands) {
    PsiExpression lExpr = operands[0];
    lExpr.accept(this);
    PsiType lType = lExpr.getType();

    for (int i = 1; i < operands.length; i++) {
      PsiExpression rExpr = operands[i];
      PsiType rType = rExpr.getType();

      acceptBinaryRightOperand(op, lExpr, lType, rExpr, rType);
      PsiType resType = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, op, true);
      DfaAnchor anchor = i == operands.length - 1 ? new JavaExpressionAnchor(expression) :
                         new JavaPolyadicPartAnchor(expression, i);
      generateBinOp(anchor, op, rExpr, resType);

      lExpr = rExpr;
      lType = resType;
    }
  }

  private void generateBinOp(@NotNull DfaAnchor anchor, @NotNull IElementType op, PsiExpression rExpr, PsiType resType) {
    if ((op == JavaTokenType.DIV || op == JavaTokenType.PERC) && resType != null && PsiType.LONG.isAssignableFrom(resType)) {
      Object divisorValue = ExpressionUtils.computeConstantExpression(rExpr);
      if (!(divisorValue instanceof Number) || (((Number)divisorValue).longValue() == 0)) {
        checkZeroDivisor(resType);
      }
    }
    Instruction inst;
    if (op.equals(JavaTokenType.PLUS) && TypeUtils.isJavaLangString(resType)) {
      inst = new StringConcatInstruction(anchor, resType);
    }
    else if (PsiType.BOOLEAN.equals(resType)) {
      if (op.equals(JavaTokenType.AND)) {
        inst = new BooleanAndOrInstruction(false, anchor);
      }
      else if (op.equals(JavaTokenType.OR)) {
        inst = new BooleanAndOrInstruction(true, anchor);
      }
      else {
        RelationType relation = op == JavaTokenType.XOR ? RelationType.NE : DfaPsiUtil.getRelationByToken(op);
        inst = relation != null ? new BooleanBinaryInstruction(relation, false, anchor) :
               new EvalUnknownInstruction(anchor, 2);
      }
    }
    else {
      inst = new NumericBinaryInstruction(JvmPsiRangeSetUtil.binOpFromToken(op), anchor);
    }
    addInstruction(inst);
  }

  private void acceptBinaryRightOperand(@NotNull IElementType op,
                                        PsiExpression lExpr, @Nullable PsiType lType,
                                        PsiExpression rExpr, @Nullable PsiType rType) {
    boolean comparing = op == JavaTokenType.EQEQ || op == JavaTokenType.NE;
    boolean comparingRef = comparing
                           && !TypeConversionUtil.isPrimitiveAndNotNull(lType)
                           && !TypeConversionUtil.isPrimitiveAndNotNull(rType);

    boolean comparingPrimitiveNumeric = !comparingRef && ComparisonUtils.isComparisonOperation(op) &&
                                        TypeConversionUtil.isNumericType(lType) &&
                                        TypeConversionUtil.isNumericType(rType);

    // comparing object and primitive is not compilable code, but we try to balance types to avoid noise warnings
    boolean comparingObjectAndPrimitive = comparing && !comparingRef && !comparingPrimitiveNumeric &&
                                          (TypeConversionUtil.isNumericType(lType) || TypeConversionUtil.isNumericType(rType));

    boolean shift = op == JavaTokenType.GTGT || op == JavaTokenType.LTLT || op == JavaTokenType.GTGTGT;

    PsiType leftCast = null;
    PsiType rightCast = null;
    if (comparingObjectAndPrimitive) {
      leftCast = rightCast = TypeConversionUtil.isNumericType(lType) ? rType : lType;
    }
    else if (shift) {
      leftCast = PsiType.LONG.equals(PsiPrimitiveType.getOptionallyUnboxedType(lType)) ? PsiType.LONG : PsiType.INT;
      rightCast = PsiType.LONG.equals(PsiPrimitiveType.getOptionallyUnboxedType(rType)) ? PsiType.LONG : PsiType.INT;
    }
    else if (!comparingRef) {
      leftCast = rightCast = TypeConversionUtil.unboxAndBalanceTypes(lType, rType);
    }

    if (leftCast != null) {
      generateBoxingUnboxingInstructionFor(lExpr, leftCast);
    }

    rExpr.accept(this);
    if (rightCast != null) {
      generateBoxingUnboxingInstructionFor(rExpr, rightCast);
    }
  }

  void generateBoxingUnboxingInstructionFor(@NotNull PsiExpression expression, PsiType expectedType) {
    generateBoxingUnboxingInstructionFor(expression, expression.getType(), expectedType, false);
  }

  void generateBoxingUnboxingInstructionFor(@NotNull PsiExpression context, PsiType actualType, PsiType expectedType, boolean explicit) {
    if (PsiType.VOID.equals(expectedType)) return;

    if (TypeConversionUtil.isPrimitiveAndNotNull(expectedType) &&
        TypeConversionUtil.isAssignableFromPrimitiveWrapper(toBound(actualType))) {
      addInstruction(new UnwrapDerivedVariableInstruction(SpecialField.UNBOX));
      actualType = PsiPrimitiveType.getUnboxedType(actualType);
    }
    expectedType = toBound(expectedType);
    if (TypeConversionUtil.isPrimitiveAndNotNull(actualType) &&
        TypeConversionUtil.isAssignableFromPrimitiveWrapper(expectedType)) {
      addConditionalErrorThrow();
      PsiType boxedType = TypeConversionUtil.isPrimitiveWrapper(expectedType) ? expectedType :
                          ((PsiPrimitiveType)actualType).getBoxedType(context);
      PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(boxedType);
      if (unboxedType != null && !unboxedType.equals(actualType)) {
        addInstruction(new PrimitiveConversionInstruction(unboxedType, null));
      }
      addInstruction(new WrapDerivedVariableInstruction(DfTypes.typedObject(boxedType, Nullability.NOT_NULL), SpecialField.UNBOX));
    }
    else if (actualType != expectedType &&
             TypeConversionUtil.isPrimitiveAndNotNull(actualType) &&
             TypeConversionUtil.isPrimitiveAndNotNull(expectedType) &&
             TypeConversionUtil.isNumericType(actualType) &&
             TypeConversionUtil.isNumericType(expectedType)) {
      DfaAnchor anchor = explicit ? new JavaExpressionAnchor(context) : null;
      addInstruction(new PrimitiveConversionInstruction((PsiPrimitiveType)expectedType, anchor));
    }
  }

  private static PsiType toBound(PsiType type) {
    if (type instanceof PsiWildcardType && ((PsiWildcardType)type).isExtends()) {
      return ((PsiWildcardType)type).getBound();
    }
    if (type instanceof PsiCapturedWildcardType) {
      return ((PsiCapturedWildcardType)type).getUpperBound();
    }
    return type;
  }

  private void generateShortCircuitAndOr(PsiExpression expression, PsiExpression[] operands, PsiType exprType, boolean and) {
    DeferredOffset endOffset = new DeferredOffset();
    for (int i = 0; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(operand, exprType);

      PsiExpression nextOperand = i == operands.length - 1 ? null : operands[i + 1];
      if (nextOperand != null) {
        DeferredOffset nextOffset = new DeferredOffset();
        addInstruction(new ConditionalGotoInstruction(nextOffset, DfTypes.booleanValue(and), operand));
        push(DfTypes.booleanValue(!and), expression);
        addInstruction(new GotoInstruction(endOffset));
        nextOffset.setOffset(getInstructionCount());
        addInstruction(new FinishElementInstruction(null));
      }
    }
    endOffset.setOffset(getInstructionCount());
    addInstruction(new ResultOfInstruction(new JavaExpressionAnchor(expression)));
  }

  @Override public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    startElement(expression);
    PsiTypeElement operand = expression.getOperand();
    DfType classConstant = DfTypes.referenceConstant(operand.getType(), expression.getType());
    push(classConstant, expression);
    finishElement(expression);
  }

  @Override public void visitConditionalExpression(PsiConditionalExpression expression) {
    startElement(expression);

    PsiExpression thenExpression = expression.getThenExpression();
    if (thenExpression != null) {
      PsiExpression condition = expression.getCondition();
      PsiExpression elseExpression = expression.getElseExpression();
      DeferredOffset elseOffset = new DeferredOffset();
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
      PsiType type = expression.getType();
      addInstruction(new ConditionalGotoInstruction(elseOffset, DfTypes.FALSE, PsiUtil.skipParenthesizedExprDown(condition)));
      thenExpression.accept(this);
      generateBoxingUnboxingInstructionFor(thenExpression,type);

      DeferredOffset endOffset = new DeferredOffset();
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
    addInstruction(new PushValueInstruction(DfType.TOP, null));
  }

  @Override public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
    startElement(expression);

    PsiPattern pattern = expression.getPattern();
    PsiExpression operand = expression.getOperand();
    PsiType operandType = operand.getType();
    PsiTypeElement checkType = expression.getCheckType();
    CFGBuilder builder = new CFGBuilder(this);
    DfaVariableValue expressionValue;
    PsiPatternVariable patternVariable = pattern == null ? null : JavaPsiPatternUtil.getPatternVariable(pattern);
    if (patternVariable == null) {
      if (checkType == null) {
        pushUnknown();
      }
      else {
        buildSimpleInstanceof(builder, expression, operand, checkType);
      }
    }
    else if (operandType != null) {
      DfaValue expr = JavaDfaValueFactory.getExpressionDfaValue(getFactory(), operand);
      if (expr instanceof DfaVariableValue) {
        expressionValue = (DfaVariableValue)expr;
      }
      else {
        expressionValue = createTempVariable(operand.getType());
        builder
          .pushForWrite(expressionValue)
          .pushExpression(operand)
          .assign();
      }
      processPatternInInstanceof(pattern, expression, expressionValue, operandType);
    }
    else {
      pushUnknown();
    }

    finishElement(expression);
  }

  private static void buildSimpleInstanceof(CFGBuilder builder,
                                            PsiInstanceOfExpression expression,
                                            PsiExpression operand,
                                            PsiTypeElement checkType) {
    PsiType type = checkType.getType();
    builder
      .pushExpression(operand)
      .push(DfTypes.typedObject(type, Nullability.NOT_NULL))
      .isInstance(expression);
  }

  void addMethodThrows(PsiMethod method) {
    if (myTrapTracker.shouldHandleException()) {
      addThrows(method == null ? Collections.emptyList() : Arrays.asList(method.getThrowsList().getReferencedTypes()));
    }
  }

  private void addThrows(Collection<? extends PsiType> exceptions) {
    StreamEx<TypeConstraint> allExceptions = StreamEx.of(JAVA_LANG_ERROR, JAVA_LANG_RUNTIME_EXCEPTION)
      .map(fqn -> myTrapTracker.transfer(fqn).getThrowable());
    if (!exceptions.isEmpty()) {
      allExceptions = allExceptions.append(StreamEx.of(exceptions).map(TypeConstraints::instanceOf))
        .map(TypeConstraint::tryNegate).nonNull()
        .reduce(TypeConstraints.TOP, TypeConstraint::meet)
        .notInstanceOfTypes()
        .map(TypeConstraint.Exact::instanceOf);
    }
    allExceptions
      .map(exc -> myTrapTracker.transferValue(new ExceptionTransfer(exc)))
      .map(transfer -> new EnsureInstruction(null, RelationType.EQ, DfType.TOP, transfer))
      .forEach(this::addInstruction);
  }

  void throwException(@Nullable PsiType ref, @Nullable PsiElement anchor) {
    if (ref != null) {
      DfaControlTransferValue value = myTrapTracker.transferValue(new ExceptionTransfer(TypeConstraints.instanceOf(ref)));
      addInstruction(new ThrowInstruction(value, anchor));
    }
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
        DfaValue thisVariable = JavaDfaValueFactory.getQualifierOrThisValue(myFactory, call.getMethodExpression());
        if (thisVariable != null) {
          addInstruction(new JvmPushInstruction(thisVariable, null));
        }
        else {
          pushUnknown();
        }
        break;
      } else if (!qualifierExpression.isPhysical() && call.isPhysical()) {
        // Possible -- see com.intellij.psi.impl.source.jsp.jspJava.JspMethodCallImpl.getMethodExpression
        pushUnknown();
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
    PsiReferenceExpression methodExpression = call.getMethodExpression();
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
    foldVarArgs(call, parameters);

    addBareCall(call, methodExpression);
    finishElement(call);
  }

  void addBareCall(@Nullable PsiMethodCallExpression expression, @NotNull PsiReferenceExpression reference) {
    addConditionalErrorThrow();
    PsiMethod method = ObjectUtils.tryCast(reference.resolve(), PsiMethod.class);
    List<? extends MethodContract> contracts =
      method == null ? Collections.emptyList() :
      DfaUtil.addRangeContracts(method, JavaMethodContractUtil.getMethodCallContracts(method, expression));
    PsiExpression anchor;
    if (expression == null) {
      assert reference instanceof PsiMethodReferenceExpression;
      addInstruction(new MethodCallInstruction((PsiMethodReferenceExpression)reference, contracts));
      anchor = reference;
    }
    else {
      if (ExpressionUtils.isVoidContext(expression) && ContainerUtil.all(contracts, c ->
        c.getReturnValue() != ContractReturnValue.fail() && !(c.getReturnValue() instanceof ContractReturnValue.ParameterReturnValue))) {
        // Do not track contracts if return value is not used
        contracts = Collections.emptyList();
      }
      addInstruction(new MethodCallInstruction(expression, JavaDfaValueFactory.getExpressionDfaValue(myFactory, expression), contracts));
      anchor = expression;
    }
    processFailResult(contracts, anchor);

    addMethodThrows(method);
    if (expression != null) {
      addNullCheck(expression);
    }
  }

  private void processFailResult(List<? extends MethodContract> contracts, PsiExpression anchor) {
    if (contracts.stream().anyMatch(c -> c.getReturnValue().isFail())) {
      DfaControlTransferValue transfer = createTransfer(JAVA_LANG_THROWABLE);
      // if a contract resulted in 'fail', handle it
      addInstruction(new EnsureInstruction(new ContractFailureProblem(anchor), RelationType.NE, DfType.FAIL, transfer));
    }
  }

  @Nullable DfaControlTransferValue createTransfer(@NotNull String exception) {
    return myTrapTracker.maybeTransferValue(exception);
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

    PsiType type = expression.getType();
    if (type instanceof PsiArrayType) {
      PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
      if (arrayInitializer != null) {
        initializeArray(arrayInitializer, expression);
        return;
      }
      final PsiExpression[] dimensions = expression.getArrayDimensions();
      int dims = dimensions.length;
      if (dims > 0) {
        for (final PsiExpression dimension : dimensions) {
          dimension.accept(this);
          generateBoxingUnboxingInstructionFor(dimension, PsiType.INT);
        }
        DfaControlTransferValue transfer = createTransfer("java.lang.NegativeArraySizeException");
        for (int i = dims - 1; i >= 0; i--) {
          addInstruction(new EnsureInstruction(new NegativeArraySizeProblem(dimensions[i]),
                                               RelationType.GE, DfTypes.intValue(0), transfer, true));
          if (i != 0) {
            addInstruction(new PopInstruction());
          }
        }
      }
      else {
        pushUnknown();
      }
      DfType arrayValue = TypeConstraints.exact(type).asDfType().meet(DfTypes.LOCAL_OBJECT);
      addInstruction(new WrapDerivedVariableInstruction(arrayValue, SpecialField.ARRAY_LENGTH));

      initializeSmallArray((PsiArrayType)type, dimensions);
    }
    else {
      PsiExpression qualifier = expression.getQualifier();
      if (qualifier != null) {
        qualifier.accept(this);
      } else {
        DfaValue qualifierValue = myFactory.getUnknown();
        PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
        if (aClass != null && !aClass.hasModifierProperty(PsiModifier.STATIC)) {
          PsiClass outerClass = aClass.getContainingClass();
          if (outerClass != null && InheritanceUtil.hasEnclosingInstanceInScope(outerClass, expression, true, false)) {
            qualifierValue = ThisDescriptor.createThisValue(myFactory, outerClass);
          }
        }
        addInstruction(new JvmPushInstruction(qualifierValue, null));
      }

      PsiMethod constructor = pushConstructorArguments(expression);
      PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
      if (anonymousClass != null) {
        handleClosure(anonymousClass);
      }

      addConditionalErrorThrow();
      DfaValue precalculatedNewValue = getPrecalculatedNewValue(expression);
      List<? extends MethodContract> contracts = constructor == null ? Collections.emptyList() :
                                                 JavaMethodContractUtil.getMethodCallContracts(constructor, null);
      contracts = DfaUtil.addRangeContracts(constructor, contracts);
      addInstruction(new MethodCallInstruction(expression, precalculatedNewValue, contracts));
      processFailResult(contracts, expression);

      addMethodThrows(constructor);
    }

    finishElement(expression);
  }

  private DfaValue getPrecalculatedNewValue(PsiNewExpression expression) {
    PsiType type = expression.getType();
    if (type != null && ConstructionUtils.isEmptyCollectionInitializer(expression)) {
      DfType dfType = SpecialField.COLLECTION_SIZE.asDfType(DfTypes.intValue(0))
        .meet(TypeConstraints.exact(type).asDfType())
        .meet(DfTypes.LOCAL_OBJECT);
      return myFactory.fromDfType(dfType);
    }
    return null;
  }

  private void initializeSmallArray(PsiArrayType type, PsiExpression[] dimensions) {
    if (dimensions.length != 1) return;
    PsiType componentType = type.getComponentType();
    // Ignore objects as they may produce false NPE warnings due to non-perfect loop handling
    if (!(componentType instanceof PsiPrimitiveType)) return;
    Object val = ExpressionUtils.computeConstantExpression(dimensions[0]);
    if (val instanceof Integer) {
      int lengthValue = (Integer)val;
      if (lengthValue > 0 && lengthValue <= MAX_UNROLL_SIZE) {
        DfaVariableValue var = createTempVariable(type);
        addInstruction(new SimpleAssignmentInstruction(null, var));
        addInstruction(new PushValueInstruction(DfTypes.defaultValue(componentType)));
        for (int i = lengthValue - 1; i >= 0; i--) {
          DfaValue value = ArrayElementDescriptor.getArrayElementValue(getFactory(), var, i);
          if (value instanceof DfaVariableValue) {
            addInstruction(new SimpleAssignmentInstruction(null, (DfaVariableValue)value));
          }
        }
        addInstruction(new PopInstruction());
      }
    }
  }

  private @Nullable PsiMethod pushConstructorArguments(PsiConstructorCall call) {
    PsiExpressionList args = call.getArgumentList();
    PsiMethod ctr = call.resolveConstructor();
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (call instanceof PsiNewExpression) {
      substitutor = call.resolveMethodGenerics().getSubstitutor();
    }
    if (args != null) {
      PsiExpression[] arguments = args.getExpressions();
      PsiParameter[] parameters = ctr == null ? null : ctr.getParameterList().getParameters();
      for (int i = 0; i < arguments.length; i++) {
        PsiExpression argument = arguments[i];
        argument.accept(this);
        if (parameters != null && i < parameters.length) {
          generateBoxingUnboxingInstructionFor(argument, substitutor.substitute(parameters[i].getType()));
        }
      }
      foldVarArgs(call, parameters);
    }
    return ctr;
  }

  private void foldVarArgs(PsiCall call, PsiParameter[] parameters) {
    if (!MethodCallUtils.isVarArgCall(call)) return;
    PsiExpressionList args = call.getArgumentList();
    if (args == null) return;
    PsiParameter lastParameter = ArrayUtil.getLastElement(parameters);
    if (lastParameter != null && lastParameter.isVarArgs()) {
      int arraySize = args.getExpressionCount() - parameters.length + 1;
      if (arraySize >= 0) {
        addInstruction(new FoldArrayInstruction(null, DfTypes.typedObject(lastParameter.getType(), Nullability.NOT_NULL), arraySize));
      }
    }
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
      addInstruction(new DupInstruction());
      processIncrementDecrement(expression, operand);
      addInstruction(new PopInstruction());
    } else {
      pushUnknown();
    }

    finishElement(expression);
  }

  private boolean processIncrementDecrement(PsiUnaryExpression expression, PsiExpression operand) {
    LongRangeBinOp token;
    IElementType exprTokenType = expression.getOperationTokenType();
    if (exprTokenType.equals(JavaTokenType.MINUSMINUS)) {
      token = LongRangeBinOp.MINUS;
    }
    else if (exprTokenType.equals(JavaTokenType.PLUSPLUS)) {
      token = LongRangeBinOp.PLUS;
    }
    else {
      return false;
    }
    PsiType operandType = operand.getType();
    PsiPrimitiveType unboxedType = PsiPrimitiveType.getOptionallyUnboxedType(operandType);
    if (unboxedType == null) return false;
    addInstruction(new DupInstruction());
    generateBoxingUnboxingInstructionFor(operand, unboxedType);
    PsiType resultType = TypeConversionUtil.binaryNumericPromotion(unboxedType, PsiType.INT);
    Object addend = TypeConversionUtil.computeCastTo(1, resultType);
    addInstruction(new PushValueInstruction(DfTypes.primitiveConstant(addend)));
    addInstruction(new NumericBinaryInstruction(token, null));
    if (!unboxedType.equals(resultType)) {
      addInstruction(new PrimitiveConversionInstruction(unboxedType, null));
    }
    if (!(operandType instanceof PsiPrimitiveType)) {
      addInstruction(new WrapDerivedVariableInstruction(DfTypes.typedObject(operandType, Nullability.NOT_NULL), SpecialField.UNBOX));
    }
    addInstruction(new AssignInstruction(operand, null, JavaDfaValueFactory.getExpressionDfaValue(myFactory, operand)));
    return true;
  }

  @Override public void visitPrefixExpression(PsiPrefixExpression expression) {
    startElement(expression);

    DfaValue dfaValue = expression.getOperationTokenType() == JavaTokenType.EXCL ? null : JavaDfaValueFactory
      .getExpressionDfaValue(myFactory, expression);
    if (dfaValue != null) {
      // Constant expression is computed: just push the result
      push(dfaValue, expression);
    }
    else {
      PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());

      if (operand == null) {
        pushUnknown();
      }
      else {
        operand.accept(this);
        if (PsiUtil.isIncrementDecrementOperation(expression)) {
          if (!processIncrementDecrement(expression, operand)) {
            pushUnknown();
            addInstruction(new AssignInstruction(operand, null, JavaDfaValueFactory.getExpressionDfaValue(myFactory, operand)));
          }
        }
        else {
          PsiType type = expression.getType();
          PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(type);
          generateBoxingUnboxingInstructionFor(operand, unboxed == null ? type : unboxed);
          if (expression.getOperationTokenType() == JavaTokenType.EXCL) {
            addInstruction(new NotInstruction(new JavaExpressionAnchor(expression)));
          }
          else if (expression.getOperationTokenType() == JavaTokenType.MINUS && (PsiType.INT.equals(type) || PsiType.LONG.equals(type))) {
            addInstruction(new PushValueInstruction(DfTypes.defaultValue(type)));
            addInstruction(new SwapInstruction());
            addInstruction(new NumericBinaryInstruction(LongRangeBinOp.MINUS, new JavaExpressionAnchor(expression)));
          }
          else {
            addInstruction(new PopInstruction());
            pushUnknown();
          }
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
    DfaValue value = JavaDfaValueFactory.getExpressionDfaValue(myFactory, expression);
    addInstruction(new JvmPushInstruction(value == null ? myFactory.getUnknown() : value,
                                          writing ? null : new JavaExpressionAnchor(expression),
                                          writing));
    addNullCheck(expression);

    finishElement(expression);
  }

  @Override public void visitLiteralExpression(PsiLiteralExpression expression) {
    startElement(expression);

    DfType dfType = DfaPsiUtil.fromLiteral(expression);
    push(dfType, expression);
    if (dfType == DfTypes.NULL) {
      addNullCheck(expression);
    }

    finishElement(expression);
  }

  @Override public void visitTypeCastExpression(PsiTypeCastExpression castExpression) {
    startElement(castExpression);
    PsiExpression operand = castExpression.getOperand();

    if (operand != null) {
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(castExpression, operand.getType(), castExpression.getType(), true);
    }
    else {
      addInstruction(new PushValueInstruction(DfTypes.typedObject(castExpression.getType(), Nullability.UNKNOWN)));
    }

    final PsiTypeElement typeElement = castExpression.getCastType();
    if (typeElement != null && operand != null && operand.getType() != null && !(typeElement.getType() instanceof PsiPrimitiveType)) {
      DfaControlTransferValue transfer = createTransfer("java.lang.ClassCastException");
      addInstruction(new TypeCastInstruction(castExpression, operand, typeElement.getType(), transfer));
    }
    finishElement(castExpression);
  }

  @Override public void visitClass(PsiClass aClass) {
  }

  @Contract("null -> false")
  final boolean wasAdded(PsiElement element) {
    return element != null &&
           myCurrentFlow.getStartOffset(element).getInstructionOffset() > -1 &&
           myCurrentFlow.getEndOffset(element).getInstructionOffset() > -1;
  }

  public void removeLambda(@NotNull PsiLambdaExpression lambda) {
    int start = myCurrentFlow.getStartOffset(lambda).getInstructionOffset();
    int end = myCurrentFlow.getEndOffset(lambda).getInstructionOffset();
    for (int i = start; i < end; i++) {
      Instruction inst = myCurrentFlow.getInstruction(i);
      if (inst instanceof EscapeInstruction || inst instanceof ClosureInstruction) {
        myCurrentFlow.makeNop(i);
      }
    }
  }

  /**
   * Inline code block (lambda or method body) into this CFG. Incoming parameters are assumed to be handled already (if necessary)
   *
   * @param block block to inline
   * @param resultNullability desired nullability returned by block return statement
   * @param target a variable to store the block result (returned via {@code return} statement)
   * @param type resulting type
   */
  void inlineBlock(@NotNull PsiCodeBlock block,
                   @NotNull Nullability resultNullability,
                   @NotNull DfaVariableValue target,
                   @Nullable PsiType type) {
    enterExpressionBlock(block, resultNullability, target, type);
    block.accept(this);
    exitExpressionBlock();
  }

  private void enterExpressionBlock(@NotNull PsiCodeBlock block,
                                    @NotNull Nullability resultNullability,
                                    @NotNull DfaVariableValue target,
                                    @Nullable PsiType type) {
    // Transfer value is pushed to avoid emptying stack beyond this point
    pushTrap(new InsideInlinedBlockTrap(block));
    addInstruction(new JvmPushInstruction(myFactory.controlTransfer(DfaControlTransferValue.RETURN_TRANSFER, FList.emptyList()), null));
    myExpressionBlockContext =
      new ExpressionBlockContext(myExpressionBlockContext, block, resultNullability == Nullability.NOT_NULL, target, type);
    startElement(block);
  }

  private void exitExpressionBlock() {
    finishElement(myExpressionBlockContext.myCodeBlock);
    myExpressionBlockContext = myExpressionBlockContext.myPreviousBlock;
    popTrap(InsideInlinedBlockTrap.class);
    // Pop transfer value
    addInstruction(new PopInstruction());
  }

  private static List<PsiElement> getClosures(@NotNull PsiClass nestedClass) {
    List<PsiElement> closures = new ArrayList<>();
    closures.add(nestedClass); // process fields and initializers
    for (PsiMethod method : nestedClass.getMethods()) {
      PsiCodeBlock body = method.getBody();
      if (body != null && (method.isPhysical() || !nestedClass.isPhysical())) {
        // Skip analysis of non-physical methods of physical class (possibly autogenerated by some plugin like Lombok)
        closures.add(body);
      }
    }
    return closures;
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
    DfType dfType = type == null ? DfType.BOTTOM : DfTypes.typedObject(type, Nullability.UNKNOWN);
    return myCurrentFlow.createTempVariable(dfType);
  }

  /**
   * @param expression expression to test
   * @return true if some inliner may add constraints on the precise type of given expression
   */
  public static boolean inlinerMayInferPreciseType(PsiExpression expression) {
    return ContainerUtil.exists(INLINERS, inliner -> inliner.mayInferPreciseType(expression));
  }

  @Nullable
  public static ControlFlow buildFlow(@NotNull PsiElement psiBlock, DfaValueFactory targetFactory) {
    return buildFlow(psiBlock, targetFactory, true);
  }

  /**
   * Create control flow for given PSI block (method body, lambda expression, etc.) and return it. May return cached block.
   * It's prohibited to change the resulting control flow (e.g. add instructions, update their indices, update flush variable lists, etc.)
   *
   * @param psiBlock psi-block
   * @param targetFactory factory to bind the PSI block to
   * @param useInliners whether to use inliners
   * @return resulting control flow; null if it cannot be built (e.g. if the code block contains unrecoverable errors)
   */
  @Nullable
  public static ControlFlow buildFlow(@NotNull PsiElement psiBlock, DfaValueFactory targetFactory, boolean useInliners) {
    if (!useInliners) {
      return new ControlFlowAnalyzer(targetFactory, psiBlock, false).buildControlFlow();
    }
    PsiFile file = psiBlock.getContainingFile();
    ConcurrentHashMap<PsiElement, Optional<ControlFlow>> fileMap =
      CachedValuesManager.getCachedValue(file, () ->
        CachedValueProvider.Result.create(new ConcurrentHashMap<>(), PsiModificationTracker.MODIFICATION_COUNT));
    return fileMap.computeIfAbsent(psiBlock, psi -> {
      DfaValueFactory factory = new DfaValueFactory(file.getProject());
      ControlFlow flow = new ControlFlowAnalyzer(factory, psiBlock, true).buildControlFlow();
      return Optional.ofNullable(flow);
    }).map(flow -> new ControlFlow(flow, targetFactory)).orElse(null);
  }

  private static class CannotAnalyzeException extends RuntimeException {
    private CannotAnalyzeException() {
      super(null, null, false, false);
    }
  }

  static class ExpressionBlockContext {
    final @Nullable ExpressionBlockContext myPreviousBlock;
    final @NotNull PsiCodeBlock myCodeBlock;
    final boolean myForceNonNullBlockResult;
    final @NotNull DfaVariableValue myTarget;
    private final @Nullable PsiType myTargetType;

    ExpressionBlockContext(@Nullable ExpressionBlockContext previousBlock,
                           @NotNull PsiCodeBlock codeBlock,
                           boolean forceNonNullBlockResult,
                           @NotNull DfaVariableValue target,
                           @Nullable PsiType targetType) {
      myPreviousBlock = previousBlock;
      myCodeBlock = codeBlock;
      myForceNonNullBlockResult = forceNonNullBlockResult;
      myTarget = target;
      myTargetType = targetType;
    }

    boolean isSwitch() {
      return myCodeBlock.getParent() instanceof PsiSwitchExpression;
    }

    void generateReturn(PsiExpression returnValue, ControlFlowAnalyzer analyzer) {
      if (returnValue != null) {
        analyzer.addInstruction(new JvmPushInstruction(myTarget, null, true));
        if (!isSwitch()) {
          analyzer.addCustomNullabilityProblem(returnValue, myForceNonNullBlockResult ?
                                                            NullabilityProblemKind.nullableFunctionReturn : NullabilityProblemKind.noProblem);
        }
        returnValue.accept(analyzer);
        analyzer.removeCustomNullabilityProblem(returnValue);
        analyzer.generateBoxingUnboxingInstructionFor(returnValue, myTargetType);
        analyzer.addInstruction(new AssignInstruction(returnValue, null));
        analyzer.addInstruction(new PopInstruction());
      }

      analyzer.jumpOut(myCodeBlock);
    }
  }

  private static final CallInliner[] INLINERS = {
    new OptionalChainInliner(), new LambdaInliner(), new CollectionUpdateInliner(),
    new StreamChainInliner(), new MapUpdateInliner(), new AssumeInliner(), new ClassMethodsInliner(),
    new AssertAllInliner(), new BoxingInliner(), new SimpleMethodInliner(),
    new TransformInliner(), new EnumCompareInliner(), new IndexOfInliner()
  };
}