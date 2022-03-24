// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.EvalInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Pop array elements from the stack and push the array back
 */
public class FoldArrayInstruction extends EvalInstruction {
  private final DfType myTargetType;

  /**
   * @param anchor   PsiExpression to anchor to
   * @param targetType array type
   * @param elements number of array elements
   */
  public FoldArrayInstruction(@Nullable DfaAnchor anchor,
                              @NotNull DfType targetType,
                              int elements) {
    super(anchor, elements);
    myTargetType = targetType;
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory,
                                @NotNull DfaMemoryState state,
                                @NotNull DfaValue @NotNull ... arguments) {
    for (DfaValue argument : arguments) {
      JavaDfaHelpers.dropLocality(argument, state);
    }
    DfType type = SpecialField.ARRAY_LENGTH.asDfType(myTargetType.meet(DfTypes.NOT_NULL_OBJECT), DfTypes.intValue(arguments.length));
    return factory.fromDfType(type);
  }
}
