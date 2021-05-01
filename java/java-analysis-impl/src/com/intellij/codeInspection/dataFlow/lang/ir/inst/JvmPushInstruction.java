/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.dataFlow.lang.ir.inst;

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
    var instruction = new JvmPushInstruction(getValue().bindToFactory(factory), getDfaAnchor(), myReferenceWrite);
    instruction.setIndex(getIndex());
    return instruction;
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
