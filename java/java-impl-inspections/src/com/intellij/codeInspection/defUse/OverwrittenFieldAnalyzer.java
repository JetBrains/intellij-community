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
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Analyze overwritten fields based on DFA-CFG (unlike usual CFG, it includes method calls, so we can know when field value may leak)
 */
final class OverwrittenFieldAnalyzer extends BaseVariableAnalyzer {
  OverwrittenFieldAnalyzer(ControlFlow flow) {
    super(flow);
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

  @Override
  protected boolean isInterestingInstruction(Instruction instruction) {
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
    boolean hasFieldWrite = StreamEx.of(overwrites).map(AssignInstruction::getAssignedValue)
      .select(DfaVariableValue.class)
      .map(DfaVariableValue::getPsiVariable)
      .anyMatch(f -> f instanceof PsiField);
    if (!hasFieldWrite) return Collections.emptySet();
    Set<AssignInstruction> visited = new HashSet<>();
    boolean ok = runDfa(false, (instruction, beforeVars) -> {
      // beforeVars: IDs of variables that were written but not read yet
      if (beforeVars.isEmpty() && !(instruction instanceof AssignInstruction)) return beforeVars;
      if (instruction instanceof FlushFieldsInstruction) {
        return new BitSet();
      }
      BitSet afterVars = (BitSet)beforeVars.clone();
      boolean skipDependent = false;
      StreamEx<DfaVariableValue> readVariables;
      if (instruction instanceof AssignInstruction) {
        visited.add((AssignInstruction)instruction);
        DfaValue value = ((AssignInstruction)instruction).getAssignedValue();
        if (value instanceof DfaVariableValue) {
          int id = value.getID();
          readVariables = StreamEx.of(((DfaVariableValue)value).getDependentVariables());
          if (!beforeVars.get(id)) {
            overwrites.remove(instruction);
            afterVars.set(id);
          }
        } else {
          readVariables = StreamEx.empty();
        }
        skipDependent = true;
      }
      else if (instruction instanceof MethodCallInstruction) {
        if (!((MethodCallInstruction)instruction).getMutationSignature().isPure()) {
          return new BitSet();
        }
        // We assume that pure methods may read only static fields and fields which are passed as parameters (directly or by qualifier).
        // This might be incorrect in rare cases but allows finding many useful bugs.
        readVariables = StreamEx.of(myFactory.getValues())
          .select(DfaVariableValue.class)
          .filter(value -> value.getPsiVariable() instanceof PsiField &&
                           ((PsiField)value.getPsiVariable()).hasModifierProperty(PsiModifier.STATIC));
      }
      else if (instruction instanceof FinishElementInstruction) {
        readVariables = StreamEx.of(((FinishElementInstruction)instruction).getVarsToFlush());
      }
      else {
        readVariables = getReadVariables(instruction);
      }
      if (instruction instanceof PushInstruction) {
        // Avoid forgetting about qualifier.field on qualifier.field = x;
        DfaAnchor anchor = ((PushInstruction)instruction).getDfaAnchor();
        PsiExpression expression = anchor instanceof JavaExpressionAnchor ? ((JavaExpressionAnchor)anchor).getExpression() : null;
        skipDependent = expression != null && PsiUtil.skipParenthesizedExprUp(expression).getParent() instanceof PsiReferenceExpression
                      && ExpressionUtils.getCallForQualifier(expression) == null;
      }
      if (!skipDependent) {
        readVariables = readVariables.flatMap(v -> StreamEx.of(v.getDependentVariables()).prepend(v)).distinct();
      }
      readVariables.forEach(v -> afterVars.clear(v.getID()));
      return afterVars;
    });
    overwrites.retainAll(visited);
    return ok ? overwrites : Collections.emptySet();
  }
}

