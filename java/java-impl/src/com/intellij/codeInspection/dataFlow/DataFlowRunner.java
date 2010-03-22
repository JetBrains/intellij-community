/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 10:16:39 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.BranchingInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DataFlowRunner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DataFlowRunner");
  private static final long ourTimeLimit = 10000;

  private Instruction[] myInstructions;
  private DfaVariableValue[] myFields;
  private final DfaValueFactory myValueFactory = new DfaValueFactory();

  // Maximum allowed attempts to process instruction. Fail as too complex to process if certain instruction
  // is executed more than this limit times.
  public static final int MAX_STATES_PER_BRANCH = 300;

  public Instruction getInstruction(int index) {
    return myInstructions[index];
  }

  protected DataFlowRunner() {
  }

  public DfaValueFactory getFactory() {
    return myValueFactory;
  }

  @Nullable
  protected Collection<DfaMemoryState> createInitialStates(@NotNull PsiElement psiBlock, InstructionVisitor visitor) {
    if (psiBlock.getParent() instanceof PsiMethod) {
      final PsiClass containingClass = ((PsiMethod)psiBlock.getParent()).getContainingClass();
      if (containingClass instanceof PsiAnonymousClass) {
        final PsiElement newExpression = containingClass.getParent();
        final PsiCodeBlock block = DfaUtil.getTopmostBlockInSameClass(newExpression);
        if (newExpression instanceof PsiNewExpression && block != null) {
          final EnvironmentalInstructionVisitor envVisitor = new EnvironmentalInstructionVisitor(visitor, (PsiNewExpression)newExpression);
          final RunnerResult result = analyzeMethod(block, envVisitor);
          if (result == RunnerResult.OK) {
            final Collection<DfaMemoryState> closureStates = envVisitor.getClosureStates();
            if (!closureStates.isEmpty()) {
              return closureStates;
            }
          }
          return null;
        }
      }
    }


    return Arrays.asList(createMemoryState());
  }

  public final RunnerResult analyzeMethod(@NotNull PsiElement psiBlock, InstructionVisitor visitor) {
    try {
      final Collection<DfaMemoryState> initialStates = createInitialStates(psiBlock, visitor);
      if (initialStates == null) return RunnerResult.NOT_APPLICABLE;

      final ControlFlow flow = createControlFlowAnalyzer().buildControlFlow(psiBlock);
      if (flow == null) return RunnerResult.NOT_APPLICABLE;

      int endOffset = flow.getInstructionCount();
      myInstructions = flow.getInstructions();
      myFields = flow.getFields();

      if (LOG.isDebugEnabled()) {
        for (int i = 0; i < myInstructions.length; i++) {
          Instruction instruction = myInstructions[i];
          LOG.debug(i + ": " + instruction.toString());
        }
      }

      int branchCount = 0;
      for (Instruction instruction : myInstructions) {
        if (instruction instanceof BranchingInstruction) branchCount++;
      }

      if (branchCount > 80) return RunnerResult.TOO_COMPLEX; // Do not even try. Definitely will out of time.

      final ArrayList<DfaInstructionState> queue = new ArrayList<DfaInstructionState>();
      for (final DfaMemoryState initialState : initialStates) {
        queue.add(new DfaInstructionState(myInstructions[0], initialState));
      }

      long timeLimit = ourTimeLimit;
      final boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
      final long before = System.currentTimeMillis();
      int count = 0;
      while (!queue.isEmpty()) {
        if (count % 50 == 0 && !unitTestMode && System.currentTimeMillis() - before > timeLimit) return RunnerResult.TOO_COMPLEX;
        ProgressManager.checkCanceled();

        DfaInstructionState instructionState = queue.remove(0);
        if (LOG.isDebugEnabled()) {
          LOG.debug(instructionState.toString());
        }

        Instruction instruction = instructionState.getInstruction();
        long distance = instructionState.getDistanceFromStart();

        if (instruction instanceof BranchingInstruction) {
          if (!instruction.setMemoryStateProcessed(instructionState.getMemoryState().createCopy())) {
            return RunnerResult.TOO_COMPLEX; // Too complex :(
          }
        }

        DfaInstructionState[] after = instruction.accept(this, instructionState.getMemoryState(), visitor);
        if (after != null) {
          for (DfaInstructionState state : after) {
            Instruction nextInstruction = state.getInstruction();
            if ((!(nextInstruction instanceof BranchingInstruction) || !nextInstruction.isMemoryStateProcessed(state.getMemoryState())) && instruction.getIndex() < endOffset) {
              state.setDistanceFromStart(distance + 1);
              queue.add(state);
            }
          }
        }

        count++;
      }

      return RunnerResult.OK;
    }
    catch (ArrayIndexOutOfBoundsException e) {
      LOG.error(psiBlock.getText(), e); /* TODO[max] !!! hack (of 18186). Please fix in better times. */
      return RunnerResult.ABORTED;
    }
    catch (EmptyStackException e) /* TODO[max] !!! hack (of 18186). Please fix in better times. */ {
      return RunnerResult.ABORTED;
    }
  }

  protected ControlFlowAnalyzer createControlFlowAnalyzer() {
    return new ControlFlowAnalyzer(myValueFactory);
  }

  protected DfaMemoryState createMemoryState() {
    return new DfaMemoryStateImpl(myValueFactory);
  }

  public Instruction[] getInstructions() {
    return myInstructions;
  }

  public DfaVariableValue[] getFields() {
    return myFields;
  }

  public Pair<Set<Instruction>,Set<Instruction>> getConstConditionalExpressions() {
    Set<Instruction> trueSet = new HashSet<Instruction>();
    Set<Instruction> falseSet = new HashSet<Instruction>();

    for (Instruction instruction : myInstructions) {
      if (instruction instanceof BranchingInstruction) {
        BranchingInstruction branchingInstruction = (BranchingInstruction)instruction;
        if (branchingInstruction.getPsiAnchor() != null && branchingInstruction.isConditionConst()) {
          if (!branchingInstruction.isTrueReachable()) {
            falseSet.add(branchingInstruction);
          }

          if (!branchingInstruction.isFalseReachable()) {
            trueSet.add(branchingInstruction);
          }
        }
      }
    }

    for (Instruction instruction : myInstructions) {
      if (instruction instanceof BranchingInstruction) {
        BranchingInstruction branchingInstruction = (BranchingInstruction)instruction;
        if (branchingInstruction.isTrueReachable()) {
          falseSet.remove(branchingInstruction);
        }
        if (branchingInstruction.isFalseReachable()) {
          trueSet.remove(branchingInstruction);
        }
      }
    }

    return Pair.create(trueSet, falseSet);
  }

  private static class EnvironmentalInstructionVisitor extends DelegatingInstructionVisitor {
    private final PsiNewExpression myNewExpression;
    private final Set<DfaMemoryState> myClosureStates = new THashSet<DfaMemoryState>();

    public EnvironmentalInstructionVisitor(@NotNull InstructionVisitor delegate, @NotNull PsiNewExpression newExpression) {
      super(delegate);
      myNewExpression = newExpression;
    }

    @Override
    public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      if (myNewExpression == instruction.getCallExpression()) {
        myClosureStates.add(memState.createCopy());
      }
      return super.visitMethodCall(instruction, runner, memState);
    }

    @NotNull
    public Collection<DfaMemoryState> getClosureStates() {
      return myClosureStates;
    }
  }
}
