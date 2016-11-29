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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 9:40:01 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DfaInstructionState implements Comparable<DfaInstructionState> {
  public static final DfaInstructionState[] EMPTY_ARRAY = new DfaInstructionState[0];
  private final DfaMemoryState myBeforeMemoryState;
  private final Instruction myInstruction;

  public DfaInstructionState(@NotNull Instruction myInstruction, @NotNull DfaMemoryState myBeforeMemoryState) {
    this.myBeforeMemoryState = myBeforeMemoryState;
    this.myInstruction = myInstruction;
  }

  @NotNull
  public Instruction getInstruction() {
    return myInstruction;
  }

  @NotNull
  public DfaMemoryState getMemoryState() {
    return myBeforeMemoryState;
  }

  public String toString() {
    return getInstruction().getIndex() + " " + getInstruction() + ":   " + getMemoryState().toString();
  }

  @Override
  public int compareTo(@NotNull DfaInstructionState o) {
    return myInstruction.getIndex() - o.myInstruction.getIndex();
  }
}

class StateQueue {
  private final PriorityQueue<DfaInstructionState> myQueue = new PriorityQueue<>();
  private final Set<Pair<Instruction, DfaMemoryState>> mySet = ContainerUtil.newHashSet();
  
  void offer(DfaInstructionState state) {
    if (mySet.add(Pair.create(state.getInstruction(), state.getMemoryState()))) {
      myQueue.offer(state);
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
    DfaInstructionState state = myQueue.poll();
    final Instruction instruction = state.getInstruction();
    mySet.remove(Pair.create(instruction, state.getMemoryState()));

    DfaInstructionState next = myQueue.peek();
    if (next == null || next.compareTo(state) != 0) return Collections.singletonList(state);

    List<DfaMemoryStateImpl> memoryStates = ContainerUtil.newArrayList();
    memoryStates.add((DfaMemoryStateImpl)state.getMemoryState());
    while (!myQueue.isEmpty() && myQueue.peek().compareTo(state) == 0) {
      DfaMemoryState anotherState = myQueue.poll().getMemoryState();
      mySet.remove(Pair.create(instruction, anotherState));
      memoryStates.add((DfaMemoryStateImpl)anotherState);
    }

    if (memoryStates.size() > 1 && joinInstructions.contains(instruction)) {
      MultiMap<Object, DfaMemoryStateImpl> groups = MultiMap.create();
      for (DfaMemoryStateImpl memoryState : memoryStates) {
        groups.putValue(memoryState.getSuperficialKey(), memoryState);
      }

      memoryStates = ContainerUtil.newArrayList();
      for (Map.Entry<Object, Collection<DfaMemoryStateImpl>> entry : groups.entrySet()) {
        memoryStates.addAll(mergeGroup((List<DfaMemoryStateImpl>)entry.getValue()));
      }
      
    }

    return ContainerUtil.map(memoryStates, state1 -> new DfaInstructionState(instruction, state1));
  }                                                                      

  private static List<DfaMemoryStateImpl> mergeGroup(List<DfaMemoryStateImpl> group) {
    if (group.size() < 2) {
      return group;
    }

    StateMerger merger = new StateMerger();
    while (true) {
      List<DfaMemoryStateImpl> nextStates = merger.mergeByFacts(group);
      if (nextStates == null) nextStates = merger.mergeByNullability(group);
      if (nextStates == null) nextStates = merger.mergeByUnknowns(group);
      if (nextStates == null) break;
      group = nextStates;
    }
    return group;
  }
}