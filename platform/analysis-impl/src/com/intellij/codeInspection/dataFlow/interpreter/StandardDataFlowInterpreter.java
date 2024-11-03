// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.interpreter;

import com.intellij.codeInspection.dataFlow.lang.DfaListener;
import com.intellij.codeInspection.dataFlow.lang.ir.*;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Standard interpreter implementation
 */
public class StandardDataFlowInterpreter implements DataFlowInterpreter {
  /**
   * Default complexity limit (maximum allowed attempts to process instruction).
   * Fail as too complex to process if certain instruction is executed more than this limit times.
   * Also used to calculate a threshold when states are forcibly merged.
   */
  public static final int DEFAULT_MAX_STATES_PER_BRANCH = 300;
  private static final Logger LOG = Logger.getInstance(StandardDataFlowInterpreter.class);
  final @NotNull ControlFlow myFlow;
  private final Instruction @NotNull [] myInstructions;
  private final @NotNull DfaListener myListener;
  private final @NotNull MultiMap<PsiElement, DfaMemoryState> myNestedClosures = new MultiMap<>();
  private final @NotNull PsiElement myPsiAnchor;
  private final @NotNull DfaValueFactory myValueFactory;
  private final boolean myStopOnNull;
  private final boolean myStopOnCast;
  private boolean myCancelled = false;
  private boolean myWasForciblyMerged = false;

  public StandardDataFlowInterpreter(@NotNull ControlFlow flow, @NotNull DfaListener listener) {
    this(flow, listener, false);
  }

  public StandardDataFlowInterpreter(@NotNull ControlFlow flow,
                                     @NotNull DfaListener listener,
                                     boolean stopOnNull) {
    this(flow, listener, stopOnNull, false);
  }

  public StandardDataFlowInterpreter(@NotNull ControlFlow flow,
                                     @NotNull DfaListener listener,
                                     boolean stopOnNull, 
                                     boolean stopOnCast) {
    myFlow = flow;
    myInstructions = flow.getInstructions();
    myListener = listener;
    myPsiAnchor = flow.getPsiAnchor();
    myValueFactory = flow.getFactory();
    myStopOnNull = stopOnNull;
    myStopOnCast = stopOnCast;
  }

  @Override
  public @NotNull DfaValueFactory getFactory() {
    return myValueFactory;
  }

  @Override
  public final @NotNull RunnerResult interpret(@NotNull DfaMemoryState startingState) {
    return interpret(List.of(new DfaInstructionState(getInstruction(0), startingState)));
  }

