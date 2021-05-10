// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class StateQueue {
  private static final int FORCE_MERGE_THRESHOLD = 100;
  private boolean myWasForciblyMerged;
  private final PriorityQueue<DfaInstructionState> myQueue = new PriorityQueue<>();
  private final Map<DfaInstructionState, DfaInstructionState> myMap = new HashMap<>();

  void offer(DfaInstructionState state) {
    DfaInstructionState otherState = myMap.putIfAbsent(state, state);
    if (otherState == null) {
      myQueue.offer(state);
    }
    else if (otherState.getMemoryState() instanceof TrackingDfaMemoryState) {
      ((TrackingDfaMemoryState)otherState.getMemoryState()).afterMerge((TrackingDfaMemoryState)state.getMemoryState());
    }
  }

  boolean isEmpty() {
    return myQueue.isEmpty();
  }

  boolean processAll(@NotNull Processor<? super DfaInstructionState> processor) {
    for (DfaInstructionState state : myQueue) {
      if (!processor.process(state)) return false;
    }
    return true;
  }

  @NotNull
  List<DfaInstructionState> getNextInstructionStates(Set<Instruction> joinInstructions) {
    DfaInstructionState state = myQueue.remove();
    final Instruction instruction = state.getInstruction();
    myMap.remove(state);

    DfaInstructionState next = myQueue.peek();
    if (next == null || next.compareTo(state) != 0) return Collections.singletonList(state);

    List<DfaMemoryStateImpl> memoryStates = new ArrayList<>();
    memoryStates.add((DfaMemoryStateImpl)state.getMemoryState());
    while (!myQueue.isEmpty() && myQueue.peek().compareTo(state) == 0) {
      DfaInstructionState anotherInstructionState = myQueue.poll();
      myMap.remove(anotherInstructionState);
      DfaMemoryState anotherState = anotherInstructionState.getMemoryState();
      memoryStates.add((DfaMemoryStateImpl)anotherState);
    }

    memoryStates = forceMerge(memoryStates);
    if (memoryStates.size() > 1 && joinInstructions.contains(instruction)) {
      squash(memoryStates);
    }

    return ContainerUtil.map(memoryStates, state1 -> new DfaInstructionState(instruction, state1));
  }

  @NotNull
  static List<DfaMemoryStateImpl> squash(List<DfaMemoryStateImpl> states) {
    for (int i = 1; i < states.size(); i++) {
      DfaMemoryStateImpl left = states.get(i);
      if (left == null) continue;
      for (int j = 0; j < i; j++) {
        ProgressManager.checkCanceled();
        DfaMemoryStateImpl right = states.get(j);
        if (right == null) continue;
        DfaMemoryStateImpl result = left.tryJoinExactly(right);
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

  private List<DfaMemoryStateImpl> forceMerge(List<DfaMemoryStateImpl> states) {
    if (states.size() < FORCE_MERGE_THRESHOLD) return states;
    myWasForciblyMerged = true;
    Collection<List<DfaMemoryStateImpl>> groups = StreamEx.of(states).groupingBy(DfaMemoryStateImpl::getMergeabilityKey).values();
    return StreamEx.of(groups)
      .flatMap(group -> StreamEx.ofSubLists(group, 2)
        .map(pair -> {
          if (pair.size() == 2) {
            pair.get(0).merge(pair.get(1));
          }
          return pair.get(0);
        })).distinct().toListAndThen(StateQueue::squash);
  }

  boolean wasForciblyMerged() {
    return myWasForciblyMerged;
  }
}
