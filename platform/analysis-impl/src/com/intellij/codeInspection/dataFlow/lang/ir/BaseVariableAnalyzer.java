// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.containers.MultiMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public abstract class BaseVariableAnalyzer {
  protected final Instruction[] myInstructions;
  protected final MultiMap<Instruction, Instruction> myForwardMap;
  protected final MultiMap<Instruction, Instruction> myBackwardMap;
  protected final DfaValueFactory myFactory;

  public BaseVariableAnalyzer(ControlFlow flow) {
    myFactory = flow.getFactory();
    myInstructions = flow.getInstructions();
    myForwardMap = calcForwardMap();
    myBackwardMap = calcBackwardMap();
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

  protected abstract boolean isInterestingInstruction(Instruction instruction);

  /**
   * @return true if completed, false if "too complex"
   */
  protected boolean runDfa(boolean forward, BiFunction<? super Instruction, ? super Set<VariableDescriptor>, ? extends Set<VariableDescriptor>> handleState) {
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
