// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.lang.ir.inst.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiMember;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author peter
 */
final class LiveVariablesAnalyzer extends BaseVariableAnalyzer {
  LiveVariablesAnalyzer(ControlFlow flow) {
    super(flow);
  }

  @Nullable
  private static DfaVariableValue getWrittenVariable(Instruction instruction) {
    if (instruction instanceof AssignInstruction) {
      DfaValue value = ((AssignInstruction)instruction).getAssignedValue();
      return value instanceof DfaVariableValue ? (DfaVariableValue)value : null;
    }
    if (instruction instanceof FlushVariableInstruction) return ((FlushVariableInstruction)instruction).getVariable();
    return null;
  }

  @NotNull
  private StreamEx<DfaVariableValue> getReadVariables(Instruction instruction) {
    if (instruction instanceof PushInstruction) {
      DfaValue value = ((PushInstruction)instruction).getValue();
      if (value instanceof DfaVariableValue) {
        return StreamEx.of((DfaVariableValue)value);
      }
    }
    else if (instruction instanceof EscapeInstruction) {
      return StreamEx.of(((EscapeInstruction)instruction).getEscapedVars());
    }
    else if (instruction instanceof EndOfInitializerInstruction) {
      return StreamEx.of(myFactory.getValues()).select(DfaVariableValue.class)
        .filter(var -> var.getPsiVariable() instanceof PsiMember);
    }
    return StreamEx.empty();
  }

  @Override
  protected boolean isInterestingInstruction(Instruction instruction) {
    if (instruction == myInstructions[0]) return true;
    if (getReadVariables(instruction).findFirst().isPresent() || getWrittenVariable(instruction) != null) return true;
    return instruction instanceof FinishElementInstruction ||
           instruction instanceof GotoInstruction ||
           instruction instanceof ConditionalGotoInstruction ||
           instruction instanceof ControlTransferInstruction ||
           instruction instanceof EnsureInstruction;
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

      DfaVariableValue written = getWrittenVariable(instruction);
      if (written != null) {
        BitSet newVars = (BitSet)liveVars.clone();
        newVars.clear(written.getID());
        for (DfaVariableValue var : written.getDependentVariables()) {
          newVars.clear(var.getID());
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
        getReadVariables(instruction).flatMap(v -> StreamEx.of(v.getDependentVariables()).prepend(v)).distinct().forEach(processor);
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
