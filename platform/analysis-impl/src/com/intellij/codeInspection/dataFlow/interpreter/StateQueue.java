// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.interpreter;

import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

public class StateQueue {
  private static final int FORCE_MERGE_THRESHOLD = 100;
  private boolean myWasForciblyMerged;
  private final PriorityQueue<DfaInstructionState> myQueue = new PriorityQueue<>();
  private final Map<DfaInstructionState, DfaInstructionState> myMap = new HashMap<>();

  public void offer(DfaInstructionState state) {
    DfaInstructionState otherState = myMap.putIfAbsent(state, state);
    if (otherState == null) {
      myQueue.offer(state);
    }
    else {
      otherState.getMemoryState().afterMerge(state.getMemoryState());
    }
  }

  public boolean isEmpty() {
    return myQueue.isEmpty();
  }

  public boolean processAll(@NotNull Processor<? super DfaInstructionState> processor) {
    for (DfaInstructionState state : myQueue) {
      if (!processor.process(state)) return false;
    }
    return true;
  }

  public @Unmodifiable @NotNull List<DfaInstructionState> getNextInstructionStates(Set<Instruction> joinInstructions) {
    DfaInstructionState state = myQueue.remove();
    final Instruction instruction = state.getInstruction();
    myMap.remove(state);

    DfaInstructionState next = myQueue.peek();
    if (next == null || next.compareTo(state) != 0) return Collections.singletonList(state);

    List<DfaMemoryState> memoryStates = new ArrayList<>();
    memoryStates.add(state.getMemoryState());
    while (!myQueue.isEmpty() && myQueue.peek().compareTo(state) == 0) {
      DfaInstructionState anotherInstructionState = myQueue.poll();
      myMap.remove(anotherInstructionState);
      memoryStates.add(anotherInstructionState.getMemoryState());
    }

    memoryStates = forceMerge(memoryStates);
    if (memoryStates.size() > 1 && joinInstructions.contains(instruction)) {
      squash(memoryStates);
    }

    return ContainerUtil.map(memoryStates, state1 -> new DfaInstructionState(instruction, state1));
  }

  public static @NotNull List<DfaMemoryState> squash(List<DfaMemoryState> states) {
    for (int i = 1; i < states.size(); i++) {
      DfaMemoryState left = states.get(i);
      if (left == null) continue;
      for (int j = 0; j < i; j++) {
        ProgressManager.checkCanceled();
        DfaMemoryState right = states.get(j);
        if (right == null) continue;
        DfaMemoryState result = left.tryJoinExactly(right);
        if (result == left) {
          states.set(j, null);
        } else if (result == right) {
          states.set(i, null);
          break;
        } else if (result != null) {
          states.set(i, null);
          states.set(j, null);
          states.add(result);
          break;
        }
      }
    }
    states.removeIf(Objects::isNull);
    return states;
  }

  private List<DfaMemoryState> forceMerge(List<DfaMemoryState> states) {
    if (states.size() < FORCE_MERGE_THRESHOLD) return states;
    myWasForciblyMerged = true;
    Collection<List<DfaMemoryState>> groups = states.stream().collect(Collectors.groupingBy(DfaMemoryState::getMergeabilityKey)).values();
    List<DfaMemoryState> result = new ArrayList<>();
    Set<DfaMemoryState> seen = new HashSet<>();
    for (List<DfaMemoryState> group : groups) {
      for (int i = 0; i < group.size(); i += 2) {
        DfaMemoryState first = group.get(i);
        if (i + 1 < group.size()) {
          first.merge(group.get(i + 1));
        }
        if (seen.add(first)) {
          result.add(first);
        }
      }
    }
    return squash(result);
  }

  public boolean wasForciblyMerged() {
    return myWasForciblyMerged;
  }
}
