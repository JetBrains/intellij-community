// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.interpreter;

import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

  @NotNull
  public List<DfaInstructionState> getNextInstructionStates(Set<Instruction> joinInstructions) {
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

  @NotNull
  public static List<DfaMemoryState> squash(List<DfaMemoryState> states) {
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
    List<DfaMemoryState> list = groups
      .stream()
      .flatMap(group -> ofSubLists(group, 2)
        .map(pair -> {
          if (pair.size() == 2) {
            pair.get(0).merge(pair.get(1));
          }
          return pair.get(0);
        }))
      .distinct()
      .collect(Collectors.toList());

    return squash(list);
  }

  private static <T> Stream<List<T>> ofSubLists(List<T> l, int length) {
    if (l.size() < length) {
      return Stream.of(l);
    }
    return IntStream.range(0, l.size()/length)
      .mapToObj(i->l.subList(i*length, Math.min(l.size(), i*length+length)))  // l[i*length ... i+length+1)
      ;
  }

  public boolean wasForciblyMerged() {
    return myWasForciblyMerged;
  }
}