  @Override
  public final @NotNull RunnerResult interpret(@NotNull List<? extends DfaInstructionState> startingStates) {
    int endOffset = myFlow.getInstructionCount();
    DfaInstructionState lastInstructionState = null;
    myNestedClosures.clear();
    myWasForciblyMerged = false;
    myValueFactory.setContext(myPsiAnchor);

    final StateQueue queue = new StateQueue();
    for (DfaInstructionState state : startingStates) {
      queue.offer(state);
    }

    MultiMap<Instruction, DfaMemoryState> processedStates = MultiMap.createSet();
    MultiMap<Instruction, DfaMemoryState> incomingStates = MultiMap.createSet();
    try {
      Set<Instruction> joinInstructions = getJoinInstructions();
      int[] loopNumber = myFlow.getLoopNumbers();

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

          if (!instruction.isLinear()) {
            Collection<DfaMemoryState> processed = processedStates.get(instruction);
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
            if (loopNumber[instruction.getIndex()] != 0) {
              processedStates.putValue(instruction, instructionState.getMemoryState().createCopy());
            }
          }

          DfaInstructionState[] after = acceptInstruction(instructionState);
          if (LOG.isDebugEnabled() && instruction instanceof ControlTransferInstruction && after.length == 0) {
            DfaMemoryState memoryState = instructionState.getMemoryState();
            if (!memoryState.isEmptyStack()) {
              // can pop safely as this memory state is unnecessary anymore (after is empty)
              DfaValue topValue = memoryState.pop();
              if (!(topValue instanceof DfaControlTransferValue || myPsiAnchor instanceof PsiCodeFragment && memoryState.isEmptyStack())) {
                // push back so error report includes this entry
                memoryState.push(topValue);
                reportDfaProblem(instructionState, new RuntimeException("Stack is corrupted"));
              }
            }
          }
          for (DfaInstructionState state : after) {
            Instruction nextInstruction = state.getInstruction();
            if (nextInstruction.getIndex() >= endOffset) {
              continue;
            }
            handleStepOutOfLoop(instruction, nextInstruction, loopNumber, processedStates, incomingStates, states, after, queue);
            if (!nextInstruction.isLinear()) {
              if (containsState(processedStates.get(nextInstruction), state) ||
                  containsState(incomingStates.get(nextInstruction), state)) {
                continue;
              }
              if (loopNumber[nextInstruction.getIndex()] != 0) {
                incomingStates.putValue(nextInstruction, state.getMemoryState().createCopy());
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
      reportDfaProblem(lastInstructionState, e);
      return RunnerResult.ABORTED;
    }
  }

  private @NotNull Set<Instruction> getJoinInstructions() {
    Set<Instruction> joinInstructions = new HashSet<>();
    StreamEx.of(myInstructions)
      .remove(Instruction::isLinear)
      .flatMap(inst -> IntStreamEx.of(inst.getSuccessorIndexes()).elements(myInstructions))
      .into(joinInstructions);
    for (int index = 0; index < myInstructions.length; index++) {
      Instruction instruction = myInstructions[index];
      if (instruction instanceof FinishElementInstruction finishInstruction && !finishInstruction.mayFlushSomething()) {
        // Good chances to squash something after some vars are flushed
        joinInstructions.add(myInstructions[index + 1]);
      }
    }
    return joinInstructions;
  }

  public @NotNull MultiMap<@NotNull PsiElement, @NotNull DfaMemoryState> getClosures() {
    return myNestedClosures;
  }

  private static boolean containsState(Collection<? extends DfaMemoryState> processed,
                                       DfaInstructionState instructionState) {
    if (processed.contains(instructionState.getMemoryState())) {
      return true;
    }
    for (DfaMemoryState state : processed) {
      if (state.isSuperStateOf(instructionState.getMemoryState())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public final void cancel() {
    myCancelled = true;
  }

  @Override
  public void createClosureState(PsiElement anchor, DfaMemoryState state) {
    myNestedClosures.putValue(anchor, state.createClosureState());
  }

  @Override
  public int getComplexityLimit() {
    return DEFAULT_MAX_STATES_PER_BRANCH;
  }

  @Override
  public @NotNull Instruction getInstruction(int index) {
    return myInstructions[index];
  }

  @Override
  public @NotNull DfaListener getListener() {
    return myListener;
  }

  private void handleStepOutOfLoop(@NotNull Instruction prevInstruction,
                                   @NotNull Instruction nextInstruction,
                                   int @NotNull [] loopNumber,
                                   @NotNull MultiMap<Instruction, DfaMemoryState> processedStates,
                                   @NotNull MultiMap<Instruction, DfaMemoryState> incomingStates,
                                   @NotNull List<? extends DfaInstructionState> inFlightStates,
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
    final Set<Instruction> mayRemoveStatesFor = new HashSet<>();
    for (Instruction instruction : myInstructions) {
      if (inSameLoop(prevInstruction, instruction, loopNumber) && !instruction.isLinear()) {
        mayRemoveStatesFor.add(instruction);
      }
    }

    for (Instruction instruction : mayRemoveStatesFor) {
      processedStates.remove(instruction);
      incomingStates.remove(instruction);
    }
  }

  private static boolean inSameLoop(@NotNull Instruction prevInstruction, @NotNull Instruction nextInstruction, int @NotNull [] loopNumber) {
    return loopNumber[nextInstruction.getIndex()] == loopNumber[prevInstruction.getIndex()];
  }

  private @NotNull DfaInstructionState mergeBackBranches(DfaInstructionState instructionState, Collection<? extends DfaMemoryState> processed) {
    DfaMemoryStateImpl curState = (DfaMemoryStateImpl)instructionState.getMemoryState();
    int curStateStackSize = curState.getStackSize();
    if (processed.size() > 10 && curStateStackSize > 10) {
      for (DfaMemoryState state : processed) {
        int diff = curStateStackSize - state.getStackSize();
        if (diff > 10) {
          throw new IllegalStateException("Stack for instruction %d increased by %d; it's likely that IR was built incorrectly"
              .formatted(instructionState.getInstruction().getIndex(), diff));
        }
      }
    }
    Object key = curState.getMergeabilityKey();
    DfaMemoryStateImpl mergedState =
      StreamEx.of(processed).filterBy(DfaMemoryState::getMergeabilityKey, key)
        .foldLeft(curState, (s1, s2) -> {
          s1.merge(s2);
          return s1;
        });
    mergedState.widen();
    instructionState = new DfaInstructionState(instructionState.getInstruction(), mergedState);
    myWasForciblyMerged = true;
    return instructionState;
  }

  protected DfaInstructionState @NotNull [] acceptInstruction(@NotNull DfaInstructionState instructionState) {
    Instruction instruction = instructionState.getInstruction();
    return instruction.accept(this, instructionState.getMemoryState());
  }

  protected void beforeInstruction(Instruction instruction) {

  }

  protected void afterInstruction(Instruction instruction) {

  }

  public boolean wasForciblyMerged() {
    return myWasForciblyMerged;
  }

  /**
   * @return true if analysis should stop when null is dereferenced.
   * If false, the analysis will be continued under assumption that null value is not null anymore.
   */
  public boolean stopOnNull() {
    return myStopOnNull;
  }

  /**
   * @return true if analysis should stop when impossible cast happens
   * If false, the analysis will be continued
   */
  public boolean stopOnCast() {
    return myStopOnCast;
  }

  private void reportDfaProblem(@Nullable DfaInstructionState lastInstructionState, @NotNull Throwable e) {
    Attachment[] attachments = {new Attachment("method_body.txt", myPsiAnchor.getText())};
    try {
      String flowText = myFlow.toString();
      if (lastInstructionState != null) {
        int index = lastInstructionState.getInstruction().getIndex();
        flowText = flowText.replaceAll("(?m)^", "  ");
        flowText = flowText.replaceFirst("(?m)^ {2}" + index + ": ", "* " + index + ": ");
      }
      attachments = ArrayUtil.append(attachments, new Attachment("flow.txt", flowText));
    }
    catch (ProcessCanceledException pce) {
      throw pce;
    }
    catch (Exception ex) {
      ex.addSuppressed(e);
      LOG.error("While gathering flow text", ex);
    }
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
    LOG.error("Dataflow interpretation error (wasForciblyMerged = " + myWasForciblyMerged + ")", e, attachments);
  }
}
