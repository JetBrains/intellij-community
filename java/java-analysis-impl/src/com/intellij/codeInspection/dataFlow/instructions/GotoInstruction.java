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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2002
 * Time: 10:48:19 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.ControlFlow;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class GotoInstruction extends Instruction {

  @Nullable
  private ControlFlow.ControlFlowOffset myOffset;

  public GotoInstruction(@Nullable ControlFlow.ControlFlowOffset myOffset) {
    this.myOffset = myOffset;
  }

  public int getOffset() {
    return myOffset == null ? -1 : myOffset.getInstructionOffset();
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DfaMemoryState stateBefore, @NotNull InstructionVisitor visitor) {
    return visitor.visitGoto(this, stateBefore);
  }

  public String toString() {
    return "GOTO: " + getOffset();
  }

  public void setOffset(final int offset) {
    assert myOffset == null : "Offset already set";
    myOffset = new ControlFlow.ControlFlowOffset() {
      @Override
      public int getInstructionOffset() {
        return offset;
      }
    };
  }

  public void setOffset(ControlFlow.ControlFlowOffset offset) {
    assert myOffset == null : "Offset already set";
    myOffset = offset;
  }
}
