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

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.PsiPrefixExpression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.FALSE;
import static com.intellij.codeInspection.dataFlow.types.DfTypes.TRUE;

public class NotInstruction extends ExpressionPushingInstruction {
  public NotInstruction(PsiPrefixExpression anchor) {
    super(new JavaExpressionAnchor(anchor));
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    DfaValue dfaValue = stateBefore.pop();

    DfaMemoryState falseState = stateBefore.createCopy();
    List<DfaInstructionState> result = new ArrayList<>(2);
    if (stateBefore.applyCondition(dfaValue.eq(FALSE))) {
      pushResult(interpreter, stateBefore, TRUE);
      result.add(nextState(interpreter, stateBefore));
    }
    if (falseState.applyCondition(dfaValue.eq(TRUE))) {
      pushResult(interpreter, falseState, FALSE);
      result.add(nextState(interpreter, falseState));
    }

    return result.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  public String toString() {
    return "NOT";
  }
}
