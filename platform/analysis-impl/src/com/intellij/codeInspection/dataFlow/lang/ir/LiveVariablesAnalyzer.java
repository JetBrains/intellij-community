// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class LiveVariablesAnalyzer {
  private final Instruction[] myInstructions;
  private final List<List<Instruction>> myForwardMap;
  private final List<List<Instruction>> myBackwardMap;
  private final DfaValueFactory myFactory;
  private final Object2IntMap<VariableDescriptor> myDescriptorNumbering = new Object2IntOpenHashMap<>();
  private final List<VariableDescriptor> myDescriptors = new ArrayList<>();

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

  private int getDescriptorNumber(@NotNull VariableDescriptor descriptor) {
    int num = myDescriptorNumbering.getInt(descriptor);
    if (num == 0) {
      myDescriptors.add(descriptor);
      num = myDescriptors.size();
      myDescriptorNumbering.put(descriptor, num);
    }
    return num - 1;
  }

  private @Nullable Map<FinishElementInstruction, ProcessedState> findLiveVars() {
    final Map<FinishElementInstruction, ProcessedState> result = new HashMap<>();

    boolean ok = runDfa(false, (instruction, liveVars) -> {
      if (instruction instanceof FinishElementInstruction finishInstruction) {
        ProcessedState set = result.get(instruction);
        if (set != null) {
          set.addAll(liveVars);
          return new ProcessedState(set);
        }
        else if (!liveVars.isEmpty()) {
          result.put(finishInstruction, liveVars);
        }
      }

      var processor = new Consumer<VariableDescriptor>() {
        boolean cloned = false;
        ProcessedState newVars = liveVars;

        @Override
        public void accept(VariableDescriptor value) {
          ProcessedState newState = newVars.add(value, !cloned);
          cloned |= newState != newVars;
          newVars = newState;
        }
      };
      instruction.getRequiredDescriptors(myFactory).forEach(processor);
      return processor.newVars;
    });
    return ok ? result : null;
  }

  void flushDeadVariablesOnStatementFinish() {
    final Map<FinishElementInstruction, ProcessedState> liveVars = findLiveVars();
    if (liveVars == null) return;

    final Map<FinishElementInstruction, BitSet> toFlush = new IdentityHashMap<>();

    boolean ok = runDfa(true, (instruction, prevLiveVars) -> {
      if (instruction instanceof FinishElementInstruction finishInstruction) {
        ProcessedState currentlyLive = liveVars.get(instruction);
        BitSet varsToFlush = (BitSet)prevLiveVars.processedVars.clone();
        if (currentlyLive != null) {
          varsToFlush.andNot(currentlyLive.processedVars);
        }
        toFlush.compute(finishInstruction, (k, set) -> {
          if (set == null) return varsToFlush;
          set.or(varsToFlush);
          return set;
        });
        return currentlyLive == null ? new ProcessedState() : currentlyLive;
      }

      return prevLiveVars;
    });

    if (ok) {
      toFlush.forEach((instruction, set) -> {
        List<VariableDescriptor> descriptors = set.stream().mapToObj(myDescriptors::get)
          .filter(var -> !var.isImplicitReadPossible()).toList();
        instruction.flushVars(descriptors);
      });
    }
  }

  private List<List<Instruction>> calcBackwardMap() {
    List<List<Instruction>> result = Stream.<List<Instruction>>generate(() -> new ArrayList<>()).limit(myInstructions.length).toList();
    for (Instruction instruction : myInstructions) {
      List<Instruction> list = myForwardMap.get(instruction.getIndex());
      for (Instruction next : list) {
        result.get(next.getIndex()).add(instruction);
      }
    }
    return result;
  }

  private List<List<Instruction>> calcForwardMap() {
    List<List<Instruction>> result = new ArrayList<>();
    for (Instruction instruction : myInstructions) {
      if (isInterestingInstruction(instruction)) {
        List<Instruction> successors = new ArrayList<>();
        for (int index : instruction.getSuccessorIndexes()) {
          Instruction next = myInstructions[index];
          while (true) {
            if (isInterestingInstruction(next)) {
              successors.add(next);
              break;
            }
            if (next.getIndex() + 1 >= myInstructions.length) {
              break;
            }
            next = myInstructions[next.getIndex() + 1];
          }
        }
        result.add(successors);
      } else {
        result.add(List.of());
      }
    }
    return result;
  }

  /**
   * @return true if completed, false if "too complex"
   */
  private boolean runDfa(boolean forward, BiFunction<? super Instruction, ProcessedState, ProcessedState> handleState) {
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
      queue.addLast(new InstructionState(i, new ProcessedState()));
    }

    int limit = myForwardMap.size() * 50;
    Map<ProcessedState, IntSet> processed = new HashMap<>();
    int steps = 0;
    while (!queue.isEmpty()) {
      if (steps > limit) {
        return false;
      }
      if (steps % 8192 == 0) {
        ProgressManager.checkCanceled();
      }
      InstructionState state = queue.removeFirst();
      Instruction instruction = state.instruction;
      Collection<Instruction> nextInstructions = forward ? 
                                                 myForwardMap.get(instruction.getIndex()) : 
                                                 myBackwardMap.get(instruction.getIndex());
      ProcessedState nextVars = handleState.apply(instruction, state.nextVars);
      if (nextInstructions != null) {
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
    }
    return true;
  }

  private final class ProcessedState {
    private final @NotNull BitSet processedVars;

    ProcessedState() {
      this.processedVars = new BitSet();
    }
    
    ProcessedState(ProcessedState state) {
      this.processedVars = (BitSet)state.processedVars.clone();
    }
    
    boolean isEmpty() { 
      return processedVars.isEmpty(); 
    }

    ProcessedState add(VariableDescriptor descriptor, boolean clone) {
      int number = getDescriptorNumber(descriptor);
      if (!processedVars.get(number)) {
        ProcessedState newState = clone ? new ProcessedState(this) : this;
        newState.processedVars.set(number);
        return newState;
      }
      return this;
    }

    void addAll(ProcessedState vars) {
      processedVars.or(vars.processedVars);
    }

    public void forEach(IntConsumer consumer) {
      BitSet vars = processedVars;
      for (int num = vars.nextSetBit(0); num >= 0; num = vars.nextSetBit(num + 1)) {
        consumer.accept(num);
      }
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (obj == null || obj.getClass() != this.getClass()) return false;
      var that = (ProcessedState)obj;
      return this.processedVars.equals(that.processedVars);
    }

    @Override
    public int hashCode() {
      return this.processedVars.hashCode();
    }
  }

  private record InstructionState(Instruction instruction, ProcessedState nextVars) {
  }
}
