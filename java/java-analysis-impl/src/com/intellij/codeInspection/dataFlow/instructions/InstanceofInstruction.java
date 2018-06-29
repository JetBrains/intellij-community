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
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class InstanceofInstruction extends BinopInstruction {
  @Nullable private final PsiExpression myLeft;
  @Nullable private final PsiType myCastType;

  public InstanceofInstruction(PsiElement psiAnchor, @Nullable PsiExpression left, @NotNull PsiType castType) {
    super(JavaTokenType.INSTANCEOF_KEYWORD, psiAnchor, PsiType.BOOLEAN);
    myLeft = left;
    myCastType = castType;
  }

  /**
   * Construct a class object instanceof check (e.g. from Class.isInstance call); castType is not known
   * @param psiAnchor anchor call
   */
  public InstanceofInstruction(PsiMethodCallExpression psiAnchor) {
    super(JavaTokenType.INSTANCEOF_KEYWORD, psiAnchor, PsiType.BOOLEAN);
    myLeft = null;
    myCastType = null;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitInstanceof(this, runner, stateBefore);
  }

  /**
   * @return instanceof operand or null if it's not applicable
   * (e.g. instruction is emitted when inlining Xyz.class::isInstance method reference)
   */
  @Nullable
  public PsiExpression getLeft() {
    return myLeft;
  }

  @Nullable
  public PsiType getCastType() {
    return myCastType;
  }

  /**
   * @return true if this instanceof instruction checks against Class object (e.g. Class.isInstance() call). In this case
   * class object is located on the stack and cast type is not known
   */
  public boolean isClassObjectCheck() {
    return myCastType == null;
  }
}
