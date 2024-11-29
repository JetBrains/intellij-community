// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.containers.MultiMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

final class LiveVariablesAnalyzer {
  private final Instruction[] myInstructions;
  private final MultiMap<Instruction, Instruction> myForwardMap;
  private final MultiMap<Instruction, Instruction> myBackwardMap;
  private final DfaValueFactory myFactory;

  LiveVariablesAnalyzer(ControlFlow flow) {
    this.myFactory = flow.getFactory();
    this.myInstructions = flow.getInstructions();
    this.myForwardMap = calcForwardMap();
    this.myBackwardMap = calcBackwardMap();
  }

  private boolean isInterestingInstruction(Instruction instruction) {
    if (instruction == myInstructions[0]) return true;
    if (!instruction.getRequiredDescriptors(myFactory).isEmpty()) return true;
    return !instruction.isLinear() || instruction instanceof FinishElementInstruction;
  }

  private @Nullable Map<FinishElementInstruction, Set<VariableDescriptor>> findLiveVars() {
    final Map<FinishElementInstruction, Set<VariableDescriptor>> result = new HashMap<>();

    boolean ok = runDfa(false, (instruction, liveVars) -> {
      if (instruction instanceof FinishElementInstruction finishInstruction) {
        Set<VariableDescriptor> set = result.get(instruction);
        if (set != null) {
          set.addAll(liveVars);
          return new HashSet<>(set);
        }
        else if (!liveVars.isEmpty()) {
          result.put(finishInstruction, new HashSet<>(liveVars));
        }
      }

      var processor = new Consumer<VariableDescriptor>() {
        boolean cloned = false;
        Set<VariableDescriptor> newVars = liveVars;

        @Override
        public void accept(VariableDescriptor value) {
          if (!newVars.contains(value)) {
            if (!cloned) {
              newVars = new HashSet<>(newVars);
              cloned = true;
            }
            newVars.add(value);
          }
        }
      };
      instruction.getRequiredDescriptors(myFactory).forEach(processor);
      return processor.newVars;
    });
    return ok ? result : null;
  }

  void flushDeadVariablesOnStatementFinish() {
    final Map<FinishElementInstruction, Set<VariableDescriptor>> liveVars = findLiveVars();
    if (liveVars == null) return;

    final MultiMap<FinishElementInstruction, VariableDescriptor> toFlush = MultiMap.createSet();

    boolean ok = runDfa(true, (instruction, prevLiveVars) -> {
      if (instruction instanceof FinishElementInstruction finishInstruction) {
        Set<VariableDescriptor> currentlyLive = liveVars.get(instruction);
        if (currentlyLive == null) {
          currentlyLive = new HashSet<>();
        }
        for (VariableDescriptor var : prevLiveVars) {
          if (!currentlyLive.contains(var)) {
            toFlush.putValue(finishInstruction, var);
          }
        }
        return currentlyLive;
      }

      return prevLiveVars;
    });

    if (ok) {
      for (FinishElementInstruction instruction : toFlush.keySet()) {
        Collection<VariableDescriptor> values = toFlush.get(instruction);
        values.removeIf(var -> var.isImplicitReadPossible());
        instruction.flushVars(values);
      }
    }
  }

  private List<Instruction> getSuccessors(Instruction ins) {
    return IntStreamEx.of(ins.getSuccessorIndexes()).elements(myInstructions).toList();
  }

  private MultiMap<Instruction, Instruction> calcBackwardMap() {
    MultiMap<Instruction, Instruction> result = MultiMap.create();
    for (Instruction instruction : myInstructions) {
      for (Instruction next : myForwardMap.get(instruction)) {
        result.putValue(next, instruction);
      }
    }
    return result;
  }

  private MultiMap<Instruction, Instruction> calcForwardMap() {
    MultiMap<Instruction, Instruction> result = MultiMap.create();
    for (Instruction instruction : myInstructions) {
      if (isInterestingInstruction(instruction)) {
        for (Instruction next : getSuccessors(instruction)) {
          while (true) {
            if (isInterestingInstruction(next)) {
              result.putValue(instruction, next);
              break;
            }
            if (next.getIndex() + 1 >= myInstructions.length) {
              break;
            }
            next = myInstructions[next.getIndex() + 1];
          }
        }
      }
    }
    return result;
  }

  /**
   * @return true if completed, false if "too complex"
   */
  private boolean runDfa(boolean forward,
                         BiFunction<? super Instruction, ? super Set<VariableDescriptor>, ? extends Set<VariableDescriptor>> handleState) {
    List<Instruction> entryPoints;
    if (forward) {
      entryPoints = List.of(myInstructions[0]);
    }
    else {
      entryPoints = StreamEx.of(myInstructions).select(ControlTransferInstruction.class)
        .filter(cti -> cti.getTransfer().getTarget().getPossibleTargets().length == 0)
        .collect(Collectors.toList());
    }
    
    Deque<InstructionState> queue = new ArrayDeque<>(10);
    for (Instruction i : entryPoints) {
      queue.addLast(new InstructionState(i, new HashSet<>()));
    }

    int limit = myForwardMap.size() * 100;
    Map<Set<VariableDescriptor>, IntSet> processed = new HashMap<>();
    int steps = 0;
    while (!queue.isEmpty()) {
      if (steps > limit) {
        return false;
      }
      if (steps % 1024 == 0) {
        ProgressManager.checkCanceled();
      }
      InstructionState state = queue.removeFirst();
      Instruction instruction = state.instruction;
      Collection<Instruction> nextInstructions = forward ? myForwardMap.get(instruction) : myBackwardMap.get(instruction);
      Set<VariableDescriptor> nextVars = handleState.apply(instruction, state.nextVars);
      for (Instruction next : nextInstructions) {
        IntSet instructionSet = processed.computeIfAbsent(nextVars, k -> new IntOpenHashSet());
        int index = next.getIndex() + 1;
        if (!instructionSet.contains(index)) {
          instructionSet.add(index);
          queue.addLast(new InstructionState(next, nextVars));
          steps++;
        }
      }
    }
    return true;
  }

  private record InstructionState(Instruction instruction, Set<VariableDescriptor> nextVars) {
  }
}
