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
package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Author: msk
 */
public abstract class ConditionalBranchingInstruction extends BranchingInstruction {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.psi.controlFlow.ConditionalGoToInstruction");
  public final PsiExpression expression;

  ConditionalBranchingInstruction(int offset, @Nullable PsiExpression expression, @NotNull Role role) {
    super(offset, role);
    this.expression = expression;
  }

  @Override
  public int nNext() { return 2; }

  @Override
  public int getNext(int index, int no) {
    switch (no) {
      case 0: return offset;
      case 1: return index + 1;
      default:
        LOG.assertTrue(false);
        return -1;
    }
  }

  @Override
  public void accept(@NotNull ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitConditionalBranchingInstruction(this, offset, nextOffset);
  }
}
