// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult;
import com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.AssertionDisabledDescriptor;
import com.intellij.codeInspection.dataFlow.lang.DfaInterceptor;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.lang.ir.inst.*;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.*;
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
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;

public class StandardDataFlowRunner implements DataFlowRunner {
  private static final Logger LOG = Logger.getInstance(StandardDataFlowRunner.class);
  // Default complexity limit (maximum allowed attempts to process instruction).
  // Fail as too complex to process if certain instruction is executed more than this limit times.
  // Also used to calculate a threshold when states are forcibly merged.
  protected static final int DEFAULT_MAX_STATES_PER_BRANCH = 300;

  private Instruction[] myInstructions;
  private final @NotNull MultiMap<PsiElement, DfaMemoryState> myNestedClosures = new MultiMap<>();
  private final @NotNull DfaValueFactory myValueFactory;
  private final @NotNull ThreeState myIgnoreAssertions;
  private boolean myInlining = true;
  private boolean myCancelled = false;
  private boolean myWasForciblyMerged = false;
  private @NotNull DfaInterceptor myInterceptor = DfaInterceptor.EMPTY;

  public StandardDataFlowRunner(@NotNull Project project) {
    this(project, null);
  }

  public StandardDataFlowRunner(@NotNull Project project, @Nullable PsiElement context) {
    this(project, context, ThreeState.NO);
  }

  /**
   * @param project current project
   * @param context analysis context element (code block, class, expression, etc.); used to determine whether we can trust
 *                field initializers (e.g. we usually cannot if context is a constructor)
   * @param ignoreAssertions if true, assertion statements will be ignored, as if JVM is started with -da.
   */
  public StandardDataFlowRunner(@NotNull Project project,
                                @Nullable PsiElement context,
                                @NotNull ThreeState ignoreAssertions) {
    myValueFactory = new DfaValueFactory(project, context);
    myIgnoreAssertions = ignoreAssertions;
  }

  @Override
  public @NotNull DfaValueFactory getFactory() {
    return myValueFactory;
  }

  @Override
  public final void cancel() {
    myCancelled = true;
  }

  @Override
  public int getComplexityLimit() {
    return DEFAULT_MAX_STATES_PER_BRANCH;
  }

