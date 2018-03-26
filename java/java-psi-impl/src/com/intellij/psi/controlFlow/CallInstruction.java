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

import org.jetbrains.annotations.NotNull;


public class CallInstruction extends GoToInstruction {
  public final ControlFlowStack stack;
  int procBegin;
  int procEnd;

  public CallInstruction(int procBegin, int procEnd, @NotNull ControlFlowStack stack) {
    super(procBegin);
    this.stack = stack;
    this.procBegin = procBegin;
    this.procEnd = procEnd;
  }

  public String toString() {
    return "CALL " + offset ;
  }

  public void execute(int returnOffset) {
    synchronized (stack) {
      stack.push(returnOffset, this);
    }
  }

  @Override
  public void accept(@NotNull ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitCallInstruction(this, offset, nextOffset);
  }
}
