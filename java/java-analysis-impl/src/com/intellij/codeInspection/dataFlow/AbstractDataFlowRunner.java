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

import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EmptyStackException;
import java.util.Set;

public abstract class AbstractDataFlowRunner {

  // Maximum allowed attempts to process instruction. Fail as too complex to process if certain instruction
  // is executed more than this limit times.
  public static final int MAX_STATES_PER_BRANCH = 300;
  protected static final Key<Integer> TOO_EXPENSIVE_HASH = Key.create("TOO_EXPENSIVE_HASH");
  private static final Logger LOG = Logger.getInstance(AbstractDataFlowRunner.class);

  private Instruction[] myInstructions;

  protected void prepareAnalysis(@NotNull PsiElement psiBlock, Iterable<DfaMemoryState> initialStates) {
  }

  public final RunnerResult analyzeMethod(@NotNull PsiElement psiBlock,
                                          @NotNull InstructionVisitor visitor,
                                          @NotNull Collection<DfaMemoryState> initialStates) {
    try {
      prepareAnalysis(psiBlock, initialStates);

      final ControlFlow flow = createControlFlowAnalyzer(psiBlock).buildControlFlow();
      if (flow == null) return RunnerResult.NOT_APPLICABLE;

      int endOffset = flow.getInstructionCount();
      myInstructions = flow.getInstructions();

      Set<Instruction> joinInstructions = ContainerUtil.newHashSet();
      for (int index = 0; index < myInstructions.length; index++) {
        Instruction instruction = myInstructions[index];
        if (instruction instanceof GotoInstruction) {
          joinInstructions.add(myInstructions[((GotoInstruction)instruction).getOffset()]);
        }
        else if (instruction instanceof ConditionalGotoInstruction) {
          joinInstructions.add(myInstructions[((ConditionalGotoInstruction)instruction).getOffset()]);
        }
        else if (instruction instanceof MethodCallInstruction && !((MethodCallInstruction)instruction).getContracts().isEmpty()) {
          joinInstructions.add(myInstructions[index + 1]);
        }
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("Analyzing code block: " + psiBlock.getText());
        for (int i = 0; i < myInstructions.length; i++) {
          LOG.debug(i + ": " + myInstructions[i].toString());
        }
      }

      final Integer tooExpensiveHash = psiBlock.getUserData(TOO_EXPENSIVE_HASH);
      if (tooExpensiveHash != null && tooExpensiveHash == psiBlock.getText().hashCode()) {
        LOG.debug("Too complex because hasn't changed since being too complex already");
        return RunnerResult.TOO_COMPLEX;
      }

      final StateQueue queue = new StateQueue();
      for (final DfaMemoryState initialState : initialStates) {
        queue.offer(new DfaInstructionState(myInstructions[0], initialState));
      }

      MultiMap<BranchingInstruction, DfaMemoryState> processedStates = MultiMap.createSet();
      MultiMap<BranchingInstruction, DfaMemoryState> incomingStates = MultiMap.createSet();

      final long msLimit = shouldCheckTimeLimit()
                           ? Registry.intValue("ide.dfa.time.limit.online")
                           : Registry.intValue("ide.dfa.time.limit.offline");
      final WorkingTimeMeasurer measurer = new WorkingTimeMeasurer(msLimit * 1000 * 1000);
      int count = 0;
      while (!queue.isEmpty()) {
        for (DfaInstructionState instructionState : queue.getNextInstructionStates(joinInstructions)) {
          if (count++ % 1024 == 0 && measurer.isTimeOver()) {
            LOG.debug("Too complex because the analysis took too long");
            psiBlock.putUserData(TOO_EXPENSIVE_HASH, psiBlock.getText().hashCode());
            return RunnerResult.TOO_COMPLEX;
          }
          ProgressManager.checkCanceled();

          if (LOG.isDebugEnabled()) {
            LOG.debug(instructionState.toString());
          }

          final Instruction instruction = instructionState.getInstruction();

          if (instruction instanceof BranchingInstruction) {
            BranchingInstruction branching = (BranchingInstruction)instruction;
            Collection<DfaMemoryState> processed = processedStates.get(branching);
            if (processed.contains(instructionState.getMemoryState())) {
              continue;
            }
            if (processed.size() > MAX_STATES_PER_BRANCH) {
              LOG.debug("Too complex because too many different possible states");
              return RunnerResult.TOO_COMPLEX; // Too complex :(
            }
            processedStates.putValue(branching, instructionState.getMemoryState().createCopy());
          }

          final DfaInstructionState[] after = acceptInstruction(visitor, instructionState);
          for (DfaInstructionState state : after) {
            Instruction nextInstruction = state.getInstruction();
            if (nextInstruction.getIndex() >= endOffset) {
              continue;
            }
            if (nextInstruction instanceof BranchingInstruction) {
              BranchingInstruction branching = (BranchingInstruction)nextInstruction;
              if (processedStates.get(branching).contains(state.getMemoryState()) ||
                  incomingStates.get(branching).contains(state.getMemoryState())) {
                continue;
              }
              incomingStates.putValue(branching, state.getMemoryState().createCopy());
            }
            queue.offer(state);
          }
        }
      }

      psiBlock.putUserData(TOO_EXPENSIVE_HASH, null);
      LOG.debug("Analysis ok");
      return RunnerResult.OK;
    }
    catch (ArrayIndexOutOfBoundsException e) {
      LOG.error(psiBlock.getText(), e);
      return RunnerResult.ABORTED;
    }
    catch (EmptyStackException e) {
      LOG.error(psiBlock.getText(), e);
      return RunnerResult.ABORTED;
    }
  }

  public final RunnerResult analyzeMethod(@NotNull PsiElement psiBlock, InstructionVisitor visitor) {
    Collection<DfaMemoryState> initialStates = createInitialStates(psiBlock, visitor);
    return initialStates == null ? RunnerResult.NOT_APPLICABLE : analyzeMethod(psiBlock, visitor, initialStates);
  }

  @Nullable
  protected abstract Collection<DfaMemoryState> createInitialStates(@NotNull PsiElement psiBlock, InstructionVisitor visitor);

  @NotNull
  protected abstract DfaMemoryState createMemoryState();

  protected boolean shouldCheckTimeLimit() {
    return !ApplicationManager.getApplication().isUnitTestMode();
  }

  @NotNull
  protected DfaInstructionState[] acceptInstruction(InstructionVisitor visitor, DfaInstructionState instructionState) {
    final Instruction instruction = instructionState.getInstruction();
    final DfaMemoryState state = instructionState.getMemoryState();
    return instruction.accept(state, visitor);
  }

  @NotNull
  public abstract DfaValueFactory getFactory();

  @NotNull
  protected abstract IControlFlowAnalyzer createControlFlowAnalyzer(PsiElement block);

  public final Instruction[] getInstructions() {
    return myInstructions;
  }

  public final Instruction getInstruction(int index) {
    return myInstructions[index];
  }
}
