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
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.psi.PsiExpression;

public abstract class JavaInstructionVisitor extends InstructionVisitor {

  protected final DataFlowRunner myRunner;

  public JavaInstructionVisitor(DataFlowRunner runner) {
    super(runner);
    this.myRunner = runner;
  }

  public DfaInstructionState[] visitAssign(AssignInstruction instruction, DfaMemoryState memState) {
    memState.pop();
    memState.push(memState.pop());
    return nextInstruction(instruction, memState);
  }

  public DfaInstructionState[] visitInstanceof(InstanceofInstruction instruction, DfaMemoryState memState) {
    return visitBinop(instruction, memState);
  }

  public DfaInstructionState[] visitFieldReference(FieldReferenceInstruction instruction, DfaMemoryState memState) {
    memState.pop();
    return nextInstruction(instruction, myRunner, memState);
  }


  public DfaInstructionState[] visitTypeCast(TypeCastInstruction instruction, DfaMemoryState memState) {
    return nextInstruction(instruction, memState);
  }

  public DfaInstructionState[] visitCast(MethodCallInstruction instruction, DfaMemoryState memState) {
    return visitMethodCall(instruction, memState);
  }

  public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction, DfaMemoryState memState) {
    for (PsiExpression ignored : instruction.getArgs()) {
      memState.pop();
    }
    memState.pop(); //qualifier
    memState.push(DfaUnknownValue.getInstance());
    return nextInstruction(instruction, myRunner.getInstructions(), memState);
  }

  public DfaInstructionState[] visitLambdaExpression(LambdaInstruction instruction, DfaMemoryState memState) {
    return nextInstruction(instruction, myRunner.getInstructions(), memState);
  }
}
