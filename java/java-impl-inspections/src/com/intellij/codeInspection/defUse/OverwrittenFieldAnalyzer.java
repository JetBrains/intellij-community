// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.defUse;

import com.intellij.codeInspection.dataFlow.java.anchor.JavaEndOfInstanceInitializerAnchor;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.java.inst.AssignInstruction;
import com.intellij.codeInspection.dataFlow.java.inst.JvmPushInstruction;
import com.intellij.codeInspection.dataFlow.java.inst.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.ExpressionUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyze overwritten fields based on DFA-CFG (unlike usual CFG, it includes method calls, so we can know when field value may leak)
 */
final class OverwrittenFieldAnalyzer {
  private final Instruction[] myInstructions;
  private final MultiMap<Instruction, Instruction> myForwardMap;
  private final MultiMap<Instruction, Instruction> myBackwardMap;
  private final DfaValueFactory myFactory;

  OverwrittenFieldAnalyzer(ControlFlow flow) {
    myFactory = flow.getFactory();
    myInstructions = flow.getInstructions();
    myForwardMap = calcForwardMap();
    myBackwardMap = calcBackwardMap();
  }

  @NotNull
  private StreamEx<DfaVariableValue> getReadVariables(Instruction instruction) {
    if (instruction instanceof JvmPushInstruction && !((JvmPushInstruction)instruction).isReferenceWrite()) {
      DfaValue value = ((PushInstruction)instruction).getValue();
      if (value instanceof DfaVariableValue) {
        return StreamEx.of((DfaVariableValue)value);
      }
    }
    else if (instruction instanceof PushValueInstruction &&
             ((PushValueInstruction)instruction).getDfaAnchor() instanceof JavaEndOfInstanceInitializerAnchor) {
      return StreamEx.of(myFactory.getValues()).select(DfaVariableValue.class)
        .filter(var -> var.getPsiVariable() instanceof PsiMember);
    }
    return StreamEx.empty();
  }

  private boolean isInterestingInstruction(Instruction instruction) {
    if (instruction == myInstructions[0]) return true;
    if (!instruction.isLinear()) return true;

    if (instruction instanceof AssignInstruction && ((AssignInstruction)instruction).getAssignedValue() instanceof DfaVariableValue ||
        instruction instanceof MethodCallInstruction ||
        instruction instanceof FinishElementInstruction ||
        instruction instanceof FlushFieldsInstruction) {
      return true;
    }
    return getReadVariables(instruction).findFirst().isPresent();
  }

  Set<AssignInstruction> getOverwrittenFields() {
    // Contains instructions where overwrite without read is detected
    // Initially: all assignment instructions; during DFA traverse, some are removed
    Set<AssignInstruction> overwrites = StreamEx.of(myInstructions).select(AssignInstruction.class).toSet();
    if (overwrites.isEmpty()) return Collections.emptySet();
    Set<AssignInstruction> visited = new HashSet<>();
    List<Instruction> entryPoints = StreamEx.of(myInstructions).select(ControlTransferInstruction.class)
      .filter(cti -> cti.getTransfer().getTarget().getPossibleTargets().length == 0)
      .collect(Collectors.toList());

    Deque<InstructionState> queue = new ArrayDeque<>(10);
    for (Instruction i : entryPoints) {
      queue.addLast(new InstructionState(i, new BitSet()));
    }

    int limit = myForwardMap.size() * 100;
    Map<BitSet, IntSet> processed = new HashMap<>();
    int steps = 0;
    while (!queue.isEmpty()) {
      if (steps > limit) {
        return Set.of();
      }
      if (steps % 1024 == 0) {
        ProgressManager.checkCanceled();
      }
      InstructionState state = queue.removeFirst();
      Collection<Instruction> nextInstructions = myBackwardMap.get(state.instruction);
      BitSet nextVars = handleState(state.instruction, state.nextVars, visited, overwrites);
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
    overwrites.retainAll(visited);
    return overwrites;
  }
  
  private BitSet handleState(Instruction instruction, BitSet beforeVars, Set<AssignInstruction> visited, Set<AssignInstruction> overwrites) {
    // beforeVars: IDs of variables that were written but not read yet
    if (beforeVars.isEmpty() && !(instruction instanceof AssignInstruction)) return beforeVars;
    if (instruction instanceof FlushFieldsInstruction) {
      return new BitSet();
    }
    BitSet afterVars = (BitSet)beforeVars.clone();
    boolean skipDependent = false;
    StreamEx<DfaVariableValue> readVariables;
    if (instruction instanceof AssignInstruction assignInstruction) {
      visited.add(assignInstruction);
      DfaValue value = assignInstruction.getAssignedValue();
      if (value instanceof DfaVariableValue var) {
        int id = value.getID();
        readVariables = StreamEx.of(var.getDependentVariables());
        if (!beforeVars.get(id)) {
          overwrites.remove(instruction);
          afterVars.set(id);
        }
      } else {
        readVariables = StreamEx.empty();
      }
      skipDependent = true;
    }
    else if (instruction instanceof MethodCallInstruction callInstruction) {
      if (!callInstruction.getMutationSignature().isPure()) {
        return new BitSet();
      }
      // We assume that pure methods may read only static fields and fields which are passed as parameters (directly or by qualifier).
      // This might be incorrect in rare cases but allows finding many useful bugs.
      readVariables = StreamEx.of(myFactory.getValues())
        .select(DfaVariableValue.class)
        .filter(value -> value.getPsiVariable() instanceof PsiField field &&
                         field.hasModifierProperty(PsiModifier.STATIC));
    }
    else if (instruction instanceof FinishElementInstruction finishElementInstruction) {
      readVariables = StreamEx.of(finishElementInstruction.getVarsToFlush());
    }
    else {
      readVariables = getReadVariables(instruction);
    }
    if (instruction instanceof PushInstruction pushInstruction) {
      // Avoid forgetting about qualifier.field on qualifier.field = x;
      DfaAnchor anchor = pushInstruction.getDfaAnchor();
      PsiExpression expression = anchor instanceof JavaExpressionAnchor ? ((JavaExpressionAnchor)anchor).getExpression() : null;
      skipDependent = expression != null && PsiUtil.skipParenthesizedExprUp(expression).getParent() instanceof PsiReferenceExpression
                    && ExpressionUtils.getCallForQualifier(expression) == null;
    }
    if (!skipDependent) {
      readVariables = readVariables.flatMap(v -> StreamEx.of(v.getDependentVariables()).prepend(v)).distinct();
    }
    readVariables.forEach(v -> afterVars.clear(v.getID()));
    return afterVars;
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

  static @NotNull Set<AssignInstruction> getOverwrittenFields(@Nullable ControlFlow flow) {
    if (flow == null) return Set.of();
    if (!ContainerUtil.exists(flow.getInstructions(), i -> i instanceof AssignInstruction ai &&
                                                           ai.getAssignedValue() instanceof DfaVariableValue var &&
                                                           var.getPsiVariable() instanceof PsiField)) {
      return Set.of();
    }
    return new OverwrittenFieldAnalyzer(flow).getOverwrittenFields();
  }

  private record InstructionState(Instruction instruction, BitSet nextVars) {
  }
}

