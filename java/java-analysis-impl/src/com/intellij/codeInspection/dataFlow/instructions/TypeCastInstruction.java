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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 9, 2002
 * Time: 10:27:17 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;

public class TypeCastInstruction extends Instruction {
  private final PsiTypeCastExpression myCastExpression;
  private final PsiExpression myCasted;
  private final PsiType myCastTo;

  public TypeCastInstruction(PsiTypeCastExpression castExpression, PsiExpression casted, PsiType castTo) {
    myCastExpression = castExpression;
    myCasted = casted;
    myCastTo = castTo;
  }

  public PsiTypeCastExpression getCastExpression() {
    return myCastExpression;
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
}
