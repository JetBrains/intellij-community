// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.defUse;

import com.intellij.codeInspection.dataFlow.interpreter.StandardDataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.java.inst.AssignInstruction;
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.DfaListener;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ReturnInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Analyze overwritten fields based on DFA-CFG (unlike usual CFG, it includes method calls, so we can know when field value may leak)
 */
final class OverwrittenFieldAnalyzer {
  static @NotNull Set<DfaAnchor> getOverwrittenFields(@Nullable ControlFlow flow) {
    if (flow == null) return Set.of();
    if (!ContainerUtil.exists(flow.getInstructions(), i -> i instanceof AssignInstruction ai &&
                                                           ai.getAssignedValue() instanceof DfaVariableValue var &&
                                                           var.getPsiVariable() instanceof PsiField)) {
      return Set.of();
    }
    DfaValueFactory factory = flow.getFactory();
    FieldAnalysisListener listener = new FieldAnalysisListener(factory);
    new StandardDataFlowInterpreter(flow, listener) {
      @Override
      protected DfaInstructionState @NotNull [] acceptInstruction(@NotNull DfaInstructionState instructionState) {
        if (instructionState.getInstruction() instanceof ReturnInstruction && !listener.myAnchors.isEmpty()) {
          DfaMemoryState state = instructionState.getMemoryState();
          for (DfaValue value : factory.getValues()) {
            if (value instanceof DfaVariableValue var && var.getDescriptor() == WriteNotReadDescriptor.INSTANCE) {
              DfaAnchor anchor = state.getDfType(var).getConstantOfType(DfaAnchor.class);
              if (anchor != null) {
                listener.myAnchors.remove(anchor);
                if (listener.myAnchors.isEmpty()) break;
              }
            }
          }
        }
        return super.acceptInstruction(instructionState);
      }
    }.interpret(new JvmDfaMemoryStateImpl(factory));
    return listener.myAnchors;
  }

  private static class DfAnchorConstantType extends DfConstantType<DfaAnchor> {
    protected DfAnchorConstantType(DfaAnchor value) {
      super(value);
    }

    @Override
    public @NotNull DfType join(@NotNull DfType other) {
      return this.equals(other) ? this : TOP;
    }

    @Override
    public @Nullable DfType tryJoinExactly(@NotNull DfType other) {
      return this.equals(other) ? this : null;
    }
  }

  private static class WriteNotReadDescriptor implements VariableDescriptor {
    private static final WriteNotReadDescriptor INSTANCE = new WriteNotReadDescriptor();

    @Override
    public boolean isStable() {
      return false;
    }

    @Override
    public boolean isCall() {
      return true;
    }

    @Override
    public @NotNull DfType getDfType(@Nullable DfaVariableValue qualifier) {
      return DfType.TOP;
    }

    @Override
    public String toString() {
      return "writtenAt";
    }
  }

  private static class FieldAnalysisListener implements DfaListener {
    private final DfaValueFactory myFactory;
    private final Set<DfaAnchor> myAnchors;

    private FieldAnalysisListener(DfaValueFactory factory) {
      myFactory = factory;
      myAnchors = new HashSet<>();
    }

    @Override
    public void beforePush(@NotNull DfaValue @NotNull [] args,
                           @NotNull DfaValue value,
                           @NotNull DfaAnchor anchor,
                           @NotNull DfaMemoryState state) {
      if (value instanceof DfaVariableValue var && var.getPsiVariable() instanceof PsiField) {
        if (anchor instanceof JavaExpressionAnchor exprAnchor &&
            (ExpressionUtils.isVoidContext(exprAnchor.getExpression()) ||
             exprAnchor.getExpression() instanceof PsiAssignmentExpression ||
             PsiUtil.isIncrementDecrementOperation(exprAnchor.getExpression()))) {
          return;
        }
        DfaVariableValue wnr = myFactory.getVarFactory().createVariableValue(WriteNotReadDescriptor.INSTANCE, var);
        state.flushVariable(wnr);
      }
    }

    @Override
    public void beforeAssignment(@NotNull DfaValue source,
                                 @NotNull DfaValue dest,
                                 @NotNull DfaMemoryState state,
                                 @Nullable DfaAnchor anchor) {
      if (dest instanceof DfaVariableValue var && var.getPsiVariable() instanceof PsiField && anchor != null) {
        DfaVariableValue wnr = myFactory.getVarFactory().createVariableValue(WriteNotReadDescriptor.INSTANCE, var);
        DfaAnchor oldAnchor = state.getDfType(wnr).getConstantOfType(DfaAnchor.class);
        if (oldAnchor != null) {
          myAnchors.add(oldAnchor);
        }
      }
    }

    @Override
    public void afterAssignment(@NotNull DfaValue source,
                                 @NotNull DfaValue dest,
                                 @NotNull DfaMemoryState state,
                                 @Nullable DfaAnchor anchor) {
      if (dest instanceof DfaVariableValue var && var.getPsiVariable() instanceof PsiField && anchor != null) {
        DfaVariableValue wnr = myFactory.getVarFactory().createVariableValue(WriteNotReadDescriptor.INSTANCE, var);
        state.flushVariable(wnr);
        state.meetDfType(wnr, new DfAnchorConstantType(anchor));
      }
    }
  }
}

