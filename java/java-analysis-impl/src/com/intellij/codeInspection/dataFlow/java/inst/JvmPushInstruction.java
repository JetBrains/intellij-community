// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.lang.ir.PushInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use simply {@link PushInstruction}. If escaping processing is necessary (which is unlikely),
 * emit explicit {@link EscapeInstruction}.
 */
@Deprecated(forRemoval = true)
public class JvmPushInstruction extends PushInstruction {
  public JvmPushInstruction(@NotNull DfaValue value, DfaAnchor place) {
    this(value, place, false);
  }

  public JvmPushInstruction(@NotNull DfaValue value, DfaAnchor place, final boolean isReferenceWrite) {
    super(value, place);
    assert place == null || !isReferenceWrite;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    return new JvmPushInstruction(getValue().bindToFactory(factory), getDfaAnchor());
  }
}
