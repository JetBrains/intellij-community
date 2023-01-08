// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.StreamEx;
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
    if (!instruction.getRequiredVariables(myFactory).isEmpty() || !instruction.getWrittenVariables(myFactory).isEmpty()) return true;
    return !instruction.isLinear() || instruction instanceof FinishElementInstruction;
  }

  @Nullable
  private Map<FinishElementInstruction, BitSet> findLiveVars() {
    final Map<FinishElementInstruction, BitSet> result = new HashMap<>();

    boolean ok = runDfa(false, (instruction, liveVars) -> {
      if (instruction instanceof FinishElementInstruction) {
        BitSet set = result.get(instruction);
        if (set != null) {
          set.or(liveVars);
          return (BitSet)set.clone();
        }
        else if (!liveVars.isEmpty()) {
          result.put((FinishElementInstruction)instruction, (BitSet)liveVars.clone());
        }
      }

      List<DfaVariableValue> writtenVariables = instruction.getWrittenVariables(myFactory);
      if (!writtenVariables.isEmpty()) {
        BitSet newVars = (BitSet)liveVars.clone();
        for (DfaVariableValue written : writtenVariables) {
          newVars.clear(written.getID());
          for (DfaVariableValue var : written.getDependentVariables()) {
            newVars.clear(var.getID());
          }
        }
        return newVars;
      } else {
        var processor = new Consumer<DfaVariableValue>() {
          boolean cloned = false;
          BitSet newVars = liveVars;

          @Override
          public void accept(DfaVariableValue value) {
            if (!newVars.get(value.getID())) {
              if (!cloned) {
                newVars = (BitSet)newVars.clone();
                cloned = true;
              }
              newVars.set(value.getID());
            }
          }
        };
        StreamEx.of(instruction.getRequiredVariables(myFactory))
          .flatMap(v -> StreamEx.of(v.getDependentVariables()).prepend(v)).distinct().forEach(processor);
        return processor.newVars;
      }
    });
    return ok ? result : null;
  }

  void flushDeadVariablesOnStatementFinish() {
    final Map<FinishElementInstruction, BitSet> liveVars = findLiveVars();
    if (liveVars == null) return;

    final MultiMap<FinishElementInstruction, DfaVariableValue> toFlush = MultiMap.createSet();

    boolean ok = runDfa(true, (instruction, prevLiveVars) -> {
      if (instruction instanceof FinishElementInstruction) {
        BitSet currentlyLive = liveVars.get(instruction);
        if (currentlyLive == null) {
          currentlyLive = new BitSet();
        }
        int index = 0;
        while (true) {
          int setBit = prevLiveVars.nextSetBit(index);
          if (setBit < 0) break;
          if (!currentlyLive.get(setBit)) {
            toFlush.putValue((FinishElementInstruction)instruction, (DfaVariableValue)myFactory.getValue(setBit));
          }
          index = setBit + 1;
        }
        return currentlyLive;
      }

      return prevLiveVars;
    });

    if (ok) {
      for (FinishElementInstruction instruction : toFlush.keySet()) {
        Collection<DfaVariableValue> values = toFlush.get(instruction);
        values.removeIf(var -> var.getDescriptor().isImplicitReadPossible());
        instruction.getVarsToFlush().addAll(values);
      }
    }
  }
}
