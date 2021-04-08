// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.instructions.ReturnInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import com.intellij.util.containers.MultiMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import one.util.streamex.IntStreamEx;

import java.util.*;
import java.util.function.BiFunction;

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
    return IntStreamEx.of(LoopAnalyzer.getSuccessorIndices(ins.getIndex(), myInstructions)).elements(myInstructions).toList();
  }

  protected MultiMap<Instruction, Instruction> calcBackwardMap() {
    MultiMap<Instruction, Instruction> result = MultiMap.create();
    for (Instruction instruction : myInstructions) {
      for (Instruction next : myForwardMap.get(instruction)) {
        result.putValue(next, instruction);
      }
    }
    return result;
  }

  protected MultiMap<Instruction, Instruction> calcForwardMap() {
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
  protected boolean runDfa(boolean forward, BiFunction<? super Instruction, ? super BitSet, ? extends BitSet> handleState) {
    Set<Instruction> entryPoints = new HashSet<>();
    if (forward) {
      entryPoints.add(myInstructions[0]);
    }
    else {
      entryPoints.addAll(ContainerUtil.findAll(myInstructions, FilteringIterator.instanceOf(ReturnInstruction.class)));
    }

    Deque<InstructionState> queue = new ArrayDeque<>(10);
    for (Instruction i : entryPoints) {
      queue.addLast(new InstructionState(i, new BitSet()));
    }

    int limit = myForwardMap.size() * 100;
    Map<BitSet, IntSet> processed = new HashMap<>();
    int steps = 0;
    while (!queue.isEmpty()) {
      if (steps > limit) {
        return false;
      }
      if (steps % 1024 == 0) {
        ProgressManager.checkCanceled();
      }
      InstructionState state = queue.removeFirst();
      Instruction instruction = state.first;
      Collection<Instruction> nextInstructions = forward ? myForwardMap.get(instruction) : myBackwardMap.get(instruction);
      BitSet nextVars = handleState.apply(instruction, state.second);
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

  static class InstructionState extends Pair<Instruction, BitSet> {
    InstructionState(Instruction first, BitSet second) {
      super(first, second);
    }
  }
}
