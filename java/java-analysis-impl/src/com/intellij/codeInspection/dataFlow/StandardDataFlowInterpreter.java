// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult;
import com.intellij.codeInspection.dataFlow.java.inst.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl;
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

import static com.intellij.codeInspection.dataFlow.StandardDataFlowRunner.DEFAULT_MAX_STATES_PER_BRANCH;

public class StandardDataFlowInterpreter implements DataFlowInterpreter {
  private static final Logger LOG = Logger.getInstance(StandardDataFlowInterpreter.class);
  private final @NotNull ControlFlow myFlow;
  private final Instruction @NotNull [] myInstructions;
  private final @NotNull DfaListener myListener;
  private final @NotNull MultiMap<PsiElement, DfaMemoryState> myNestedClosures = new MultiMap<>();
  private final @NotNull PsiElement myPsiAnchor;
  private final @NotNull DfaValueFactory myValueFactory;
  private final boolean myStopOnNull;
  private boolean myCancelled = false;
  private boolean myWasForciblyMerged = false;

  public StandardDataFlowInterpreter(@NotNull ControlFlow flow, @NotNull DfaListener listener) {
    this(flow, listener, false);
  }

  public StandardDataFlowInterpreter(@NotNull ControlFlow flow,
                                     @NotNull DfaListener listener,
                                     boolean stopOnNull) {
    myFlow = flow;
    myInstructions = flow.getInstructions();
    myListener = listener;
    myPsiAnchor = flow.getPsiAnchor();
    myValueFactory = flow.getFactory();
    myStopOnNull = stopOnNull;
  }

  @Override
  public @NotNull DfaValueFactory getFactory() {
    return myValueFactory;
  }

  public final @NotNull RunnerResult interpret(@NotNull DfaMemoryState startingState) {
    return interpret(List.of(new DfaInstructionState(getInstruction(0), startingState)));
  }

  public final @NotNull RunnerResult interpret(@NotNull List<DfaInstructionState> startingStates) {
    int endOffset = myFlow.getInstructionCount();
    DfaInstructionState lastInstructionState = null;
    myNestedClosures.clear();
    myWasForciblyMerged = false;
    myValueFactory.setContext(myPsiAnchor);

    final StateQueue queue = new StateQueue();
    for (DfaInstructionState state : startingStates) {
      queue.offer(state);
    }

    MultiMap<BranchingInstruction, DfaMemoryState> processedStates = MultiMap.createSet();
    MultiMap<BranchingInstruction, DfaMemoryState> incomingStates = MultiMap.createSet();
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
      reportDfaProblem(lastInstructionState, e);
      return RunnerResult.ABORTED;
    }
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
        IntStreamEx.of(instruction.getSuccessorIndexes()).elements(myInstructions)
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

  public MultiMap<PsiElement, DfaMemoryState> getClosures() {
    return myNestedClosures;
  }

  private static boolean containsState(Collection<DfaMemoryState> processed,
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

  private @NotNull DfaInstructionState mergeBackBranches(DfaInstructionState instructionState, Collection<DfaMemoryState> processed) {
    DfaMemoryStateImpl curState = (DfaMemoryStateImpl)instructionState.getMemoryState();
    Object key = curState.getMergeabilityKey();
    DfaMemoryStateImpl mergedState =
      StreamEx.of(processed).select(JvmDfaMemoryStateImpl.class).filterBy(DfaMemoryStateImpl::getMergeabilityKey, key)
        .foldLeft(curState, (s1, s2) -> {
          s1.merge(s2);
          return s1;
        });
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

  boolean wasForciblyMerged() {
    return myWasForciblyMerged;
  }

  /**
   * @return true if analysis should stop when null is dereferenced.
   * If false, the analysis will be continued under assumption that null value is not null anymore.
   */
  public boolean stopOnNull() {
    return myStopOnNull;
  }

  private void reportDfaProblem(@Nullable DfaInstructionState lastInstructionState, @NotNull Throwable e) {
    Attachment[] attachments = {new Attachment("method_body.txt", myPsiAnchor.getText())};
    String flowText = myFlow.toString();
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
    LOG.error("Dataflow interpretation error", e, attachments);
  }
}
