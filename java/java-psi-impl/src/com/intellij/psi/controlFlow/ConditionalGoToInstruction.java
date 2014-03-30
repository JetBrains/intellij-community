/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NonNls;


public class ConditionalGoToInstruction extends ConditionalBranchingInstruction {
  public ConditionalGoToInstruction(int offset, final PsiExpression expression) {
    this(offset, Role.END, expression);
  }
  public ConditionalGoToInstruction(int offset, Role role, final PsiExpression expression) {
    super(offset, expression, role);
  }

  public String toString() {
    @NonNls final String sRole = "["+role.toString()+"]";
    return "COND_GOTO " + sRole + " " + offset;
  }

  @Override
  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitConditionalGoToInstruction(this, offset, nextOffset);
  }
}
