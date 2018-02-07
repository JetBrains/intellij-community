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

import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import gnu.trove.THashSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class DataFlowRunner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DataFlowRunner");

  private Instruction[] myInstructions;
  private final MultiMap<PsiElement, DfaMemoryState> myNestedClosures = new MultiMap<>();
  @NotNull
  private final DfaValueFactory myValueFactory;
  private boolean myInlining = true;
  private boolean myCancelled = false;
  // Maximum allowed attempts to process instruction. Fail as too complex to process if certain instruction
  // is executed more than this limit times.
  static final int MAX_STATES_PER_BRANCH = 300;

  protected DataFlowRunner() {
    this(false, true);
  }

  protected DataFlowRunner(boolean unknownMembersAreNullable, boolean honorFieldInitializers) {
    myValueFactory = new DfaValueFactory(honorFieldInitializers, unknownMembersAreNullable);
  }

  @NotNull
  public DfaValueFactory getFactory() {
    return myValueFactory;
  }

  /**
   * Call this method from the visitor to cancel analysis (e.g. if wanted fact is already established and subsequent analysis
   * is useless). In this case {@link RunnerResult#CANCELLED} will be returned.
   */
  public void cancel() {
    myCancelled = true;
  }

  @Nullable
  private Collection<DfaMemoryState> createInitialStates(@NotNull PsiElement psiBlock,
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
  @NotNull
  public final RunnerResult analyzeMethod(@NotNull PsiElement psiBlock, @NotNull InstructionVisitor visitor) {
    Collection<DfaMemoryState> initialStates = createInitialStates(psiBlock, visitor, false);
    return initialStates == null ? RunnerResult.NOT_APPLICABLE : analyzeMethod(psiBlock, visitor, false, initialStates);
  }

  /**
   * Analyze this particular method (lambda, class initializer) trying to inline it into outer scope if possible.
   * Usually inlining works, e.g. for lambdas inside stream API calls.
   *
   * @param psiBlock method/lambda/class initializer body
   * @param visitor a visitor to use
   * @return result status
   */
  @NotNull
  public final RunnerResult analyzeMethodWithInlining(@NotNull PsiElement psiBlock, @NotNull InstructionVisitor visitor) {
    Collection<DfaMemoryState> initialStates = createInitialStates(psiBlock, visitor, true);
    if (initialStates == null) {
      return RunnerResult.NOT_APPLICABLE;
    }
    if (initialStates.isEmpty()) {
      return RunnerResult.OK;
    }
    return analyzeMethod(psiBlock, visitor, false, initialStates);
  }

  public final RunnerResult analyzeCodeBlock(@NotNull PsiCodeBlock block,
                                             @NotNull InstructionVisitor visitor,
                                             Consumer<DfaMemoryState> initialStateAdjuster) {
    final DfaMemoryState state = createMemoryState();
    initialStateAdjuster.accept(state);
    return analyzeMethod(block, visitor, false, Collections.singleton(state));
  }

  @NotNull
  final RunnerResult analyzeMethod(@NotNull PsiElement psiBlock,
                                   @NotNull InstructionVisitor visitor,
                                   boolean ignoreAssertions,
                                   @NotNull Collection<? extends DfaMemoryState> initialStates) {
    try {
      final ControlFlow flow = new ControlFlowAnalyzer(myValueFactory, psiBlock, ignoreAssertions, myInlining).buildControlFlow();
      if (flow == null) return RunnerResult.NOT_APPLICABLE;
      int[] loopNumber = LoopAnalyzer.calcInLoop(flow);

      Map<DfaVariableValue, DfaValue> initialValues = StreamEx.of(flow.accessedVariables())
        .mapToEntry(var -> makeInitialValue(var, psiBlock)).nonNullValues().toMap();
      for (DfaMemoryState state : initialStates) {
        initialValues.forEach(state::setVarValue);
      }

      int endOffset = flow.getInstructionCount();
      myInstructions = flow.getInstructions();
      myNestedClosures.clear();

      Set<Instruction> joinInstructions = ContainerUtil.newHashSet();
      for (int index = 0; index < myInstructions.length; index++) {
        Instruction instruction = myInstructions[index];
        if (instruction instanceof GotoInstruction) {
          joinInstructions.add(myInstructions[((GotoInstruction)instruction).getOffset()]);
        } else if (instruction instanceof ConditionalGotoInstruction) {
          joinInstructions.add(myInstructions[((ConditionalGotoInstruction)instruction).getOffset()]);
        } else if (instruction instanceof ControlTransferInstruction) {
          joinInstructions.addAll(((ControlTransferInstruction)instruction).getPossibleTargetInstructions(myInstructions));
        } else if (instruction instanceof MethodCallInstruction && !((MethodCallInstruction)instruction).getContracts().isEmpty()) {
          joinInstructions.add(myInstructions[index + 1]);
        }
      }

      if (LOG.isTraceEnabled()) {
        LOG.trace("Analyzing code block: " + psiBlock.getText());
        for (int i = 0; i < myInstructions.length; i++) {
          LOG.trace(i + ": " + myInstructions[i]);
        }
      }

      final StateQueue queue = new StateQueue();
      for (final DfaMemoryState initialState : initialStates) {
        queue.offer(new DfaInstructionState(myInstructions[0], initialState));
      }

      MultiMap<BranchingInstruction, DfaMemoryState> processedStates = MultiMap.createSet();
      MultiMap<BranchingInstruction, DfaMemoryState> incomingStates = MultiMap.createSet();

      int stateLimit = Registry.intValue("ide.dfa.state.limit");
      int count = 0;
      while (!queue.isEmpty()) {
        List<DfaInstructionState> states = queue.getNextInstructionStates(joinInstructions);
        if (states.size() > MAX_STATES_PER_BRANCH) {
          LOG.trace("Too complex because too many different possible states");
          return RunnerResult.TOO_COMPLEX;
        }
        for (DfaInstructionState instructionState : states) {
          if (count++ > stateLimit) {
            LOG.trace("Too complex data flow: too many instruction states processed");
            return RunnerResult.TOO_COMPLEX;
          }
          ProgressManager.checkCanceled();

          if (LOG.isTraceEnabled()) {
            LOG.trace(instructionState.toString());
          }
          // useful for quick debugging by uncommenting and hot-swapping
          //System.out.println(instructionState.toString());

          Instruction instruction = instructionState.getInstruction();

          if (instruction instanceof BranchingInstruction) {
            BranchingInstruction branching = (BranchingInstruction)instruction;
            Collection<DfaMemoryState> processed = processedStates.get(branching);
            if (containsState(processed, instructionState)) {
              continue;
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
              DfaValue topValue = memoryState.pop();
              if (!(topValue instanceof DfaControlTransferValue || psiBlock instanceof PsiCodeFragment && memoryState.isEmptyStack())) {
                LOG.error("Stack is corrupted at " + instructionState);
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
        if (myCancelled) {
          return RunnerResult.CANCELLED;
        }
      }

      LOG.trace("Analysis ok");
      return RunnerResult.OK;
    }
    catch (ArrayIndexOutOfBoundsException | EmptyStackException e) {
      LOG.error(psiBlock.getText(), e);
      return RunnerResult.ABORTED;
    }
  }

  public RunnerResult analyzeMethodRecursively(PsiElement block, StandardInstructionVisitor visitor) {
    Collection<DfaMemoryState> states = createInitialStates(block, visitor, false);
    if (states == null) return RunnerResult.NOT_APPLICABLE;
    return analyzeBlockRecursively(block, states, visitor);
  }

  private RunnerResult analyzeBlockRecursively(PsiElement block,
                                               Collection<? extends DfaMemoryState> states,
                                               StandardInstructionVisitor visitor) {
    RunnerResult result = analyzeMethod(block, visitor, false, states);
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

  @Nullable
  private static DfaValue makeInitialValue(DfaVariableValue var, PsiElement block) {
    if(var.getQualifier() != null) return null;
    PsiField field = ObjectUtils.tryCast(var.getPsiVariable(), PsiField.class);
    if (field == null || DfaUtil.ignoreInitializer(field) || DfaUtil.hasInitializationHacks(field)) return null;
    return DfaUtil.getPossiblyNonInitializedValue(var.getFactory(), field, block);
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

  private void handleStepOutOfLoop(@NotNull final Instruction prevInstruction,
                                   @NotNull Instruction nextInstruction,
                                   @NotNull final int[] loopNumber,
                                   @NotNull MultiMap<BranchingInstruction, DfaMemoryState> processedStates,
                                   @NotNull MultiMap<BranchingInstruction, DfaMemoryState> incomingStates,
                                   @NotNull List<DfaInstructionState> inFlightStates,
                                   @NotNull DfaInstructionState[] afterStates,
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

    for (Instruction instruction : mayRemoveStatesFor) {
      processedStates.remove((BranchingInstruction)instruction);
      incomingStates.remove((BranchingInstruction)instruction);
    }
  }

  private static boolean inSameLoop(@NotNull Instruction prevInstruction, @NotNull Instruction nextInstruction, @NotNull int[] loopNumber) {
    return loopNumber[nextInstruction.getIndex()] == loopNumber[prevInstruction.getIndex()];
  }

  @NotNull
  protected DfaInstructionState[] acceptInstruction(@NotNull InstructionVisitor visitor, @NotNull DfaInstructionState instructionState) {
    Instruction instruction = instructionState.getInstruction();
    DfaInstructionState[] states = instruction.accept(this, instructionState.getMemoryState(), visitor);

    PsiElement closure = DfaUtil.getClosureInside(instruction);
    if (closure instanceof PsiClass) {
      registerNestedClosures(instructionState, (PsiClass)closure);
    } else if (closure instanceof PsiLambdaExpression) {
      registerNestedClosures(instructionState, (PsiLambdaExpression)closure);
    }

    return states;
  }

  private void registerNestedClosures(@NotNull DfaInstructionState instructionState, @NotNull PsiClass nestedClass) {
    DfaMemoryState state = instructionState.getMemoryState();
    for (PsiMethod method : nestedClass.getMethods()) {
      PsiCodeBlock body = method.getBody();
      if (body != null) {
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

  @NotNull
  protected DfaMemoryState createMemoryState() {
    return new DfaMemoryStateImpl(myValueFactory);
  }

  @NotNull
  public Instruction[] getInstructions() {
    return myInstructions;
  }

  @NotNull
  public Instruction getInstruction(int index) {
    return myInstructions[index];
  }

  public void forNestedClosures(BiConsumer<PsiElement, Collection<? extends DfaMemoryState>> consumer) {
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

  @NotNull
  public Pair<Set<Instruction>,Set<Instruction>> getConstConditionalExpressions() {
    Set<Instruction> trueSet = new HashSet<>();
    Set<Instruction> falseSet = new HashSet<>();

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
}
