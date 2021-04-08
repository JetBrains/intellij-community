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

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TypeCastInstruction extends ExpressionPushingInstruction<PsiTypeCastExpression> {
  private final PsiExpression myCasted;
  private final PsiType myCastTo;
  private final @Nullable DfaControlTransferValue myTransferValue;

  public TypeCastInstruction(PsiTypeCastExpression castExpression,
                             PsiExpression casted,
                             PsiType castTo,
                             @Nullable DfaControlTransferValue value) {
    super(castExpression);
    assert !(castTo instanceof PsiPrimitiveType);
    myCasted = casted;
    myCastTo = castTo;
    myTransferValue = value;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    if (myTransferValue == null) return this;
    var instruction = new TypeCastInstruction(getExpression(), myCasted, myCastTo,
                                              myTransferValue.bindToFactory(factory));
    instruction.setIndex(getIndex());
    return instruction;
  }

  @Nullable
  public DfaControlTransferValue getCastExceptionTransfer() {
    return myTransferValue;
  }

  public PsiExpression getCasted() {
    return myCasted;
  }

  public PsiType getCastTo() {
    return myCastTo;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitTypeCast(this, runner, stateBefore);
  }

  @Override
  public String toString() {
    return "CAST_TO "+myCastTo.getCanonicalText();
  }
}
