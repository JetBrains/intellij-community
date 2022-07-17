// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.lang.ir.PushInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import org.jetbrains.annotations.NotNull;

/**
 * An instruction that pushes given value to the stack for JVM analysis
 * (it additionally processes escaping)
 */
public class JvmPushInstruction extends PushInstruction {
  private final boolean myReferenceWrite;

  public JvmPushInstruction(@NotNull DfaValue value, DfaAnchor place) {
    this(value, place, false);
  }

  public JvmPushInstruction(@NotNull DfaValue value, DfaAnchor place, final boolean isReferenceWrite) {
    super(value, place);
    assert place == null || !isReferenceWrite;
    myReferenceWrite = isReferenceWrite;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    return new JvmPushInstruction(getValue().bindToFactory(factory), getDfaAnchor(), myReferenceWrite);
  }

  public boolean isReferenceWrite() {
    return myReferenceWrite;
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory, @NotNull DfaMemoryState state, @NotNull DfaValue @NotNull ... arguments) {
    DfaValue value = getValue();
    if (value instanceof DfaVariableValue && JavaDfaHelpers.mayLeakFromType(value.getDfType())) {
      DfaVariableValue qualifier = ((DfaVariableValue)value).getQualifier();
      if (qualifier != null) {
        JavaDfaHelpers.dropLocality(qualifier, state);
      }
    }
    return value;
  }
}
