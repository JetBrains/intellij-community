// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.defUse;

import com.intellij.codeInspection.dataFlow.interpreter.StandardDataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.java.inst.AssignInstruction;
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.DfaListener;
import com.intellij.codeInspection.dataFlow.lang.ir.*;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Analyze overwritten fields based on DFA-CFG (unlike usual CFG, it includes method calls, so we can know when field value may leak)
 */
final class OverwrittenFieldAnalyzer {
  private static PsiElement getAssignedElement(Instruction instruction) {
    DfaValue target = instruction instanceof AssignInstruction assign ? assign.getAssignedValue() :
                      instruction instanceof SimpleAssignmentInstruction simple ? simple.getDestination() :
                      null;
    return target instanceof DfaVariableValue var ? var.getPsiVariable() : null;
  }
  
  static @NotNull Set<DfaAnchor> getOverwrittenFields(@Nullable ControlFlow flow) {
    if (flow == null) return Set.of();
    if (!ContainerUtil.exists(flow.getInstructions(), i -> getAssignedElement(i) instanceof PsiField)) return Set.of();
    DfaValueFactory factory = flow.getFactory();
    FieldAnalysisListener listener = new FieldAnalysisListener(factory);
    new StandardDataFlowInterpreter(flow, listener) {
      @Override
      protected DfaInstructionState @NotNull [] acceptInstruction(@NotNull DfaInstructionState instructionState) {
        Instruction instruction = instructionState.getInstruction();
        if (instruction instanceof ReturnInstruction && !listener.myAnchors.isEmpty()) {
          DfaMemoryState state = instructionState.getMemoryState();
          List<DfaValue> list = factory.getValues().stream().toList();
          for (DfaValue value : list) {
            if (value instanceof DfaVariableValue var) {
              listener.markAsRead(state, var);
            }
          }
        }
        if (instruction instanceof FlushVariableInstruction flushInstruction) {
          listener.markAsRead(instructionState.getMemoryState(), flushInstruction.getVariable());
        }
        return super.acceptInstruction(instructionState);
      }
    }.interpret(new JvmDfaMemoryStateImpl(factory));
    Map<Boolean, Set<DfaAnchor>> map = StreamEx.of(listener.myAnchors)
      .partitioningBy(DfWriteAnchorType::wasRead, Collectors.mapping(DfWriteAnchorType::write, Collectors.toSet()));
    return map.get(false).stream().filter(anchor -> !map.get(true).contains(anchor)).collect(Collectors.toSet());
  }
  
  private sealed interface DfWriteStateType extends DfType {
    @Override
    default DfType correctTypeOnFlush(DfType typeBeforeFlush) {
      return typeBeforeFlush instanceof DfWriteAnchorType anchorType ? anchorType.markAsRead() : DfWriteTopType.INSTANCE;
    }
  }
  
  private enum DfWriteTopType implements DfWriteStateType {
    INSTANCE;
    
    @Override
    public boolean isSuperType(@NotNull DfType other) {
      return other.equals(this) || other == BOTTOM || other instanceof DfWriteAnchorType;
    }

    @Override
    public @NotNull DfType join(@NotNull DfType other) {
      return other instanceof DfWriteStateType ? this : TOP;
    }

    @Override
    public @Nullable DfType tryJoinExactly(@NotNull DfType other) {
      return other instanceof DfWriteStateType ? this : null;
    }

    @Override
    public @NotNull DfType meet(@NotNull DfType other) {
      return other instanceof DfWriteStateType ? other : this;
    }

    @Override
    public @NotNull String toString() {
      return "WTOP";
    }
  }

  private record DfWriteAnchorType(@NotNull DfaAnchor write, boolean wasRead) implements DfWriteStateType {
    @Override
    public @NotNull DfType join(@NotNull DfType other) {
      DfType type = tryJoinExactly(other);
      return type == null ? DfWriteTopType.INSTANCE : type;
    }