  private @Nullable Collection<DfaMemoryState> createInitialStates(@NotNull PsiElement psiBlock,
                                                                   @NotNull DfaInterceptor interceptor,
                                                                   boolean allowInlining) {
    PsiElement container = PsiTreeUtil.getParentOfType(psiBlock, PsiClass.class, PsiLambdaExpression.class);
    if (container != null && (!(container instanceof PsiClass) || PsiUtil.isLocalOrAnonymousClass((PsiClass)container))) {
      PsiElement block = DfaPsiUtil.getTopmostBlockInSameClass(container.getParent());
      if (block != null) {
        final RunnerResult result;
        try {
          myInlining = allowInlining;
          result = analyzeMethod(block, interceptor);
        }
        finally {
          myInlining = true;
        }
        if (result == RunnerResult.OK || result == RunnerResult.CANCELLED) {
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
   * @param interceptor an interceptor to use
   * @return result status
   */
  public final @NotNull RunnerResult analyzeMethod(@NotNull PsiElement psiBlock, @NotNull DfaInterceptor interceptor) {
    Collection<DfaMemoryState> initialStates = createInitialStates(psiBlock, interceptor, false);
    return initialStates == null ? RunnerResult.NOT_APPLICABLE : analyzeMethod(psiBlock, interceptor, initialStates);
  }

  /**
   * Analyze this particular method (lambda, class initializer) trying to inline it into outer scope if possible.
   * Usually inlining works, e.g. for lambdas inside stream API calls.
   *
   * @param psiBlock method/lambda/class initializer body
   * @param interceptor an interceptor to use
   * @return result status
   */
  public final @NotNull RunnerResult analyzeMethodWithInlining(@NotNull PsiElement psiBlock, @NotNull DfaInterceptor interceptor) {
    Collection<DfaMemoryState> initialStates = createInitialStates(psiBlock, interceptor, true);
    if (initialStates == null) {
      return RunnerResult.NOT_APPLICABLE;
    }
    if (initialStates.isEmpty()) {
      return RunnerResult.OK;
    }
    return analyzeMethod(psiBlock, interceptor, initialStates);
  }

  /**
   * Analyze given code-block without analyzing any parent and children context
   * @param block block to analyze
   * @param interceptor an interceptor to use
   * @return result status
   */
  public final RunnerResult analyzeCodeBlock(@NotNull PsiCodeBlock block, @NotNull DfaInterceptor interceptor) {
    return analyzeMethod(block, interceptor, Collections.singleton(createMemoryState()));
  }

  final @NotNull RunnerResult analyzeMethod(@NotNull PsiElement psiBlock,
                                            @NotNull DfaInterceptor interceptor,
                                            @NotNull Collection<? extends DfaMemoryState> initialStates) {
    ControlFlow flow = buildFlow(psiBlock);
    if (flow == null) return RunnerResult.NOT_APPLICABLE;
    return analyzeFlow(psiBlock, interceptor, initialStates, flow);
  }

  @NotNull RunnerResult analyzeFlow(@NotNull PsiElement psiBlock,
                                    @NotNull DfaInterceptor interceptor,
                                    @NotNull Collection<? extends DfaMemoryState> initialStates,
                                    ControlFlow flow) {
    List<DfaInstructionState> startingStates = createInitialInstructionStates(psiBlock, initialStates, flow);
    if (startingStates.isEmpty()) {
      return RunnerResult.ABORTED;
    }

    return interpret(psiBlock, interceptor, flow, startingStates);
  }

  protected final @Nullable ControlFlow buildFlow(@NotNull PsiElement psiBlock) {
    try {
      return ControlFlowAnalyzer.buildFlow(psiBlock, myValueFactory, myInlining);
    }
    catch (ProcessCanceledException ex) {
      throw ex;
    }
    catch (RuntimeException | AssertionError e) {
      reportDfaProblem(psiBlock, null, null, e);
    }
    return null;
  }

  protected final @NotNull RunnerResult interpret(@NotNull PsiElement psiBlock,
                                                  @NotNull DfaInterceptor interceptor,
                                                  @NotNull ControlFlow flow,
                                                  @NotNull List<DfaInstructionState> startingStates) {
    int endOffset = flow.getInstructionCount();
    myInstructions = flow.getInstructions();
    myInterceptor = interceptor;
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
        List<DfaInstructionState> states = queue.getNextInstructionStates(joinInstructions);
        if (states.size() > getComplexityLimit()) {
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
            if (processed.size() > getComplexityLimit() / 6) {
              instructionState = mergeBackBranches(instructionState, processed);
              if (containsState(processed, instructionState)) {
                continue;
              }
            }
            if (processed.size() > getComplexityLimit()) {
              LOG.trace("Too complex because too many different possible states");
              return RunnerResult.TOO_COMPLEX;
            }
            if (loopNumber[branching.getIndex()] != 0) {
              processedStates.putValue(branching, instructionState.getMemoryState().createCopy());
            }
          }

          DfaInstructionState[] after = acceptInstruction(instructionState);
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
            if (nextInstruction.getIndex() < instruction.getIndex() &&
                (!(instruction instanceof GotoInstruction) || ((GotoInstruction)instruction).shouldWidenBackBranch())) {
              state.getMemoryState().widen();
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
    DfaVariableValue assertionStatus = AssertionDisabledDescriptor.getAssertionsDisabledVar(myValueFactory);
    if (assertionStatus != null && myIgnoreAssertions != ThreeState.UNSURE) {
      DfaCondition condition = assertionStatus.eq(DfTypes.booleanValue(myIgnoreAssertions.toBoolean()));
      for (DfaMemoryState state : memStates) {
        state.applyCondition(condition);
      }
    }
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

  public @NotNull RunnerResult analyzeMethodRecursively(@NotNull PsiElement block, @NotNull DfaInterceptor interceptor) {
    Collection<DfaMemoryState> states = createInitialStates(block, interceptor, false);
    if (states == null) return RunnerResult.NOT_APPLICABLE;
    return analyzeBlockRecursively(block, states, interceptor);
  }

  public @NotNull RunnerResult analyzeBlockRecursively(@NotNull PsiElement block,
                                                       @NotNull Collection<? extends DfaMemoryState> states,
                                                       @NotNull DfaInterceptor interceptor) {
    RunnerResult result = analyzeMethod(block, interceptor, states);
    if (result != RunnerResult.OK) return result;

    Ref<RunnerResult> ref = Ref.create(RunnerResult.OK);
    forNestedClosures((closure, nestedStates) -> {
      RunnerResult res = analyzeBlockRecursively(closure, nestedStates, interceptor);
      if (res != RunnerResult.OK) {
        ref.set(res);
      }
    });
    return ref.get();
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
    final Set<BranchingInstruction> mayRemoveStatesFor = new HashSet<>();
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

  protected DfaInstructionState @NotNull [] acceptInstruction(@NotNull DfaInstructionState instructionState) {
    Instruction instruction = instructionState.getInstruction();
    return instruction.accept(this, instructionState.getMemoryState());
  }

  /**
   * @return true if analysis should stop when null is dereferenced. 
   * If false, the analysis will be continued under assumption that null value is not null anymore.
   */
  public boolean stopOnNull() {
    return false;
  }

  @Override
  public void createClosureState(PsiElement anchor, DfaMemoryState state) {
    myNestedClosures.putValue(anchor, state.createClosureState());
  }

  protected @NotNull DfaMemoryState createMemoryState() {
    return new DfaMemoryStateImpl(myValueFactory);
  }

  public Instruction @NotNull [] getInstructions() {
    return myInstructions;
  }

  @Override
  public @NotNull Instruction getInstruction(int index) {
    return myInstructions[index];
  }

  @Override
  public @NotNull DfaInterceptor getInterceptor() {
    return myInterceptor;
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
}
