// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.controlFlow;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.Stack;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class ControlFlowAnalyzer extends JavaElementVisitor {
  private static final Logger LOG = Logger.getInstance(ControlFlowAnalyzer.class);

  private final PsiElement myCodeFragment;
  private final ControlFlowPolicy myPolicy;

  private ControlFlowImpl myCurrentFlow;
  private final Stack<PsiParameter> myCatchParameters = new Stack<>();// stack of PsiParameter for 'catch'
  private final Stack<PsiElement> myCatchBlocks = new Stack<>();

  private final Stack<FinallyBlockSubroutine> myFinallyBlocks = new Stack<>();
  private final Stack<PsiElement> myUnhandledExceptionCatchBlocks = new Stack<>();

  // element to jump to from inner (sub)expression in "jump to begin" situation.
  // E.g. we should jump to "then" branch if condition expression evaluated to true inside if statement
  private final StatementStack myStartStatementStack = new StatementStack();
  // element to jump to from inner (sub)expression in "jump to end" situation.
  // E.g. we should jump to "else" branch if condition expression evaluated to false inside if statement
  private final StatementStack myEndStatementStack = new StatementStack();

  private final Stack<BranchingInstruction.Role> myStartJumpRoles = new Stack<>();
  private final Stack<BranchingInstruction.Role> myEndJumpRoles = new Stack<>();

  private final @NotNull ControlFlowOptions myOptions;
  private final boolean myAssignmentTargetsAreElements;

  private final Stack<IntList> intArrayPool = new Stack<>();
  // map: PsiElement element -> TIntArrayList instructionOffsetsToPatch with getStartOffset(element)
  private final Map<PsiElement, IntList> offsetsAddElementStart = new HashMap<>();
  // map: PsiElement element -> TIntArrayList instructionOffsetsToPatch with getEndOffset(element)
  private final Map<PsiElement, IntList> offsetsAddElementEnd = new HashMap<>();
  private final ControlFlowFactory myControlFlowFactory;
  private final List<SubRangeInfo> mySubRanges = new ArrayList<>();
  private final PsiConstantEvaluationHelper myConstantEvaluationHelper;
  private final Map<PsiField, PsiParameter> myImplicitCompactConstructorAssignments;

  ControlFlowAnalyzer(@NotNull PsiElement codeFragment,
                      @NotNull ControlFlowPolicy policy,
                      @NotNull ControlFlowOptions options) {
    this(codeFragment, policy, options, false);
  }

  private ControlFlowAnalyzer(@NotNull PsiElement codeFragment,
                              @NotNull ControlFlowPolicy policy,
                              @NotNull ControlFlowOptions options,
                              boolean assignmentTargetsAreElements) {
    myCodeFragment = codeFragment;
    myPolicy = policy;
    myOptions = options;
    myAssignmentTargetsAreElements = assignmentTargetsAreElements;
    Project project = codeFragment.getProject();
    myControlFlowFactory = ControlFlowFactory.getInstance(project);
    myConstantEvaluationHelper = JavaPsiFacade.getInstance(project).getConstantEvaluationHelper();
    myImplicitCompactConstructorAssignments = getImplicitCompactConstructorAssignmentsMap();
  }

  private Map<PsiField, PsiParameter> getImplicitCompactConstructorAssignmentsMap() {
    PsiMethod ctor = ObjectUtils.tryCast(myCodeFragment.getParent(), PsiMethod.class);
    if (ctor == null || !JavaPsiRecordUtil.isCompactConstructor(ctor)) return Collections.emptyMap();
    PsiClass containingClass = ctor.getContainingClass();
    if (containingClass == null) return Collections.emptyMap();
    PsiParameter[] parameters = ctor.getParameterList().getParameters();
    PsiRecordComponent[] components = containingClass.getRecordComponents();
    Map<PsiField, PsiParameter> map = new HashMap<>();
    for (int i = 0; i < Math.min(components.length, parameters.length); i++) {
      PsiRecordComponent component = components[i];
      PsiField field = JavaPsiRecordUtil.getFieldForComponent(component);
      PsiParameter parameter = parameters[i];
      map.put(field, parameter);
    }
    return map;
  }

  @NotNull
  ControlFlow buildControlFlow() throws AnalysisCanceledException {
    // push guard outer statement offsets in case when nested expression is incorrect
    myStartJumpRoles.push(BranchingInstruction.Role.END);
    myEndJumpRoles.push(BranchingInstruction.Role.END);

    myCurrentFlow = new ControlFlowImpl();

    // guard elements
    myStartStatementStack.pushStatement(myCodeFragment, false);
    myEndStatementStack.pushStatement(myCodeFragment, false);

    try {
      myCodeFragment.accept(this);
      return cleanup();
    }
    catch (AnalysisCanceledSoftException e) {
      throw new AnalysisCanceledException(e.getErrorElement());
    }
  }

  private void generateCompactConstructorAssignments() {
    myImplicitCompactConstructorAssignments.values().stream().filter(myPolicy::isParameterAccepted).forEach(this::generateReadInstruction);
  }

  private static class StatementStack {
    private final Stack<PsiElement> myStatements = new Stack<>();
    private final IntList myAtStart = new IntArrayList();

    private void popStatement() {
      myAtStart.removeInt(myAtStart.size() - 1);
      myStatements.pop();
    }

    @NotNull
    private PsiElement peekElement() {
      return myStatements.peek();
    }

    private boolean peekAtStart() {
      return myAtStart.getInt(myAtStart.size() - 1) == 1;
    }

    private void pushStatement(@NotNull PsiElement statement, boolean atStart) {
      myStatements.push(statement);
      myAtStart.add(atStart ? 1 : 0);
    }
  }

  @NotNull
  private IntList getEmptyIntArray() {
    if (intArrayPool.isEmpty()) {
      return new IntArrayList(1);
    }
    IntList list = intArrayPool.pop();
    list.clear();
    return list;
  }

  private void poolIntArray(@NotNull IntList list) {
    intArrayPool.add(list);
  }

  // patch instruction currently added to control flow so that its jump offset corrected on getStartOffset(element) or getEndOffset(element)
  //  when corresponding element offset become available
  private void addElementOffsetLater(@NotNull PsiElement element, boolean atStart) {
    Map<PsiElement, IntList> offsetsAddElement = atStart ? offsetsAddElementStart : offsetsAddElementEnd;
    IntList offsets = offsetsAddElement.get(element);
    if (offsets == null) {
      offsets = getEmptyIntArray();
      offsetsAddElement.put(element, offsets);
    }
    int offset = myCurrentFlow.getSize() - 1;
    offsets.add(offset);
    if (myCurrentFlow.getEndOffset(element) != -1) {
      patchInstructionOffsets(element);
    }
  }


  private void patchInstructionOffsets(@NotNull PsiElement element) {
    patchInstructionOffsets(offsetsAddElementStart.get(element), myCurrentFlow.getStartOffset(element));
    offsetsAddElementStart.put(element, null);
    patchInstructionOffsets(offsetsAddElementEnd.get(element), myCurrentFlow.getEndOffset(element));
    offsetsAddElementEnd.put(element, null);
  }

  private void patchInstructionOffsets(@Nullable IntList offsets,
                                       int add) {
    if (offsets == null) return;
    for (int i = 0; i < offsets.size(); i++) {
      int offset = offsets.getInt(i);
      BranchingInstruction instruction = (BranchingInstruction)myCurrentFlow.getInstructions().get(offset);
      instruction.offset += add;
      LOG.assertTrue(instruction.offset >= 0);
    }
    poolIntArray(offsets);
  }

  private ControlFlow cleanup() {
    // make all non patched goto instructions jump to the end of control flow
    for (IntList offsets : offsetsAddElementStart.values()) {
      patchInstructionOffsets(offsets, myCurrentFlow.getEndOffset(myCodeFragment));
    }
    for (IntList offsets : offsetsAddElementEnd.values()) {
      patchInstructionOffsets(offsets, myCurrentFlow.getEndOffset(myCodeFragment));
    }

    ControlFlow result = myCurrentFlow.immutableCopy();

    // register all sub ranges
    for (SubRangeInfo info : mySubRanges) {
      ProgressManager.checkCanceled();
      myControlFlowFactory.registerSubRange(info.myElement, new ControlFlowSubRange(result, info.myStart, info.myEnd), myOptions, myPolicy);
    }
    return result;
  }

  private void startElement(@NotNull PsiElement element) {
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      ProgressManager.checkCanceled();
      if (child instanceof PsiErrorElement &&
          !Comparing.strEqual(((PsiErrorElement)child).getErrorDescription(), JavaPsiBundle.message("expected.semicolon"))) {
        // do not perform control flow analysis for incomplete code
        throw new AnalysisCanceledSoftException(element);
      }
    }
    ProgressManager.checkCanceled();
    myCurrentFlow.startElement(element);

    generateUncheckedExceptionJumpsIfNeeded(element, true);
  }

  private void generateUncheckedExceptionJumpsIfNeeded(@NotNull PsiElement element, boolean atStart) {
    if (!myOptions.isExceptionAfterAssignment() && !atStart) {
      if (element instanceof PsiExpression) {
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(element.getParent());
        if (parent instanceof PsiAssignmentExpression && parent.getParent() instanceof PsiExpressionStatement) {
          generateUncheckedExceptionJumps(element, false);
          return;
        }
      }
      if (element instanceof PsiCodeBlock ||
          element instanceof PsiExpressionStatement &&
          ((PsiExpressionStatement)element).getExpression() instanceof PsiAssignmentExpression) {
        return;
      }
    }
    // optimization: reduce number of instructions
    boolean isGeneratingStatement = element instanceof PsiStatement && !(element instanceof PsiSwitchLabelStatement) &&
                                    !isAbruptStatement(element);
    PsiElement parent = element.getParent();
    boolean isGeneratingCodeBlock = element instanceof PsiCodeBlock && !(parent instanceof PsiSwitchStatement);
    boolean isGeneratingExpression = element instanceof PsiExpression && !(element instanceof PsiSwitchExpression) &&
                                     isAbruptStatement(parent);
    if (isGeneratingStatement || isGeneratingCodeBlock || isGeneratingExpression) {
      generateUncheckedExceptionJumps(element, atStart);
    }
  }

  private static boolean isAbruptStatement(@NotNull PsiElement element) {
    return element instanceof PsiReturnStatement || element instanceof PsiYieldStatement || element instanceof PsiThrowStatement;
  }

  private void finishElement(@NotNull PsiElement element) {
    generateUncheckedExceptionJumpsIfNeeded(element, false);

    myCurrentFlow.finishElement(element);
    patchInstructionOffsets(element);
  }


  private void generateUncheckedExceptionJumps(@NotNull PsiElement element, boolean atStart) {
    // optimization: if we just generated all necessary jumps, do not generate it once again
    if (atStart
        && element instanceof PsiStatement
        && element.getParent() instanceof PsiCodeBlock && element.getPrevSibling() != null) {
      return;
    }

    for (int i = myUnhandledExceptionCatchBlocks.size() - 1; i >= 0; i--) {
      ProgressManager.checkCanceled();
      PsiElement block = myUnhandledExceptionCatchBlocks.get(i);
      // cannot jump to outer catch blocks (belonging to outer try stmt) if current try{} has 'finally' block
      if (block == null) {
        if (!myFinallyBlocks.isEmpty()) {
          break;
        }
        else {
          continue;
        }
      }
      ConditionalThrowToInstruction throwToInstruction = new ConditionalThrowToInstruction(-1); // -1 for init parameter
      myCurrentFlow.addInstruction(throwToInstruction);
      if (!patchUncheckedThrowInstructionIfInsideFinally(throwToInstruction, element, block)) {
        addElementOffsetLater(block, true);
      }
    }

    // generate a jump to the top 'finally' block
    if (!myFinallyBlocks.isEmpty()) {
      final PsiElement finallyBlock = myFinallyBlocks.peek().getElement();
      ConditionalThrowToInstruction throwToInstruction = new ConditionalThrowToInstruction(-2);
      myCurrentFlow.addInstruction(throwToInstruction);
      if (!patchUncheckedThrowInstructionIfInsideFinally(throwToInstruction, element, finallyBlock)) {
        addElementOffsetLater(finallyBlock, true);
      }
    }
  }

  private void generateCheckedExceptionJumps(@NotNull PsiElement element) {
    //generate jumps to all handled exception handlers
    generateExceptionJumps(element, ExceptionUtil.collectUnhandledExceptions(element, element.getParent()));
  }

  private void generateExceptionJumps(@NotNull PsiElement element, Collection<? extends PsiClassType> unhandledExceptions) {
    for (PsiClassType unhandledException : unhandledExceptions) {
      ProgressManager.checkCanceled();
      generateThrow(unhandledException, element);
    }
  }

  private void generateThrow(@NotNull PsiClassType unhandledException, @NotNull PsiElement throwingElement) {
    final List<PsiElement> catchBlocks = findThrowToBlocks(unhandledException);
    for (PsiElement block : catchBlocks) {
      ProgressManager.checkCanceled();
      ConditionalThrowToInstruction instruction = new ConditionalThrowToInstruction(0);
      myCurrentFlow.addInstruction(instruction);
      if (!patchCheckedThrowInstructionIfInsideFinally(instruction, throwingElement, block)) {
        if (block == null) {
          addElementOffsetLater(myCodeFragment, false);
        }
        else {
          instruction.offset--; // -1 for catch block param init
          addElementOffsetLater(block, true);
        }
      }
    }
  }

  private final Map<PsiElement, List<PsiElement>> finallyBlockToUnhandledExceptions = new HashMap<>();

  private boolean patchCheckedThrowInstructionIfInsideFinally(@NotNull ConditionalThrowToInstruction instruction,
                                                              @NotNull PsiElement throwingElement,
                                                              PsiElement elementToJumpTo) {
    final PsiElement finallyBlock = findEnclosingFinallyBlockElement(throwingElement, elementToJumpTo);
    if (finallyBlock == null) return false;

    List<PsiElement> unhandledExceptionCatchBlocks =
      finallyBlockToUnhandledExceptions.computeIfAbsent(finallyBlock, k -> new ArrayList<>());
    int index = unhandledExceptionCatchBlocks.indexOf(elementToJumpTo);
    if (index == -1) {
      index = unhandledExceptionCatchBlocks.size();
      unhandledExceptionCatchBlocks.add(elementToJumpTo);
    }
    // first three return instructions are for normal completion, return statement call completion and unchecked exception throwing completion resp.
    instruction.offset = 3 + index;
    addElementOffsetLater(finallyBlock, false);

    return true;
  }

  private boolean patchUncheckedThrowInstructionIfInsideFinally(@NotNull ConditionalThrowToInstruction instruction,
                                                                @NotNull PsiElement throwingElement,
                                                                @NotNull PsiElement elementToJumpTo) {
    final PsiElement finallyBlock = findEnclosingFinallyBlockElement(throwingElement, elementToJumpTo);
    if (finallyBlock == null) return false;

    // first three return instructions are for normal completion, return statement call completion and unchecked exception throwing completion resp.
    instruction.offset = 2;
    addElementOffsetLater(finallyBlock, false);

    return true;
  }

  @Override
  public void visitCodeFragment(@NotNull JavaCodeFragment codeFragment) {
    startElement(codeFragment);
    int prevOffset = myCurrentFlow.getSize();
    PsiElement[] children = codeFragment.getChildren();
    for (PsiElement child : children) {
      ProgressManager.checkCanceled();
      child.accept(this);
    }

    finishElement(codeFragment);
    registerSubRange(codeFragment, prevOffset);
  }

  private void registerSubRange(@NotNull PsiElement codeFragment, final int startOffset) {
    mySubRanges.add(new SubRangeInfo(codeFragment, startOffset, myCurrentFlow.getSize()));
  }

  @Override
  public void visitCodeBlock(@NotNull PsiCodeBlock block) {
    startElement(block);
    int prevOffset = myCurrentFlow.getSize();
    PsiStatement[] statements = block.getStatements();
    for (PsiStatement statement : statements) {
      ProgressManager.checkCanceled();
      statement.accept(this);
    }

    //each statement should contain at least one instruction in order to getElement(offset) work
    int nextOffset = myCurrentFlow.getSize();
    if (!(block.getParent() instanceof PsiSwitchStatement) && prevOffset == nextOffset) {
      emitEmptyInstruction();
    }
    if (block == myCodeFragment) {
      generateCompactConstructorAssignments();
    }

    finishElement(block);
    if (prevOffset != 0) {
      registerSubRange(block, prevOffset);
    }
  }

  private void emitEmptyInstruction() {
    myCurrentFlow.addInstruction(EmptyInstruction.INSTANCE);
  }

  @Override
  public void visitFile(@NotNull PsiFile file) {
    visitChildren(file);
  }

  @Override
  public void visitBlockStatement(@NotNull PsiBlockStatement statement) {
    startElement(statement);
    final PsiCodeBlock codeBlock = statement.getCodeBlock();
    codeBlock.accept(this);
    finishElement(statement);
  }

  @Override
  public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
    generateYieldInstructions(statement, null, statement.findExitedStatement());
  }

  @Override
  public void visitYieldStatement(@NotNull PsiYieldStatement statement) {
    generateYieldInstructions(statement, statement.getExpression(), statement.findEnclosingExpression());
  }

  private void generateYieldInstructions(PsiStatement statement, PsiExpression valueExpression, PsiElement exitedStatement) {
    startElement(statement);
    generateExpressionInstructions(valueExpression);

    if (exitedStatement != null) {
      callFinallyBlocksOnExit(exitedStatement);

      final Instruction instruction;
      final PsiElement finallyBlock = findEnclosingFinallyBlockElement(statement, exitedStatement);
      final int finallyStartOffset = finallyBlock == null ? -1 : myCurrentFlow.getStartOffset(finallyBlock);
      if (finallyBlock != null && finallyStartOffset != -1) {
        // go out of finally, use return
        CallInstruction callInstruction = (CallInstruction)myCurrentFlow.getInstructions().get(finallyStartOffset - 2);
        instruction = new ReturnInstruction(0, callInstruction);
      }
      else {
        instruction = new GoToInstruction(0, BranchingInstruction.Role.END, PsiTreeUtil.isAncestor(exitedStatement, myCodeFragment, true));
      }
      myCurrentFlow.addInstruction(instruction);
      // exited statement might be out of control flow analyzed
      addElementOffsetLater(exitedStatement, false);
    }
    finishElement(statement);
  }

  private void callFinallyBlocksOnExit(PsiElement exitedStatement) {
    for (final ListIterator<FinallyBlockSubroutine> it = myFinallyBlocks.listIterator(myFinallyBlocks.size()); it.hasPrevious(); ) {
      final FinallyBlockSubroutine finallyBlockSubroutine = it.previous();
      PsiElement finallyBlock = finallyBlockSubroutine.getElement();
      final PsiElement enclosingTryStatement = finallyBlock.getParent();
      if (enclosingTryStatement == null || !PsiTreeUtil.isAncestor(exitedStatement, enclosingTryStatement, false)) {
        break;
      }
      CallInstruction instruction = new CallInstruction(0, 0);
      finallyBlockSubroutine.addCall(instruction);
      myCurrentFlow.addInstruction(instruction);
      addElementOffsetLater(finallyBlock, true);
    }
  }

  private PsiElement findEnclosingFinallyBlockElement(@NotNull PsiElement sourceElement, @Nullable PsiElement jumpElement) {
    PsiElement element = sourceElement;
    while (element != null && !(element instanceof PsiFile)) {
      if (element instanceof PsiCodeBlock
          && element.getParent() instanceof PsiTryStatement
          && ((PsiTryStatement)element.getParent()).getFinallyBlock() == element) {
        // element maybe out of scope to be analyzed
        if (myCurrentFlow.getStartOffset(element.getParent()) == -1) return null;
        if (jumpElement == null || !PsiTreeUtil.isAncestor(element, jumpElement, false)) return element;
      }
      element = element.getParent();
    }
    return null;
  }

  @Override
  public void visitContinueStatement(@NotNull PsiContinueStatement statement) {
    startElement(statement);
    PsiStatement continuedStatement = statement.findContinuedStatement();
    if (continuedStatement != null) {
      PsiElement body = null;
      if (continuedStatement instanceof PsiLoopStatement) {
        body = ((PsiLoopStatement)continuedStatement).getBody();
      }
      if (body == null) {
        body = myCodeFragment;
      }
      callFinallyBlocksOnExit(continuedStatement);

      final Instruction instruction;
      final PsiElement finallyBlock = findEnclosingFinallyBlockElement(statement, continuedStatement);
      final int finallyStartOffset = finallyBlock == null ? -1 : myCurrentFlow.getStartOffset(finallyBlock);
      if (finallyBlock != null && finallyStartOffset != -1) {
        // go out of finally, use return
        CallInstruction callInstruction = (CallInstruction)myCurrentFlow.getInstructions().get(finallyStartOffset - 2);
        instruction = new ReturnInstruction(0, callInstruction);
      }
      else {
        instruction = new GoToInstruction(0, BranchingInstruction.Role.END, PsiTreeUtil.isAncestor(body, myCodeFragment, true));
      }
      myCurrentFlow.addInstruction(instruction);
      addElementOffsetLater(body, false);
    }
    finishElement(statement);
  }

  @Override
  public void visitDeclarationStatement(@NotNull PsiDeclarationStatement statement) {
    startElement(statement);
    int pc = myCurrentFlow.getSize();
    PsiElement[] elements = statement.getDeclaredElements();
    for (PsiElement element : elements) {
      ProgressManager.checkCanceled();
      if (element instanceof PsiClass) {
        element.accept(this);
      }
      else if (element instanceof PsiVariable) {
        processVariable((PsiVariable)element);
      }
    }
    if (pc == myCurrentFlow.getSize()) {
      // generate at least one instruction for declaration
      emitEmptyInstruction();
    }
    finishElement(statement);
  }

  private void processVariable(@NotNull PsiVariable element) {
    final PsiExpression initializer = element.getInitializer();
    generateExpressionInstructions(initializer);

    if (element instanceof PsiLocalVariable && initializer != null ||
        element instanceof PsiField) {
      if (element instanceof PsiLocalVariable && !myPolicy.isLocalVariableAccepted((PsiLocalVariable)element)) return;

      if (myAssignmentTargetsAreElements) {
        startElement(element);
      }

      generateWriteInstruction(element);

      if (myAssignmentTargetsAreElements) {
        finishElement(element);
      }
    }
  }

  @Override
  public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
    startElement(statement);
    PsiStatement body = statement.getBody();
    myStartStatementStack.pushStatement(body == null ? statement : body, true);
    myEndStatementStack.pushStatement(statement, false);

    if (body != null) {
      body.accept(this);
    }

    PsiExpression condition = statement.getCondition();
    if (condition != null) {
      condition.accept(this);
    }

    int offset = myCurrentFlow.getStartOffset(statement);

    Object loopCondition = myConstantEvaluationHelper.computeConstantExpression(statement.getCondition());
    if (loopCondition instanceof Boolean) {
      if (((Boolean)loopCondition).booleanValue()) {
        myCurrentFlow.addInstruction(new GoToInstruction(offset));
      }
      else {
        emitEmptyInstruction();
      }
    }
    else {
      Instruction instruction = new ConditionalGoToInstruction(offset, statement.getCondition());
      myCurrentFlow.addInstruction(instruction);
    }

    myStartStatementStack.popStatement();
    myEndStatementStack.popStatement();
    finishElement(statement);
  }

  @Override
  public void visitEmptyStatement(@NotNull PsiEmptyStatement statement) {
    startElement(statement);
    emitEmptyInstruction();

    finishElement(statement);
  }

  @Override
  public void visitExpressionStatement(@NotNull PsiExpressionStatement statement) {
    startElement(statement);
    final PsiExpression expression = statement.getExpression();
    expression.accept(this);

    for (PsiParameter catchParameter : myCatchParameters) {
      ProgressManager.checkCanceled();
      if (myUnhandledExceptionCatchBlocks.contains(((PsiCatchSection)catchParameter.getDeclarationScope()).getCatchBlock())) {
        continue;
      }
      PsiType type = catchParameter.getType();
      List<PsiType> types =
        type instanceof PsiDisjunctionType ? ((PsiDisjunctionType)type).getDisjunctions() : Collections.singletonList(type);
      for (PsiType subType : types) {
        if (subType instanceof PsiClassType) {
          generateThrow((PsiClassType)subType, statement);
        }
      }
    }
    finishElement(statement);
  }

  @Override
  public void visitExpressionListStatement(@NotNull PsiExpressionListStatement statement) {
    startElement(statement);
    PsiExpression[] expressions = statement.getExpressionList().getExpressions();
    for (PsiExpression expr : expressions) {
      ProgressManager.checkCanceled();
      expr.accept(this);
    }
    finishElement(statement);
  }

  @Override
  public void visitField(@NotNull PsiField field) {
    final PsiExpression initializer = field.getInitializer();
    if (initializer != null) {
      startElement(field);
      initializer.accept(this);
      finishElement(field);
    }
  }

  @Override
  public void visitForStatement(@NotNull PsiForStatement statement) {
    startElement(statement);
    PsiStatement body = statement.getBody();
    myStartStatementStack.pushStatement(body == null ? statement : body, false);
    myEndStatementStack.pushStatement(statement, false);

    PsiStatement initialization = statement.getInitialization();
    if (initialization != null) {
      initialization.accept(this);
    }

    PsiExpression condition = statement.getCondition();
    if (condition != null) {
      condition.accept(this);
    }


    Object loopCondition = myConstantEvaluationHelper.computeConstantExpression(condition);
    if (loopCondition instanceof Boolean || condition == null) {
      boolean value = condition == null || ((Boolean)loopCondition).booleanValue();
      if (value) {
        emitEmptyInstruction();
      }
      else {
        myCurrentFlow.addInstruction(new GoToInstruction(0));
        addElementOffsetLater(statement, false);
      }
    }
    else {
      Instruction instruction = new ConditionalGoToInstruction(0, statement.getCondition());
      myCurrentFlow.addInstruction(instruction);
      addElementOffsetLater(statement, false);
    }

    if (body != null) {
      body.accept(this);
    }

    PsiStatement update = statement.getUpdate();
    if (update != null) {
      update.accept(this);
    }

    int offset = initialization != null
                 ? myCurrentFlow.getEndOffset(initialization)
                 : myCurrentFlow.getStartOffset(statement);
    Instruction instruction = new GoToInstruction(offset);
    myCurrentFlow.addInstruction(instruction);

    myStartStatementStack.popStatement();
    myEndStatementStack.popStatement();
    finishElement(statement);
  }

  @Override
  public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
    handleForeach(statement);
  }

  @Override
  public void visitForeachPatternStatement(@NotNull PsiForeachPatternStatement statement) {
    handleForeach(statement);
  }

  private void handleForeach(@NotNull PsiForeachStatementBase statement) {
    startElement(statement);
    final PsiStatement body = statement.getBody();
    myStartStatementStack.pushStatement(body == null ? statement : body, false);
    myEndStatementStack.pushStatement(statement, false);
    final PsiExpression iteratedValue = statement.getIteratedValue();
    if (iteratedValue != null) {
      iteratedValue.accept(this);
    }

    final int gotoTarget = myCurrentFlow.getSize();
    Instruction instruction = new ConditionalGoToInstruction(0, statement.getIteratedValue());
    myCurrentFlow.addInstruction(instruction);
    addElementOffsetLater(statement, false);

    if (statement instanceof PsiForeachStatement) {
      final PsiParameter iterationParameter = ((PsiForeachStatement)statement).getIterationParameter();
      if (myPolicy.isParameterAccepted(iterationParameter)) {
        generateWriteInstruction(iterationParameter);
      }
    } else if (statement instanceof PsiForeachPatternStatement) {
      PsiPattern pattern = ((PsiForeachPatternStatement)statement).getIterationPattern();
      processPattern(pattern);
    }
    if (body != null) {
      body.accept(this);
    }

    final GoToInstruction gotoInstruction = new GoToInstruction(gotoTarget);
    myCurrentFlow.addInstruction(gotoInstruction);
    myStartStatementStack.popStatement();
    myEndStatementStack.popStatement();
    finishElement(statement);
  }

  @Override
  public void visitIfStatement(@NotNull PsiIfStatement statement) {
    startElement(statement);

    final PsiStatement elseBranch = statement.getElseBranch();
    final PsiStatement thenBranch = statement.getThenBranch();
    PsiExpression conditionExpression = statement.getCondition();

    generateConditionalStatementInstructions(statement, conditionExpression, thenBranch, elseBranch);

    finishElement(statement);
  }

  private void generateConditionalStatementInstructions(@NotNull PsiElement statement,
                                                        @Nullable PsiExpression conditionExpression,
                                                        final PsiElement thenBranch,
                                                        final PsiElement elseBranch) {
    if (thenBranch == null) {
      myStartStatementStack.pushStatement(statement, false);
    }
    else {
      myStartStatementStack.pushStatement(thenBranch, true);
    }
    if (elseBranch == null) {
      myEndStatementStack.pushStatement(statement, false);
    }
    else {
      myEndStatementStack.pushStatement(elseBranch, true);
    }

    myEndJumpRoles.push(elseBranch == null ? BranchingInstruction.Role.END : BranchingInstruction.Role.ELSE);
    myStartJumpRoles.push(thenBranch == null ? BranchingInstruction.Role.END : BranchingInstruction.Role.THEN);

    if (conditionExpression != null) {
      conditionExpression.accept(this);
    }

    boolean thenReachable = true;
    boolean generateConditionalJump = true;
    /*
     * if() statement generated instructions outline:
     *  'if (C) { A } [ else { B } ]' :
     *     generate (C)
     *     cond_goto else
     *     generate (A)
     *     [ goto end ]
     * :else
     *     [ generate (B) ]
     * :end
     */
    if (myOptions.shouldEvaluateConstantIfCondition()) {
      final Object value = myConstantEvaluationHelper.computeConstantExpression(conditionExpression);
      if (value instanceof Boolean) {
        thenReachable = ((Boolean)value).booleanValue();
        generateConditionalJump = false;
        myCurrentFlow.setConstantConditionOccurred(true);
      }
    }
    if (generateConditionalJump || !thenReachable) {
      BranchingInstruction.Role role = elseBranch == null ? BranchingInstruction.Role.END : BranchingInstruction.Role.ELSE;
      Instruction instruction = generateConditionalJump ? new ConditionalGoToInstruction(0, role, conditionExpression) :
        new GoToInstruction(0, role);
      myCurrentFlow.addInstruction(instruction);
      if (elseBranch == null) {
        addElementOffsetLater(statement, false);
      }
      else {
        addElementOffsetLater(elseBranch, true);
      }
    }

    myStartJumpRoles.pop();
    myEndJumpRoles.pop();

    myStartStatementStack.popStatement();
    myEndStatementStack.popStatement();

    if (thenBranch != null) {
      thenBranch.accept(this);
    }
    if (elseBranch != null) {
      Instruction instruction = new GoToInstruction(0);
      myCurrentFlow.addInstruction(instruction);
      addElementOffsetLater(statement, false);
      elseBranch.accept(this);
    }
  }

  @Override
  public void visitLabeledStatement(@NotNull PsiLabeledStatement statement) {
    startElement(statement);
    final PsiStatement innerStatement = statement.getStatement();
    if (innerStatement != null) {
      innerStatement.accept(this);
    }
    finishElement(statement);
  }

  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
    startElement(statement);
    PsiExpression returnValue = statement.getReturnValue();

    if (returnValue != null) {
      myStartStatementStack.pushStatement(returnValue, false);
      myEndStatementStack.pushStatement(returnValue, false);
      returnValue.accept(this);
    }
    addReturnInstruction(statement);
    if (returnValue != null) {
      myStartStatementStack.popStatement();
      myEndStatementStack.popStatement();
    }

    finishElement(statement);
  }

  private void addReturnInstruction(@NotNull PsiElement statement) {
    BranchingInstruction instruction;
    final PsiElement finallyBlock = findEnclosingFinallyBlockElement(statement, null);
    final int finallyStartOffset = finallyBlock == null ? -1 : myCurrentFlow.getStartOffset(finallyBlock);
    if (finallyBlock != null && finallyStartOffset != -1) {
      // go out of finally, go to 2nd return after finally block
      // second return is for return statement called completion
      instruction = new GoToInstruction(1, BranchingInstruction.Role.END, true);
      myCurrentFlow.addInstruction(instruction);
      addElementOffsetLater(finallyBlock, false);
    }
    else {
      instruction = new GoToInstruction(0, BranchingInstruction.Role.END, true);
      myCurrentFlow.addInstruction(instruction);
      if (myFinallyBlocks.isEmpty()) {
        addElementOffsetLater(myCodeFragment, false);
      }
      else {
        instruction.offset = -4; // -4 for return
        addElementOffsetLater(myFinallyBlocks.peek().getElement(), true);
      }
    }
  }

  @Override
  public void visitSwitchLabelStatement(@NotNull PsiSwitchLabelStatement statement) {
    startElement(statement);
    generateCaseValueInstructions(statement.getCaseLabelElementList());
    finishElement(statement);
  }

  @Override
  public void visitSwitchLabeledRuleStatement(@NotNull PsiSwitchLabeledRuleStatement statement) {
    startElement(statement);

    generateCaseValueInstructions(statement.getCaseLabelElementList());

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    PsiSwitchBlock switchBlock = statement.getEnclosingSwitchBlock();
    if (switchBlock != null) {
      Instruction instruction =
        new GoToInstruction(0, BranchingInstruction.Role.END, PsiTreeUtil.isAncestor(switchBlock, myCodeFragment, true));
      myCurrentFlow.addInstruction(instruction);
      addElementOffsetLater(switchBlock, false);
    }

    finishElement(statement);
  }

  private void generateCaseValueInstructions(@Nullable PsiCaseLabelElementList values) {
    if (values != null) {
      for (PsiCaseLabelElement caseValue : values.getElements()) {
        ProgressManager.checkCanceled();
        if (caseValue instanceof PsiExpression) {
          generateExpressionInstructions((PsiExpression)caseValue);
        }
        else if (caseValue instanceof PsiPatternGuard) {
          PsiPatternGuard patternGuard = (PsiPatternGuard)caseValue;
          processPattern(patternGuard.getPattern());
          generateExpressionInstructions(patternGuard.getGuardingExpression());
        }
        else if (caseValue instanceof PsiPattern) {
          processPattern((PsiPattern)caseValue);
        }
      }
    }
  }

  @Override
  public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
    generateSwitchBlockInstructions(statement);
  }

  @Override
  public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
    generateSwitchBlockInstructions(expression);
  }

  public void generateSwitchBlockInstructions(PsiSwitchBlock statement) {
    startElement(statement);

    PsiExpression expr = statement.getExpression();
    if (expr != null) {
      expr.accept(this);
    }

    PsiCodeBlock body = statement.getBody();
    if (body != null) {
      PsiStatement[] statements = body.getStatements();
      boolean needToCreateDefault = false;
      PsiType exprType = expr == null ? null : expr.getType();
      for (PsiStatement aStatement : statements) {
        ProgressManager.checkCanceled();
        if (!(aStatement instanceof PsiSwitchLabelStatementBase)) continue;
        PsiSwitchLabelStatementBase labelStatement = (PsiSwitchLabelStatementBase)aStatement;
        if (labelStatement.isDefaultCase()) {
          needToCreateDefault = true;
        }
        PsiCaseLabelElementList labelElementList = labelStatement.getCaseLabelElementList();
        if (labelElementList != null) {
          for (PsiCaseLabelElement element : labelElementList.getElements()) {
            if (element instanceof PsiDefaultCaseLabelElement ||
                exprType != null && JavaPsiPatternUtil.isUnconditionalForType(element, exprType)) {
              needToCreateDefault = true;
              break;
            }
          }
        }
        Instruction instruction = new ConditionalGoToInstruction(0, expr);
        myCurrentFlow.addInstruction(instruction);
        addElementOffsetLater(aStatement, true);
      }
      if (!needToCreateDefault) {
        Instruction instruction = new GoToInstruction(0);
        myCurrentFlow.addInstruction(instruction);
        addElementOffsetLater(body, false);
      }

      body.accept(this);
    }

    finishElement(statement);
  }

  @Override
  public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
    startElement(statement);

    PsiExpression lock = statement.getLockExpression();
    if (lock != null) {
      lock.accept(this);
    }

    PsiCodeBlock body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    finishElement(statement);
  }

  @Override
  public void visitThrowStatement(@NotNull PsiThrowStatement statement) {
    startElement(statement);

    PsiExpression exception = statement.getException();
    if (exception != null) {
      exception.accept(this);
    }
    final List<PsiElement> blocks = findThrowToBlocks(statement);
    addThrowInstructions(blocks);

    finishElement(statement);
  }

  private void addThrowInstructions(@NotNull List<? extends PsiElement> blocks) {
    PsiElement element;
    if (blocks.isEmpty() || blocks.get(0) == null) {
      ThrowToInstruction instruction = new ThrowToInstruction(0);
      myCurrentFlow.addInstruction(instruction);
      if (myFinallyBlocks.isEmpty()) {
        element = myCodeFragment;
        addElementOffsetLater(element, false);
      }
      else {
        instruction.offset = -2; // -2 to rethrow exception
        element = myFinallyBlocks.peek().getElement();
        addElementOffsetLater(element, true);
      }
    }
    else {
      for (int i = 0; i < blocks.size(); i++) {
        ProgressManager.checkCanceled();
        element = blocks.get(i);
        BranchingInstruction instruction = i == blocks.size() - 1
                                           ? new ThrowToInstruction(0)
                                           : new ConditionalThrowToInstruction(0);
        myCurrentFlow.addInstruction(instruction);
        instruction.offset = -1; // -1 to init catch param
        addElementOffsetLater(element, true);
      }
    }
  }

  /**
   * Find offsets of catch(es) corresponding to this throw statement
   * myCatchParameters and myCatchBlocks arrays should be sorted in ascending scope order (from outermost to innermost)
   *
   * @return list of targets or list of single null element if no appropriate targets found
   */
  @NotNull
  private List<PsiElement> findThrowToBlocks(@NotNull PsiThrowStatement statement) {
    final PsiExpression exceptionExpr = statement.getException();
    if (exceptionExpr == null) return Collections.emptyList();
    final PsiType throwType = exceptionExpr.getType();
    if (!(throwType instanceof PsiClassType)) return Collections.emptyList();
    return findThrowToBlocks((PsiClassType)throwType);
  }

  @NotNull
  private List<PsiElement> findThrowToBlocks(@NotNull PsiClassType throwType) {
    List<PsiElement> blocks = new ArrayList<>();
    for (int i = myCatchParameters.size() - 1; i >= 0; i--) {
      ProgressManager.checkCanceled();
      PsiParameter parameter = myCatchParameters.get(i);
      PsiType catchType = parameter.getType();
      if (ControlFlowUtil.isCaughtExceptionType(throwType, catchType)) {
        blocks.add(myCatchBlocks.get(i));
      }
    }
    if (blocks.isEmpty()) {
      // consider it as throw at the end of the control flow
      blocks.add(null);
    }
    return blocks;
  }

  @Override
  public void visitAssertStatement(@NotNull PsiAssertStatement statement) {
    startElement(statement);

    myStartStatementStack.pushStatement(statement, false);
    myEndStatementStack.pushStatement(statement, false);
    Instruction passByWhenAssertionsDisabled = new ConditionalGoToInstruction(0, BranchingInstruction.Role.END, null);
    myCurrentFlow.addInstruction(passByWhenAssertionsDisabled);
    addElementOffsetLater(statement, false);

    final PsiExpression condition = statement.getAssertCondition();
    boolean generateCondition = true;
    boolean throwReachable = true;
    if (myOptions.shouldEvaluateConstantIfCondition()) {
      Object conditionValue = myConstantEvaluationHelper.computeConstantExpression(condition);
      if (conditionValue instanceof Boolean) {
        throwReachable = !((Boolean)conditionValue);
        generateCondition = false;
        emitEmptyInstruction();
      }
    }

    if (generateCondition) {
      if (condition != null) {
        myStartStatementStack.pushStatement(statement, false);
        myEndStatementStack.pushStatement(statement, false);

        myEndJumpRoles.push(BranchingInstruction.Role.END);
        myStartJumpRoles.push(BranchingInstruction.Role.END);

        condition.accept(this);

        myStartJumpRoles.pop();
        myEndJumpRoles.pop();

        myStartStatementStack.popStatement();
        myEndStatementStack.popStatement();
      }
      Instruction ifTrue = new ConditionalGoToInstruction(0, BranchingInstruction.Role.END, statement.getAssertCondition());
      myCurrentFlow.addInstruction(ifTrue);
      addElementOffsetLater(statement, false);
    } else {
      if (!throwReachable) {
        myCurrentFlow.addInstruction(new GoToInstruction(0, BranchingInstruction.Role.END));
        addElementOffsetLater(statement, false);
      }
    }
    PsiExpression description = statement.getAssertDescription();
    if (description != null) {
      description.accept(this);
    }
    // if description is evaluated, the 'assert' statement cannot complete normally
    // though non-necessarily AssertionError will be thrown (description may throw something, or AssertionError ctor, etc.)
    PsiClassType exceptionClass = JavaPsiFacade.getElementFactory(statement.getProject()).createTypeByFQClassName(
      CommonClassNames.JAVA_LANG_THROWABLE, statement.getResolveScope());
    addThrowInstructions(findThrowToBlocks(exceptionClass));

    myStartStatementStack.popStatement();
    myEndStatementStack.popStatement();

    finishElement(statement);
  }

  @Override
  public void visitTryStatement(@NotNull PsiTryStatement statement) {
    startElement(statement);

    PsiCodeBlock[] catchBlocks = statement.getCatchBlocks();
    PsiParameter[] catchBlockParameters = statement.getCatchBlockParameters();
    int catchNum = Math.min(catchBlocks.length, catchBlockParameters.length);
    myUnhandledExceptionCatchBlocks.push(null);
    for (int i = catchNum - 1; i >= 0; i--) {
      ProgressManager.checkCanceled();
      myCatchParameters.push(catchBlockParameters[i]);
      myCatchBlocks.push(catchBlocks[i]);

      final PsiType type = catchBlockParameters[i].getType();
      // todo cast param
      if (type instanceof PsiClassType && ExceptionUtil.isUncheckedExceptionOrSuperclass((PsiClassType)type)) {
        myUnhandledExceptionCatchBlocks.push(catchBlocks[i]);
      }
      else if (type instanceof PsiDisjunctionType) {
        final PsiType lub = ((PsiDisjunctionType)type).getLeastUpperBound();
        if (lub instanceof PsiClassType && ExceptionUtil.isUncheckedExceptionOrSuperclass((PsiClassType)lub)) {
          myUnhandledExceptionCatchBlocks.push(catchBlocks[i]);
        }
        else if (lub instanceof PsiIntersectionType) {
          for (PsiType conjunct : ((PsiIntersectionType)lub).getConjuncts()) {
            if (conjunct instanceof PsiClassType && ExceptionUtil.isUncheckedExceptionOrSuperclass((PsiClassType)conjunct)) {
              myUnhandledExceptionCatchBlocks.push(catchBlocks[i]);
              break;
            }
          }
        }
      }
    }

    PsiCodeBlock finallyBlock = statement.getFinallyBlock();

    FinallyBlockSubroutine finallyBlockSubroutine = null;
    if (finallyBlock != null) {
      finallyBlockSubroutine = new FinallyBlockSubroutine(finallyBlock);
      myFinallyBlocks.push(finallyBlockSubroutine);
    }

    PsiResourceList resourceList = statement.getResourceList();
    if (resourceList != null) {
      generateCheckedExceptionJumps(resourceList);
      resourceList.accept(this);
    }
    PsiCodeBlock tryBlock = statement.getTryBlock();
    if (tryBlock != null) {
      // javac works as if all checked exceptions can occur at the top of the block
      generateCheckedExceptionJumps(tryBlock);
      tryBlock.accept(this);
    }

    //noinspection StatementWithEmptyBody
    while (myUnhandledExceptionCatchBlocks.pop() != null) ;

    myCurrentFlow.addInstruction(new GoToInstruction(finallyBlock == null ? 0 : -6));
    if (finallyBlock == null) {
      addElementOffsetLater(statement, false);
    }
    else {
      addElementOffsetLater(finallyBlock, true);
    }

    for (int i = 0; i < catchNum; i++) {
      myCatchParameters.pop();
      myCatchBlocks.pop();
    }

    for (int i = catchNum - 1; i >= 0; i--) {
      ProgressManager.checkCanceled();
      if (myPolicy.isParameterAccepted(catchBlockParameters[i])) {
        generateWriteInstruction(catchBlockParameters[i]);
      }
      PsiCodeBlock catchBlock = catchBlocks[i];
      if (catchBlock != null) {
        catchBlock.accept(this);
      }
      else {
        LOG.error("Catch body is null (" + i + ") " + statement.getText());
      }

      myCurrentFlow.addInstruction(new GoToInstruction(finallyBlock == null ? 0 : -6));
      if (finallyBlock == null) {
        addElementOffsetLater(statement, false);
      }
      else {
        addElementOffsetLater(finallyBlock, true);
      }
    }

    if (finallyBlock != null) {
      myFinallyBlocks.pop();
    }

    if (finallyBlock != null) {
      // normal completion, call finally block and proceed
      CallInstruction normalCompletion = new CallInstruction(0, 0);
      finallyBlockSubroutine.addCall(normalCompletion);
      myCurrentFlow.addInstruction(normalCompletion);
      addElementOffsetLater(finallyBlock, true);
      myCurrentFlow.addInstruction(new GoToInstruction(0));
      addElementOffsetLater(statement, false);
      // return completion, call finally block and return
      CallInstruction returnCompletion = new CallInstruction(0, 0);
      finallyBlockSubroutine.addCall(returnCompletion);
      myCurrentFlow.addInstruction(returnCompletion);
      addElementOffsetLater(finallyBlock, true);
      addReturnInstruction(statement);
      // throw exception completion, call finally block and rethrow
      CallInstruction throwExceptionCompletion = new CallInstruction(0, 0);
      finallyBlockSubroutine.addCall(throwExceptionCompletion);
      myCurrentFlow.addInstruction(throwExceptionCompletion);
      addElementOffsetLater(finallyBlock, true);
      final GoToInstruction gotoUncheckedRethrow = new GoToInstruction(0);
      myCurrentFlow.addInstruction(gotoUncheckedRethrow);
      addElementOffsetLater(finallyBlock, false);

      finallyBlock.accept(this);
      final int procStart = myCurrentFlow.getStartOffset(finallyBlock);
      final int procEnd = myCurrentFlow.getEndOffset(finallyBlock);
      for (CallInstruction callInstruction : finallyBlockSubroutine.getCalls()) {
        callInstruction.procBegin = procStart;
        callInstruction.procEnd = procEnd;
      }

      // generate return instructions
      // first three return instructions are for normal completion, return statement call completion and unchecked exception throwing completion resp.

      // normal completion
      myCurrentFlow.addInstruction(new ReturnInstruction(0, normalCompletion));

      // return statement call completion
      myCurrentFlow.addInstruction(new ReturnInstruction(procStart - 3, returnCompletion));

      // unchecked exception throwing completion
      myCurrentFlow.addInstruction(new ReturnInstruction(procStart - 1, throwExceptionCompletion));

      // checked exception throwing completion; need to dispatch to the correct catch clause
      final List<PsiElement> unhandledExceptionCatchBlocks = finallyBlockToUnhandledExceptions.remove(finallyBlock);
      for (int i = 0; unhandledExceptionCatchBlocks != null && i < unhandledExceptionCatchBlocks.size(); i++) {
        ProgressManager.checkCanceled();
        PsiElement catchBlock = unhandledExceptionCatchBlocks.get(i);

        final ReturnInstruction returnInstruction = new ReturnInstruction(0, throwExceptionCompletion);
        returnInstruction.setRethrowFromFinally();
        myCurrentFlow.addInstruction(returnInstruction);
        if (catchBlock == null) {
          // dispatch to rethrowing exception code
          returnInstruction.offset = procStart - 1;
        }
        else {
          // dispatch to catch clause
          returnInstruction.offset--; // -1 for catch block init parameter instruction
          addElementOffsetLater(catchBlock, true);
        }
      }

      // here generated rethrowing code for unchecked exceptions
      gotoUncheckedRethrow.offset = myCurrentFlow.getSize();
      generateUncheckedExceptionJumps(statement, false);
      // just in case
      myCurrentFlow.addInstruction(new ThrowToInstruction(0));
      addElementOffsetLater(myCodeFragment, false);
    }

    finishElement(statement);
  }

  @Override
  public void visitResourceList(final @NotNull PsiResourceList resourceList) {
    startElement(resourceList);

    for (PsiResourceListElement resource : resourceList) {
      ProgressManager.checkCanceled();
      if (resource instanceof PsiResourceVariable) {
        processVariable((PsiVariable)resource);
      }
      else if (resource instanceof PsiResourceExpression) {
        ((PsiResourceExpression)resource).getExpression().accept(this);
      }
    }

    finishElement(resourceList);
  }

  @Override
  public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
    startElement(statement);
    PsiStatement body = statement.getBody();
    if (body == null) {
      myStartStatementStack.pushStatement(statement, false);
    }
    else {
      myStartStatementStack.pushStatement(body, true);
    }
    myEndStatementStack.pushStatement(statement, false);

    PsiExpression condition = statement.getCondition();
    if (condition != null) {
      condition.accept(this);
    }


    Object loopCondition = myConstantEvaluationHelper.computeConstantExpression(statement.getCondition());
    if (loopCondition instanceof Boolean) {
      boolean value = ((Boolean)loopCondition).booleanValue();
      if (value) {
        emitEmptyInstruction();
      }
      else {
        myCurrentFlow.addInstruction(new GoToInstruction(0));
        addElementOffsetLater(statement, false);
      }
    }
    else {
      Instruction instruction = new ConditionalGoToInstruction(0, statement.getCondition());
      myCurrentFlow.addInstruction(instruction);
      addElementOffsetLater(statement, false);
    }

    if (body != null) {
      body.accept(this);
    }
    int offset = myCurrentFlow.getStartOffset(statement);
    Instruction instruction = new GoToInstruction(offset);
    myCurrentFlow.addInstruction(instruction);

    myStartStatementStack.popStatement();
    myEndStatementStack.popStatement();
    finishElement(statement);
  }

  @Override
  public void visitExpressionList(@NotNull PsiExpressionList list) {
    PsiExpression[] expressions = list.getExpressions();
    for (final PsiExpression expression : expressions) {
      ProgressManager.checkCanceled();
      generateExpressionInstructions(expression);
    }
  }

  private void generateExpressionInstructions(@Nullable PsiExpression expression) {
    if (expression != null) {
      // handle short circuit
      myStartStatementStack.pushStatement(expression, false);
      myEndStatementStack.pushStatement(expression, false);

      expression.accept(this);
      myStartStatementStack.popStatement();
      myEndStatementStack.popStatement();
    }
  }

  @Override
  public void visitArrayAccessExpression(@NotNull PsiArrayAccessExpression expression) {
    startElement(expression);

    expression.getArrayExpression().accept(this);
    final PsiExpression indexExpression = expression.getIndexExpression();
    if (indexExpression != null) {
      indexExpression.accept(this);
    }

    finishElement(expression);
  }

  @Override
  public void visitArrayInitializerExpression(@NotNull PsiArrayInitializerExpression expression) {
    startElement(expression);

    PsiExpression[] initializers = expression.getInitializers();
    for (PsiExpression initializer : initializers) {
      ProgressManager.checkCanceled();
      initializer.accept(this);
    }

    finishElement(expression);
  }

  @Override
  public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
    startElement(expression);

    PsiExpression rExpr = expression.getRExpression();
    myStartStatementStack.pushStatement(rExpr == null ? expression : rExpr, false);
    myEndStatementStack.pushStatement(rExpr == null ? expression : rExpr, false);

    int pc = myCurrentFlow.getSize();
    PsiExpression lExpr = PsiUtil.skipParenthesizedExprDown(expression.getLExpression());
    if (lExpr instanceof PsiReferenceExpression) {
      if (!myImplicitCompactConstructorAssignments.isEmpty()) {
        PsiElement target = ((PsiReferenceExpression)lExpr).resolve();
        if (target instanceof PsiField) {
          myImplicitCompactConstructorAssignments.remove(target);
        }
      }
      PsiVariable variable = getUsedVariable((PsiReferenceExpression)lExpr);
      if (variable != null) {
        if (myAssignmentTargetsAreElements) {
          startElement(lExpr);
        }

        PsiExpression qualifier = ((PsiReferenceExpression)lExpr).getQualifierExpression();
        if (qualifier != null) {
          qualifier.accept(this);
        }

        if (expression.getOperationTokenType() != JavaTokenType.EQ) {
          generateReadInstruction(variable);
        }
        if (rExpr != null) {
          rExpr.accept(this);
        }
        generateWriteInstruction(variable);

        if (myAssignmentTargetsAreElements) finishElement(lExpr);
      }
      else {
        if (rExpr != null) {
          rExpr.accept(this);
        }
        lExpr.accept(this); //?
      }
    }
    else if (lExpr != null) {
      lExpr.accept(this);
      if (rExpr != null) {
        rExpr.accept(this);
      }
    }
    //each statement should contain at least one instruction in order to getElement(offset) work
    if (pc == myCurrentFlow.getSize()) emitEmptyInstruction();

    myStartStatementStack.popStatement();
    myEndStatementStack.popStatement();

    finishElement(expression);
  }

  private enum Shortcut {
    NO_SHORTCUT, // a || b
    SKIP_CURRENT_OPERAND, // false || a
    STOP_EXPRESSION         // true || a
  }

  @Override
  public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
    startElement(expression);
    IElementType signTokenType = expression.getOperationTokenType();

    boolean isAndAnd = signTokenType == JavaTokenType.ANDAND;
    boolean isOrOr = signTokenType == JavaTokenType.OROR;

    PsiExpression[] operands = expression.getOperands();
    Boolean lValue = isAndAnd;
    PsiExpression lOperand = null;
    Boolean rValue = null;
    for (int i = 0; i < operands.length; i++) {
      PsiExpression rOperand = operands[i];
      if ((isAndAnd || isOrOr) && myOptions.enableShortCircuit()) {
        Object exprValue = myConstantEvaluationHelper.computeConstantExpression(rOperand);
        if (exprValue instanceof Boolean) {
          myCurrentFlow.setConstantConditionOccurred(true);
          rValue = shouldCalculateConstantExpression(expression) ? (Boolean)exprValue : null;
        } else {
          rValue = null;
        }

        BranchingInstruction.Role role = isAndAnd ? myEndJumpRoles.peek() : myStartJumpRoles.peek();
        PsiElement gotoElement = isAndAnd ? myEndStatementStack.peekElement() : myStartStatementStack.peekElement();
        boolean gotoIsAtStart = isAndAnd ? myEndStatementStack.peekAtStart() : myStartStatementStack.peekAtStart();

        Shortcut shortcut;
        if (lValue != null) {
          shortcut = lValue.booleanValue() == isOrOr ? Shortcut.STOP_EXPRESSION : Shortcut.SKIP_CURRENT_OPERAND;
        }
        else if (rValue != null && rValue.booleanValue() == isOrOr) {
          shortcut = Shortcut.STOP_EXPRESSION;
        }
        else {
          shortcut = Shortcut.NO_SHORTCUT;
        }

        switch (shortcut) {
          case NO_SHORTCUT:
            myCurrentFlow.addInstruction(new ConditionalGoToInstruction(0, role, lOperand));
            addElementOffsetLater(gotoElement, gotoIsAtStart);
            break;

          case STOP_EXPRESSION:
            myCurrentFlow.addInstruction(new GoToInstruction(0, role));
            addElementOffsetLater(gotoElement, gotoIsAtStart);
            rValue = null;
            break;

          case SKIP_CURRENT_OPERAND:
            break;
        }
      }
      generateLOperand(rOperand, i == operands.length - 1 ? null : operands[i + 1], signTokenType);

      lOperand = rOperand;
      lValue = rValue;
    }

    finishElement(expression);
  }

  private void generateLOperand(@NotNull PsiExpression lOperand, @Nullable PsiExpression rOperand, @NotNull IElementType signTokenType) {
    if (rOperand != null) {
      myStartJumpRoles.push(BranchingInstruction.Role.END);
      myEndJumpRoles.push(BranchingInstruction.Role.END);
      PsiElement then = signTokenType == JavaTokenType.OROR ? myStartStatementStack.peekElement() : rOperand;
      boolean thenAtStart = signTokenType != JavaTokenType.OROR || myStartStatementStack.peekAtStart();
      myStartStatementStack.pushStatement(then, thenAtStart);
      PsiElement elseS = signTokenType == JavaTokenType.ANDAND ? myEndStatementStack.peekElement() : rOperand;
      boolean elseAtStart = signTokenType != JavaTokenType.ANDAND || myEndStatementStack.peekAtStart();
      myEndStatementStack.pushStatement(elseS, elseAtStart);
    }
    lOperand.accept(this);
    if (rOperand != null) {
      myStartStatementStack.popStatement();
      myEndStatementStack.popStatement();
      myStartJumpRoles.pop();
      myEndJumpRoles.pop();
    }
  }

  private static boolean isInsideIfCondition(@NotNull PsiExpression expression) {
    PsiElement element = expression;
    while (element instanceof PsiExpression) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiIfStatement && element == ((PsiIfStatement)parent).getCondition()) return true;
      element = parent;
    }
    return false;
  }

  private boolean shouldCalculateConstantExpression(@NotNull PsiExpression expression) {
    return myOptions.shouldEvaluateConstantIfCondition() || !isInsideIfCondition(expression);
  }

  @Override
  public void visitClassObjectAccessExpression(@NotNull PsiClassObjectAccessExpression expression) {
    visitChildren(expression);
  }

  private void visitChildren(@NotNull PsiElement element) {
    startElement(element);

    PsiElement[] children = element.getChildren();
    for (PsiElement child : children) {
      ProgressManager.checkCanceled();
      child.accept(this);
    }

    finishElement(element);
  }

  @Override
  public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
    startElement(expression);

    final PsiExpression condition = expression.getCondition();
    final PsiExpression thenExpression = expression.getThenExpression();
    final PsiExpression elseExpression = expression.getElseExpression();
    generateConditionalStatementInstructions(expression, condition, thenExpression, elseExpression);

    finishElement(expression);
  }

  @Override
  public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
    startElement(expression);

    final PsiExpression operand = expression.getOperand();
    operand.accept(this);

    PsiPattern pattern = expression.getPattern();
    if (pattern != null) {
      processPattern(pattern);
    }

    finishElement(expression);
  }

  private void processPattern(@NotNull PsiPattern pattern) {
    PsiPatternVariable patternVariable = JavaPsiPatternUtil.getPatternVariable(pattern);
    if (patternVariable != null) {
      generateWriteInstruction(patternVariable);
    }
    if (pattern instanceof PsiDeconstructionPattern) {
      PsiDeconstructionPattern deconstructionPattern = (PsiDeconstructionPattern)pattern;
      PsiPattern[] deconstructionComponents = deconstructionPattern.getDeconstructionList().getDeconstructionComponents();
      for (PsiPattern deconstructionComponent : deconstructionComponents) {
        ProgressManager.checkCanceled();
        processPattern(deconstructionComponent);
      }
    }
    else if (pattern instanceof PsiGuardedPattern) {
      generateExpressionInstructions(((PsiGuardedPattern)pattern).getGuardingExpression());
    }
  }

  @Override
  public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
    startElement(expression);
    finishElement(expression);
  }

  @Override
  public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
    startElement(expression);
    final PsiElement body = expression.getBody();
    if (body != null) {
      List<PsiVariable> array = new ArrayList<>();
      addUsedVariables(array, body);
      for (PsiVariable var : array) {
        ProgressManager.checkCanceled();
        generateReadInstruction(var);
      }
    }
    finishElement(expression);
  }

  @Override
  public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
    ArrayDeque<PsiMethodCallExpression> calls = new ArrayDeque<>();
    while (true) {
      calls.addFirst(expression);
      startElement(expression);

      PsiExpression qualifierExpression = expression.getMethodExpression().getQualifierExpression();
      expression = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(qualifierExpression), PsiMethodCallExpression.class);
      if (expression == null) {
        if (qualifierExpression != null) {
          qualifierExpression.accept(this);
        }
        break;
      }
    }

    for (PsiMethodCallExpression call : calls) {
      final PsiExpressionList argumentList = call.getArgumentList();
      argumentList.accept(this);
      // just to increase counter - there is some executable code here
      emitEmptyInstruction();

      //generate jumps to all handled exception handlers
      generateExceptionJumps(call, ExceptionUtil.getUnhandledExceptions(call, call.getParent()));

      finishElement(call);
    }
  }

  @Override
  public void visitNewExpression(@NotNull PsiNewExpression expression) {
    startElement(expression);

    int pc = myCurrentFlow.getSize();
    PsiElement[] children = expression.getChildren();
    for (PsiElement child : children) {
      ProgressManager.checkCanceled();
      child.accept(this);
    }
    //generate jumps to all handled exception handlers
    generateExceptionJumps(expression, ExceptionUtil.getUnhandledExceptions(expression, expression.getParent()));

    if (pc == myCurrentFlow.getSize()) {
      // generate at least one instruction for constructor call
      emitEmptyInstruction();
    }

    finishElement(expression);
  }

  @Override
  public void visitParenthesizedExpression(@NotNull PsiParenthesizedExpression expression) {
    visitChildren(expression);
  }

  @Override
  public void visitPostfixExpression(@NotNull PsiPostfixExpression expression) {
    startElement(expression);

    IElementType op = expression.getOperationTokenType();
    PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());
    if (operand != null) {
      operand.accept(this);
      if (op == JavaTokenType.PLUSPLUS || op == JavaTokenType.MINUSMINUS) {
        if (operand instanceof PsiReferenceExpression) {
          PsiVariable variable = getUsedVariable((PsiReferenceExpression)operand);
          if (variable != null) {
            generateWriteInstruction(variable);
          }
        }
      }
    }

    finishElement(expression);
  }

  @Override
  public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
    startElement(expression);

    PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());
    if (operand != null) {
      IElementType operationSign = expression.getOperationTokenType();
      if (operationSign == JavaTokenType.EXCL) {
        // negation inverts jump targets
        PsiElement topStartStatement = myStartStatementStack.peekElement();
        boolean topAtStart = myStartStatementStack.peekAtStart();
        myStartStatementStack.pushStatement(myEndStatementStack.peekElement(), myEndStatementStack.peekAtStart());
        myEndStatementStack.pushStatement(topStartStatement, topAtStart);
      }

      operand.accept(this);

      if (operationSign == JavaTokenType.EXCL) {
        // negation inverts jump targets
        myStartStatementStack.popStatement();
        myEndStatementStack.popStatement();
      }

      if (operand instanceof PsiReferenceExpression &&
          (operationSign == JavaTokenType.PLUSPLUS || operationSign == JavaTokenType.MINUSMINUS)) {
        PsiVariable variable = getUsedVariable((PsiReferenceExpression)operand);
        if (variable != null) {
          generateWriteInstruction(variable);
        }
      }
    }

    finishElement(expression);
  }

  @Override
  public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
    startElement(expression);

    PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier != null) {
      qualifier.accept(this);
    }

    PsiVariable variable = getUsedVariable(expression);
    if (variable != null) {
      generateReadInstruction(variable);
    }

    finishElement(expression);
  }


  @Override
  public void visitSuperExpression(@NotNull PsiSuperExpression expression) {
    startElement(expression);
    finishElement(expression);
  }

  @Override
  public void visitThisExpression(@NotNull PsiThisExpression expression) {
    startElement(expression);
    finishElement(expression);
  }

  @Override
  public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
    startElement(expression);
    PsiExpression operand = expression.getOperand();
    if (operand != null) {
      operand.accept(this);
    }
    finishElement(expression);
  }

  @Override
  public void visitClass(@NotNull PsiClass aClass) {
    startElement(aClass);
    List<PsiVariable> array = new ArrayList<>();
    // anonymous or local class
    if (aClass instanceof PsiAnonymousClass) {
      final PsiElement arguments = PsiTreeUtil.getChildOfType(aClass, PsiExpressionList.class);
      if (arguments != null) arguments.accept(this);
      PsiElement next = arguments != null ? arguments.getNextSibling() : aClass.getFirstChild();
      while (next != null) {
        addUsedVariables(array, next);
        next = next.getNextSibling();
      }
    }
    else {
      addUsedVariables(array, aClass);
    }

    for (PsiVariable var : array) {
      ProgressManager.checkCanceled();
      generateReadInstruction(var);
    }
    finishElement(aClass);
  }

  private void addUsedVariables(@NotNull List<? super PsiVariable> array, @NotNull PsiElement scope) {
    if (scope instanceof PsiReferenceExpression) {
      PsiVariable variable = getUsedVariable((PsiReferenceExpression)scope);
      if (variable != null) {
        if (!array.contains(variable)) {
          array.add(variable);
        }
      }
    }

    PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      ProgressManager.checkCanceled();
      addUsedVariables(array, child);
    }
  }

  private void generateReadInstruction(@NotNull PsiVariable variable) {
    Instruction instruction = new ReadVariableInstruction(variable);
    myCurrentFlow.addInstruction(instruction);
  }

  private void generateWriteInstruction(@NotNull PsiVariable variable) {
    Instruction instruction = new WriteVariableInstruction(variable);
    myCurrentFlow.addInstruction(instruction);
  }

  @Nullable
  private PsiVariable getUsedVariable(@NotNull PsiReferenceExpression refExpr) {
    if (refExpr.getParent() instanceof PsiMethodCallExpression) return null;
    return myPolicy.getUsedVariable(refExpr);
  }

  private static class FinallyBlockSubroutine {
    private final PsiElement myElement;
    private final List<CallInstruction> myCalls;

    FinallyBlockSubroutine(@NotNull PsiElement element) {
      myElement = element;
      myCalls = new ArrayList<>();
    }

    @NotNull
    public PsiElement getElement() {
      return myElement;
    }

    @NotNull
    public List<CallInstruction> getCalls() {
      return myCalls;
    }

    private void addCall(@NotNull CallInstruction callInstruction) {
      myCalls.add(callInstruction);
    }
  }

  private static final class SubRangeInfo {
    final PsiElement myElement;
    final int myStart;
    final int myEnd;

    private SubRangeInfo(PsiElement element, int start, int end) {
      myElement = element;
      myStart = start;
      myEnd = end;
    }
  }
}
