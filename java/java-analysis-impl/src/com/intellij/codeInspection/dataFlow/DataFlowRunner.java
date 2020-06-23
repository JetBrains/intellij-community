// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaExpressionFactory;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import gnu.trove.THashSet;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class DataFlowRunner {
  private static final Logger LOG = Logger.getInstance(DataFlowRunner.class);
  private static final int MERGING_BACK_BRANCHES_THRESHOLD = 50;
  // Maximum allowed attempts to process instruction. Fail as too complex to process if certain instruction
  // is executed more than this limit times.
  static final int MAX_STATES_PER_BRANCH = 300;

  private Instruction[] myInstructions;
  private final @NotNull MultiMap<PsiElement, DfaMemoryState> myNestedClosures = new MultiMap<>();
  private final @NotNull DfaValueFactory myValueFactory;
  private final @NotNull ThreeState myIgnoreAssertions;
  private boolean myInlining = true;
  private boolean myCancelled = false;
  private boolean myWasForciblyMerged = false;
  private final TimeStats myStats = createStatistics();

  public DataFlowRunner(@NotNull Project project) {
    this(project, null);
  }

  public DataFlowRunner(@NotNull Project project, @Nullable PsiElement context) {
    this(project, context, false, ThreeState.NO);
  }

  /**
   * @param project current project
   * @param context analysis context element (code block, class, expression, etc.); used to determine whether we can trust 
 *                field initializers (e.g. we usually cannot if context is a constructor) 
   * @param unknownMembersAreNullable if true every parameter or method return value without nullity annotation is assumed to be nullable
   * @param ignoreAssertions if true, assertion statements will be ignored, as if JVM is started with -da.
   */
  public DataFlowRunner(@NotNull Project project,
                        @Nullable PsiElement context,
                        boolean unknownMembersAreNullable,
                        @NotNull ThreeState ignoreAssertions) {
    myValueFactory = new DfaValueFactory(project, context, unknownMembersAreNullable);
    myIgnoreAssertions = ignoreAssertions;
  }

  public @NotNull DfaValueFactory getFactory() {
    return myValueFactory;
  }

  /**
   * Call this method from the visitor to cancel analysis (e.g. if wanted fact is already established and subsequent analysis
   * is useless). In this case {@link RunnerResult#CANCELLED} will be returned.
   */
  public final void cancel() {
    myCancelled = true;
  }

  private @Nullable Collection<DfaMemoryState> createInitialStates(@NotNull PsiElement psiBlock,
                                                                   @NotNull InstructionVisitor visitor,
                                                                   boolean allowInlining) {
    PsiElement container = PsiTreeUtil.getParentOfType(psiBlock, PsiClass.class, PsiLambdaExpression.class);
    if (container != null && (!(container instanceof PsiClass) || PsiUtil.isLocalOrAnonymousClass((PsiClass)container))) {
      PsiElement block = DfaPsiUtil.getTopmostBlockInSameClass(container.getParent());
      if (block != null) {
        final RunnerResult result;
        try {
          myInlining = allowInlining;
          result = analyzeMethod(block, visitor);
        }
        finally {
          myInlining = true;
        }
        if (result == RunnerResult.OK) {
          final Collection<DfaMemoryState> closureStates = myNestedClosures.get(DfaPsiUtil.getTopmostBlockInSameClass(psiBlock));
          if (allowInlining || !closureStates.isEmpty()) {
            return closureStates;
          }
        }
        return null;
      }
    }

    return Collections.singletonList(createMemoryState());
  }

  /**
   * Analyze this particular method (lambda, class initializer) without inlining this method into parent one.
   * E.g. if supplied method is a lambda within Stream API call chain, it still will be analyzed as separate method.
   * On the other hand, inlining will normally work inside the supplied method.
   *
   * @param psiBlock method/lambda/class initializer body
   * @param visitor a visitor to use
   * @return result status
   */
  public final @NotNull RunnerResult analyzeMethod(@NotNull PsiElement psiBlock, @NotNull InstructionVisitor visitor) {
    Collection<DfaMemoryState> initialStates = createInitialStates(psiBlock, visitor, false);
    return initialStates == null ? RunnerResult.NOT_APPLICABLE : analyzeMethod(psiBlock, visitor, initialStates);
  }

  /**
   * Analyze this particular method (lambda, class initializer) trying to inline it into outer scope if possible.
   * Usually inlining works, e.g. for lambdas inside stream API calls.
   *
   * @param psiBlock method/lambda/class initializer body
   * @param visitor a visitor to use
   * @return result status
   */
  public final @NotNull RunnerResult analyzeMethodWithInlining(@NotNull PsiElement psiBlock, @NotNull InstructionVisitor visitor) {
    Collection<DfaMemoryState> initialStates = createInitialStates(psiBlock, visitor, true);
    if (initialStates == null) {
      return RunnerResult.NOT_APPLICABLE;
    }
    if (initialStates.isEmpty()) {
      return RunnerResult.OK;
    }
    return analyzeMethod(psiBlock, visitor, initialStates);
  }

  /**
   * Analyze given code-block without analyzing any parent and children context
   * @param block block to analyze
   * @param visitor visitor to use
   * @return result status
   */
  public final RunnerResult analyzeCodeBlock(@NotNull PsiCodeBlock block, @NotNull InstructionVisitor visitor) {
    return analyzeMethod(block, visitor, Collections.singleton(createMemoryState()));
  }

  final @NotNull RunnerResult analyzeMethod(@NotNull PsiElement psiBlock,
                                            @NotNull InstructionVisitor visitor,
                                            @NotNull Collection<? extends DfaMemoryState> initialStates) {
    ControlFlow flow = buildFlow(psiBlock);
    if (flow == null) return RunnerResult.NOT_APPLICABLE;
    List<DfaInstructionState> startingStates = createInitialInstructionStates(psiBlock, initialStates, flow);
    if (startingStates.isEmpty()) {
      return RunnerResult.ABORTED;
    }

    return interpret(psiBlock, visitor, flow, startingStates);
  }

  protected final @Nullable ControlFlow buildFlow(@NotNull PsiElement psiBlock) {
    ControlFlow flow = null;
    try {
      myStats.reset();
      flow = new ControlFlowAnalyzer(myValueFactory, psiBlock, myInlining).buildControlFlow();
      myStats.endFlow();

      if (flow != null) {
        new LiveVariablesAnalyzer(flow, myValueFactory).flushDeadVariablesOnStatementFinish();
      }
      myStats.endLVA();
    }
    catch (ProcessCanceledException ex) {
      throw ex;
    }
    catch (RuntimeException | AssertionError e) {
      reportDfaProblem(psiBlock, flow, null, e);
    }
    return flow;
  }

  protected final @NotNull RunnerResult interpret(@NotNull PsiElement psiBlock,
                                                  @NotNull InstructionVisitor visitor,
                                                  @NotNull ControlFlow flow,
                                                  @NotNull List<DfaInstructionState> startingStates) {
    int endOffset = flow.getInstructionCount();
    myInstructions = flow.getInstructions();
    DfaInstructionState lastInstructionState = null;
    myNestedClosures.clear();
    myWasForciblyMerged = false;

    final StateQueue queue = new StateQueue();
    for (DfaInstructionState state : startingStates) {
      queue.offer(state);
    }

    MultiMap<BranchingInstruction, DfaMemoryState> processedStates = MultiMap.createSet();
    MultiMap<BranchingInstruction, DfaMemoryState> incomingStates = MultiMap.createSet();
    try {
      Set<Instruction> joinInstructions = getJoinInstructions();
      int[] loopNumber = flow.getLoopNumbers();

      int stateLimit = Registry.intValue("ide.dfa.state.limit");
      int count = 0;
      while (!queue.isEmpty()) {
        myStats.startMerge();
        List<DfaInstructionState> states = queue.getNextInstructionStates(joinInstructions);
        myStats.endMerge();
        if (states.size() > MAX_STATES_PER_BRANCH) {
          LOG.trace("Too complex because too many different possible states");
          return RunnerResult.TOO_COMPLEX;
        }
        assert !states.isEmpty();
        Instruction instruction = states.get(0).getInstruction();
        beforeInstruction(instruction);
        for (DfaInstructionState instructionState : states) {
          lastInstructionState = instructionState;
          if (count++ > stateLimit) {
            LOG.trace("Too complex data flow: too many instruction states processed");
            return RunnerResult.TOO_COMPLEX;
          }
          ProgressManager.checkCanceled();

          if (LOG.isTraceEnabled()) {
            LOG.trace(instructionState.toString());
          }

          if (instruction instanceof BranchingInstruction) {
            BranchingInstruction branching = (BranchingInstruction)instruction;
            Collection<DfaMemoryState> processed = processedStates.get(branching);
            if (containsState(processed, instructionState)) {
              continue;
            }
            if (processed.size() > MERGING_BACK_BRANCHES_THRESHOLD) {
              myStats.startMerge();
              instructionState = mergeBackBranches(instructionState, processed);
              myStats.endMerge();
              if (containsState(processed, instructionState)) {
                continue;
              }
            }
            if (processed.size() > MAX_STATES_PER_BRANCH) {
              LOG.trace("Too complex because too many different possible states");
              return RunnerResult.TOO_COMPLEX;
            }
            if (loopNumber[branching.getIndex()] != 0) {
              processedStates.putValue(branching, instructionState.getMemoryState().createCopy());
            }
          }

          DfaInstructionState[] after = acceptInstruction(visitor, instructionState);
          if (LOG.isDebugEnabled() && instruction instanceof ControlTransferInstruction && after.length == 0) {
            DfaMemoryState memoryState = instructionState.getMemoryState();
            if (!memoryState.isEmptyStack()) {
              // can pop safely as this memory state is unnecessary anymore (after is empty)
              DfaValue topValue = memoryState.pop();
              if (!(topValue instanceof DfaControlTransferValue || psiBlock instanceof PsiCodeFragment && memoryState.isEmptyStack())) {
                // push back so error report includes this entry
                memoryState.push(topValue);
                reportDfaProblem(psiBlock, flow, instructionState, new RuntimeException("Stack is corrupted"));
              }
            }
          }
          for (DfaInstructionState state : after) {
            Instruction nextInstruction = state.getInstruction();
            if (nextInstruction.getIndex() >= endOffset) {
              continue;
            }
            handleStepOutOfLoop(instruction, nextInstruction, loopNumber, processedStates, incomingStates, states, after, queue);
            if (nextInstruction instanceof BranchingInstruction) {
              BranchingInstruction branching = (BranchingInstruction)nextInstruction;
              if (containsState(processedStates.get(branching), state) ||
                  containsState(incomingStates.get(branching), state)) {
                continue;
              }
              if (loopNumber[branching.getIndex()] != 0) {
                incomingStates.putValue(branching, state.getMemoryState().createCopy());
              }
            }
            queue.offer(state);
          }
        }
        afterInstruction(instruction);
        if (myCancelled) {
          return RunnerResult.CANCELLED;
        }
      }

      myWasForciblyMerged |= queue.wasForciblyMerged();
      myStats.endProcess();
      if (myStats.isTooSlow()) {
        String message = "Too slow DFA\nIf you report this problem, please consider including the attachments\n" + myStats +
                         "\nControl flow size: " + flow.getInstructionCount();
        reportDfaProblem(psiBlock, flow, null, new RuntimeException(message));
      }
      return RunnerResult.OK;
    }
    catch (ProcessCanceledException ex) {
      throw ex;
    }
    catch (RuntimeException | AssertionError e) {
      reportDfaProblem(psiBlock, flow, lastInstructionState, e);
      return RunnerResult.ABORTED;
    }
  }

  protected @NotNull List<DfaInstructionState> createInitialInstructionStates(@NotNull PsiElement psiBlock,
                                                                              @NotNull Collection<? extends DfaMemoryState> memStates,
                                                                              @NotNull ControlFlow flow) {
    initializeVariables(psiBlock, memStates, flow);
    return ContainerUtil.map(memStates, s -> new DfaInstructionState(flow.getInstruction(0), s));
  }

  protected void beforeInstruction(Instruction instruction) {
    
  }

  protected void afterInstruction(Instruction instruction) {
    
  }

  private @NotNull DfaInstructionState mergeBackBranches(DfaInstructionState instructionState, Collection<DfaMemoryState> processed) {
    DfaMemoryStateImpl curState = (DfaMemoryStateImpl)instructionState.getMemoryState();
    Object key = curState.getMergeabilityKey();
    DfaMemoryStateImpl mergedState =
      StreamEx.of(processed).select(DfaMemoryStateImpl.class).filterBy(DfaMemoryStateImpl::getMergeabilityKey, key)
        .foldLeft(curState, (s1, s2) -> {
          s1.merge(s2);
          return s1;
        });
    instructionState = new DfaInstructionState(instructionState.getInstruction(), mergedState);
    myWasForciblyMerged = true;
    return instructionState;
  }

  boolean wasForciblyMerged() {
    return myWasForciblyMerged;
  }

  private @NotNull Set<Instruction> getJoinInstructions() {
    Set<Instruction> joinInstructions = new HashSet<>();
    for (int index = 0; index < myInstructions.length; index++) {
      Instruction instruction = myInstructions[index];
      if (instruction instanceof GotoInstruction) {
        joinInstructions.add(myInstructions[((GotoInstruction)instruction).getOffset()]);
      } else if (instruction instanceof ConditionalGotoInstruction) {
        joinInstructions.add(myInstructions[((ConditionalGotoInstruction)instruction).getOffset()]);
      } else if (instruction instanceof ControlTransferInstruction) {
        IntStreamEx.of(((ControlTransferInstruction)instruction).getPossibleTargetIndices()).elements(myInstructions)
                   .into(joinInstructions);
      } else if (instruction instanceof MethodCallInstruction && !((MethodCallInstruction)instruction).getContracts().isEmpty()) {
        joinInstructions.add(myInstructions[index + 1]);
      }
      else if (instruction instanceof FinishElementInstruction && !((FinishElementInstruction)instruction).getVarsToFlush().isEmpty()) {
        // Good chances to squash something after some vars are flushed
        joinInstructions.add(myInstructions[index + 1]);
      }
    }
    return joinInstructions;
  }

  private static void reportDfaProblem(@NotNull PsiElement psiBlock,
                                       ControlFlow flow,
                                       DfaInstructionState lastInstructionState, Throwable e) {
    Attachment[] attachments = {new Attachment("method_body.txt", psiBlock.getText())};
    if (flow != null) {
      String flowText = flow.toString();
      if (lastInstructionState != null) {
        int index = lastInstructionState.getInstruction().getIndex();
        flowText = flowText.replaceAll("(?m)^", "  ");
        flowText = flowText.replaceFirst("(?m)^ {2}" + index + ": ", "* " + index + ": ");
      }
      attachments = ArrayUtil.append(attachments, new Attachment("flow.txt", flowText));
      if (lastInstructionState != null) {
        DfaMemoryState memoryState = lastInstructionState.getMemoryState();
        String memStateText = null;
        try {
          memStateText = memoryState.toString();
        }
        catch (RuntimeException second) {
          e.addSuppressed(second);
        }
        if (memStateText != null) {
          attachments = ArrayUtil.append(attachments, new Attachment("memory_state.txt", memStateText));
        }
      }
    }
    if (e instanceof RuntimeExceptionWithAttachments) {
      attachments = ArrayUtil.mergeArrays(attachments, ((RuntimeExceptionWithAttachments)e).getAttachments());
    }
    LOG.error(new RuntimeExceptionWithAttachments(e, attachments));
  }

  public @NotNull RunnerResult analyzeMethodRecursively(@NotNull PsiElement block, @NotNull StandardInstructionVisitor visitor) {
    Collection<DfaMemoryState> states = createInitialStates(block, visitor, false);
    if (states == null) return RunnerResult.NOT_APPLICABLE;
    return analyzeBlockRecursively(block, states, visitor);
  }

  public @NotNull RunnerResult analyzeBlockRecursively(@NotNull PsiElement block,
                                                       @NotNull Collection<? extends DfaMemoryState> states,
                                                       @NotNull StandardInstructionVisitor visitor) {
    RunnerResult result = analyzeMethod(block, visitor, states);
    if (result != RunnerResult.OK) return result;

    Ref<RunnerResult> ref = Ref.create(RunnerResult.OK);
    forNestedClosures((closure, nestedStates) -> {
      RunnerResult res = analyzeBlockRecursively(closure, nestedStates, visitor);
      if (res != RunnerResult.OK) {
        ref.set(res);
      }
    });
    return ref.get();
  }

  private void initializeVariables(@NotNull PsiElement psiBlock,
                                   @NotNull Collection<? extends DfaMemoryState> initialStates,
                                   @NotNull ControlFlow flow) {
    List<DfaVariableValue> vars = flow.accessedVariables().collect(Collectors.toList());
    DfaVariableValue assertionStatus = myValueFactory.getAssertionDisabled();
    if (assertionStatus != null && myIgnoreAssertions != ThreeState.UNSURE) {
      for (DfaMemoryState state : initialStates) {
        state.applyCondition(assertionStatus.eq(myValueFactory.getBoolean(myIgnoreAssertions.toBoolean())));
      }
    }
    if (psiBlock instanceof PsiClass) {
      DfaVariableValue thisValue = getFactory().getVarFactory().createThisValue((PsiClass)psiBlock);
      // In class initializer this variable is local until escaped
      for (DfaMemoryState state : initialStates) {
        state.meetDfType(thisValue, DfTypes.LOCAL_OBJECT);
      }
      return;
    }
    PsiElement parent = psiBlock.getParent();
    if (parent instanceof PsiMethod && !(((PsiMethod)parent).isConstructor())) {
      Map<DfaVariableValue, DfaValue> initialValues = StreamEx.of(vars).mapToEntry(
        var -> makeInitialValue(var, (PsiMethod)parent)).nonNullValues().toMap();
      for (DfaMemoryState state : initialStates) {
        initialValues.forEach(state::setVarValue);
      }
    }
  }

  private static @Nullable DfaValue makeInitialValue(DfaVariableValue var, @NotNull PsiMethod method) {
    DfaValueFactory factory = var.getFactory();
    if (var.getDescriptor() instanceof DfaExpressionFactory.ThisDescriptor && var.getType() != null) {
      PsiClass aClass = ((DfaExpressionFactory.ThisDescriptor)var.getDescriptor()).getPsiElement();
      if (method.getContainingClass() == aClass && MutationSignature.fromMethod(method).preservesThis()) {
        // Unmodifiable view, because we cannot call mutating methods, but it's not guaranteed that all fields are stable
        // as fields may not contribute to the visible state
        DfType dfType = DfTypes.typedObject(var.getType(), Nullability.NOT_NULL).meet(Mutability.UNMODIFIABLE_VIEW.asDfType());
        return factory.fromDfType(dfType);
      }
      return null;
    }
    if (!DfaUtil.isEffectivelyUnqualified(var)) return null;
    PsiField field = ObjectUtils.tryCast(var.getPsiVariable(), PsiField.class);
    if (field == null || DfaUtil.ignoreInitializer(field) || DfaUtil.hasInitializationHacks(field)) return null;
    return DfaUtil.getPossiblyNonInitializedValue(factory, field, method);
  }

  private static boolean containsState(Collection<DfaMemoryState> processed,
                                       DfaInstructionState instructionState) {
    if (processed.contains(instructionState.getMemoryState())) {
      return true;
    }
    for (DfaMemoryState state : processed) {
      if (((DfaMemoryStateImpl)state).isSuperStateOf((DfaMemoryStateImpl)instructionState.getMemoryState())) {
        return true;
      }
    }
    return false;
  }

  private void handleStepOutOfLoop(@NotNull Instruction prevInstruction,
                                   @NotNull Instruction nextInstruction,
                                   int @NotNull [] loopNumber,
                                   @NotNull MultiMap<BranchingInstruction, DfaMemoryState> processedStates,
                                   @NotNull MultiMap<BranchingInstruction, DfaMemoryState> incomingStates,
                                   @NotNull List<DfaInstructionState> inFlightStates,
                                   DfaInstructionState @NotNull [] afterStates,
                                   @NotNull StateQueue queue) {
    if (loopNumber[prevInstruction.getIndex()] == 0 || inSameLoop(prevInstruction, nextInstruction, loopNumber)) {
      return;
    }
    // stepped out of loop. destroy all memory states from the loop, we don't need them anymore

    // but do not touch yet states being handled right now
    for (DfaInstructionState state : inFlightStates) {
      Instruction instruction = state.getInstruction();
      if (inSameLoop(prevInstruction, instruction, loopNumber)) {
        return;
      }
    }
    for (DfaInstructionState state : afterStates) {
      Instruction instruction = state.getInstruction();
      if (inSameLoop(prevInstruction, instruction, loopNumber)) {
        return;
      }
    }
    // and still in queue
    if (!queue.processAll(state -> {
      Instruction instruction = state.getInstruction();
      return !inSameLoop(prevInstruction, instruction, loopNumber);
    })) return;

    // now remove obsolete memory states
    final Set<BranchingInstruction> mayRemoveStatesFor = new THashSet<>();
    for (Instruction instruction : myInstructions) {
      if (inSameLoop(prevInstruction, instruction, loopNumber) && instruction instanceof BranchingInstruction) {
        mayRemoveStatesFor.add((BranchingInstruction)instruction);
      }
    }

    for (BranchingInstruction instruction : mayRemoveStatesFor) {
      processedStates.remove(instruction);
      incomingStates.remove(instruction);
    }
  }

  private static boolean inSameLoop(@NotNull Instruction prevInstruction, @NotNull Instruction nextInstruction, int @NotNull [] loopNumber) {
    return loopNumber[nextInstruction.getIndex()] == loopNumber[prevInstruction.getIndex()];
  }

  protected DfaInstructionState @NotNull [] acceptInstruction(@NotNull InstructionVisitor visitor, @NotNull DfaInstructionState instructionState) {
    Instruction instruction = instructionState.getInstruction();
    DfaInstructionState[] states = instruction.accept(this, instructionState.getMemoryState(), visitor);

    if (instruction instanceof ClosureInstruction) {
      PsiElement closure = ((ClosureInstruction)instruction).getClosureElement();
      if (closure instanceof PsiClass) {
        registerNestedClosures(instructionState, (PsiClass)closure);
      } else if (closure instanceof PsiLambdaExpression) {
        registerNestedClosures(instructionState, (PsiLambdaExpression)closure);
      }
    }

    return states;
  }

  private void registerNestedClosures(@NotNull DfaInstructionState instructionState, @NotNull PsiClass nestedClass) {
    DfaMemoryState state = instructionState.getMemoryState();
    for (PsiMethod method : nestedClass.getMethods()) {
      PsiCodeBlock body = method.getBody();
      if (body != null && (method.isPhysical() || !nestedClass.isPhysical())) {
        // Skip analysis of non-physical methods of physical class (possibly autogenerated by some plugin like Lombok)
        createClosureState(body, state);
      }
    }
    for (PsiClassInitializer initializer : nestedClass.getInitializers()) {
      createClosureState(initializer.getBody(), state);
    }
    for (PsiField field : nestedClass.getFields()) {
      createClosureState(field, state);
    }
  }

  private void registerNestedClosures(@NotNull DfaInstructionState instructionState, @NotNull PsiLambdaExpression expr) {
    DfaMemoryState state = instructionState.getMemoryState();
    PsiElement body = expr.getBody();
    if (body != null) {
      createClosureState(body, state);
    }
  }

  private void createClosureState(PsiElement anchor, DfaMemoryState state) {
    myNestedClosures.putValue(anchor, state.createClosureState());
  }

  protected @NotNull TimeStats createStatistics() {
    return new TimeStats();
  }

  protected @NotNull DfaMemoryState createMemoryState() {
    return new DfaMemoryStateImpl(myValueFactory);
  }

  public Instruction @NotNull [] getInstructions() {
    return myInstructions;
  }

  public @NotNull Instruction getInstruction(int index) {
    return myInstructions[index];
  }

  public void forNestedClosures(BiConsumer<? super PsiElement, ? super Collection<? extends DfaMemoryState>> consumer) {
    // Copy to avoid concurrent modifications
    MultiMap<PsiElement, DfaMemoryState> closures = new MultiMap<>(myNestedClosures);
    for (PsiElement closure : closures.keySet()) {
      List<DfaVariableValue> unusedVars = StreamEx.of(getFactory().getValues())
        .select(DfaVariableValue.class)
        .filter(var -> var.getQualifier() == null)
        .filter(var -> var.getPsiVariable() instanceof PsiVariable &&
                       !VariableAccessUtils.variableIsUsed((PsiVariable)var.getPsiVariable(), closure))
        .toList();
      Collection<? extends DfaMemoryState> states = closures.get(closure);
      if (!unusedVars.isEmpty()) {
        List<DfaMemoryStateImpl> stateList = StreamEx.of(states)
          .peek(state -> unusedVars.forEach(state::flushVariable))
          .map(state -> (DfaMemoryStateImpl)state).distinct().toList();
        states = StateQueue.mergeGroup(stateList);
      }
      consumer.accept(closure, states);
    }
  }

  protected static class TimeStats {
    private static final long DFA_EXECUTION_TIME_TO_REPORT_NANOS = TimeUnit.SECONDS.toNanos(30);
    private final @Nullable ThreadMXBean myMxBean;
    private long myStart;
    private long myMergeStart, myFlowTime, myLVATime, myMergeTime, myProcessTime;

    TimeStats() {
      this(ApplicationManager.getApplication().isInternal());
    }

    public TimeStats(boolean record) {
      myMxBean = record ? ManagementFactory.getThreadMXBean() : null;
      reset();
    }

    void reset() {
      if (myMxBean == null) {
        myStart = 0;
      }
      else {
        myStart = myMxBean.getCurrentThreadCpuTime();
      }
      myMergeStart = myFlowTime = myLVATime = myMergeTime = myProcessTime = 0;
    }

    void endFlow() {
      if (myMxBean != null) {
        myFlowTime = myMxBean.getCurrentThreadCpuTime() - myStart;
      }
    }

    void endLVA() {
      if (myMxBean != null) {
        myLVATime = myMxBean.getCurrentThreadCpuTime() - myStart - myFlowTime;
      }
    }

    void startMerge() {
      if (myMxBean != null) {
        myMergeStart = System.nanoTime();
      }
    }

    void endMerge() {
      if (myMxBean != null) {
        myMergeTime += System.nanoTime() - myMergeStart;
      }
    }

    void endProcess() {
      if (myMxBean != null) {
        myProcessTime = myMxBean.getCurrentThreadCpuTime() - myStart;
      }
    }

    boolean isTooSlow() {
      return myProcessTime > DFA_EXECUTION_TIME_TO_REPORT_NANOS;
    }

    @Override
    public String toString() {
      double flowTime = myFlowTime/1e9;
      double lvaTime = myLVATime / 1e9;
      double mergeTime = myMergeTime/1e9;
      double interpretTime = (myProcessTime - myFlowTime - myLVATime - myMergeTime)/1e9;
      double totalTime = myProcessTime/1e9;
      String format = "Building ControlFlow: %.2fs\nLiveVariableAnalyzer: %.2fs\nMerging states: %.2fs\nInterpreting: %.2fs\nTotal: %.2fs";
      return String.format(Locale.ENGLISH, format, flowTime, lvaTime, mergeTime, interpretTime, totalTime);
    }
  }
}