    @Override
    public @Nullable DfType tryJoinExactly(@NotNull DfType other) {
      if (this.equals(other) || other instanceof DfWriteTopType) return other;
      if (other instanceof DfWriteAnchorType otherState && write.equals(otherState.write)) {
        return wasRead ? this : other;
      }
      return null;
    }

    DfWriteAnchorType markAsRead() {
      return wasRead ? this : new DfWriteAnchorType(write, true);
    }

    @Override
    public boolean isSuperType(@NotNull DfType other) {
      return other.equals(this) || other == BOTTOM ||
             (wasRead && other instanceof DfWriteAnchorType stateType && write.equals(stateType.write));
    }

    @Override
    public @NotNull DfType meet(@NotNull DfType other) {
      if (this.equals(other) || other instanceof DfWriteTopType) return this;
      if (other instanceof DfWriteAnchorType otherState && write.equals(otherState.write)) {
        return wasRead ? other : this;
      }
      return BOTTOM;
    }

    @Override
    public @NotNull String toString() {
      return "@" + write + (wasRead ? " (wasRead)" : "");
    }
  }
  
  private record EntryPointAnchor() implements DfaAnchor {}

  private record WriteAnchorDescriptor(@NotNull DfaVariableValue var) implements VariableDescriptor {
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
      return DfWriteTopType.INSTANCE;
    }

    @Override
    public @NotNull DfType getInitialDfType(@NotNull DfaVariableValue thisValue, @Nullable PsiElement context) {
      return new DfWriteAnchorType(new EntryPointAnchor(), false);
    }

    @Override
    public String toString() {
      return "writtenAt(" + var + ")";
    }
  }

  private static class FieldAnalysisListener implements DfaListener {
    private final DfaValueFactory myFactory;
    private final Set<DfWriteAnchorType> myAnchors;

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
        markAsRead(state, var);
      }
    }

    @Override
    public void beforeAssignment(@NotNull DfaValue source,
                                 @NotNull DfaValue dest,
                                 @NotNull DfaMemoryState state,
                                 @Nullable DfaAnchor anchor) {
      if (dest instanceof DfaVariableValue var && var.getPsiVariable() instanceof PsiField && anchor != null) {
        DfaVariableValue wnr = myFactory.getVarFactory().createVariableValue(new WriteAnchorDescriptor(var));
        if (state.getDfType(wnr) instanceof DfWriteAnchorType writeStateType) {
          myAnchors.add(writeStateType);
        }
      }
    }

    @Override
    public void afterAssignment(@NotNull DfaValue source,
                                @NotNull DfaValue dest,
                                @NotNull DfaMemoryState state,
                                @Nullable DfaAnchor anchor) {
      if (dest instanceof DfaVariableValue var) {
        List<DfaVariableValue> varsToFlush = StreamEx.of(myFactory.getValues())
          .select(DfaVariableValue.class)
          .filter(v -> v.getDescriptor() instanceof WriteAnchorDescriptor desc && desc.var.dependsOn(var))
          .toList();
        varsToFlush.forEach(state::flushVariable);
        if (var.getPsiVariable() instanceof PsiField && anchor != null) {
          DfaVariableValue wnr = myFactory.getVarFactory().createVariableValue(new WriteAnchorDescriptor(var));
          state.updateDfType(wnr, old -> new DfWriteAnchorType(anchor, false));
        }
      }
    }

    private void markAsRead(@NotNull DfaMemoryState state, @NotNull DfaVariableValue var) {
      List<DfaVariableValue> varsToMark = StreamEx.of(myFactory.getValues())
        .select(DfaVariableValue.class)
        .filter(v -> v.getDescriptor() instanceof WriteAnchorDescriptor desc && desc.var.dependsOn(var))
        .toList();
      varsToMark.forEach(variable -> setAnchorToRead(state, variable));
    }

    private void setAnchorToRead(@NotNull DfaMemoryState state, DfaVariableValue variable) {
      state.updateDfType(variable, t -> {
        if (t instanceof DfWriteAnchorType writeStateType) {
          DfWriteAnchorType read = writeStateType.markAsRead();
          myAnchors.add(read);
          return read;
        }
        return t;
      });
    }
  }
}

