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
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class InstanceofInstruction extends BinopInstruction {
  @Nullable private final PsiExpression myLeft;
  @NotNull private final PsiType myCastType;

  public InstanceofInstruction(PsiElement psiAnchor, @NotNull Project project, @Nullable PsiExpression left, @NotNull PsiType castType) {
    super(JavaTokenType.INSTANCEOF_KEYWORD, psiAnchor, project);
    myLeft = left;
    myCastType = castType;
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

  @NotNull
  public PsiType getCastType() {
    return myCastType;
  }
}
