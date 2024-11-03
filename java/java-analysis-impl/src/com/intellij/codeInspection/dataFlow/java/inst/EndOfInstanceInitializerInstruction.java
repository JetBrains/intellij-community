// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaEndOfInstanceInitializerAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.psi.util.PsiUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EndOfInstanceInitializerInstruction extends Instruction {

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter,
                                      @NotNull DfaMemoryState stateBefore) {
    interpreter.getListener().beforePush(DfaValue.EMPTY_ARRAY, interpreter.getFactory().getUnknown(),
                                         new JavaEndOfInstanceInitializerAnchor(), stateBefore);
    return nextStates(interpreter, stateBefore);
  }

  @Override
  public List<VariableDescriptor> getRequiredDescriptors(@NotNull DfaValueFactory factory) {
    return StreamEx.of(factory.getValues()).select(DfaVariableValue.class)
      .map(DfaVariableValue::getDescriptor)
      .filter(var -> !PsiUtil.isJvmLocalVariable(var.getPsiElement())).toList();
  }

  @Override
  public String toString() {
    return "END_OF_INSTANCE_INITIALIZER";
  }
}
