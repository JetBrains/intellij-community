/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.psi.PsiExpression;

public class CheckNotNullInstruction extends Instruction {
  private final PsiExpression myExpression;
  private final NullabilityProblem myProblem;

  public CheckNotNullInstruction(PsiExpression expression, NullabilityProblem problem) {
    myExpression = expression;
    myProblem = problem;
  }

  public PsiExpression getExpression() {
    return myExpression;
  }

  public NullabilityProblem getProblem() {
    return myProblem;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitCheckNotNull(this, runner, stateBefore);
  }
}
