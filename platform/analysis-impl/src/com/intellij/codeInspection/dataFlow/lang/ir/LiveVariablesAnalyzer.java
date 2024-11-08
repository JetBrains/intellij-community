// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

final class LiveVariablesAnalyzer extends BaseVariableAnalyzer {
  LiveVariablesAnalyzer(ControlFlow flow) {
    super(flow);
  }

  @Override
  protected boolean isInterestingInstruction(Instruction instruction) {
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
}
