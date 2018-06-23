/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

class ReadVariableInstruction extends SimpleInstruction {
  @NotNull 
  public final PsiVariable variable;

  ReadVariableInstruction(@NotNull PsiVariable variable) {
    this.variable = variable;
  }

  public String toString() {
    return "READ " + variable.getName();
  }

  @Override
  public void accept(@NotNull ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitReadVariableInstruction(this, offset, nextOffset);
  }
}
