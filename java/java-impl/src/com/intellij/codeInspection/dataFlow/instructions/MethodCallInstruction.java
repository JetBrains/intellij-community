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
 * Date: Jan 26, 2002
 * Time: 10:48:52 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class MethodCallInstruction extends Instruction {
  @Nullable private final PsiCallExpression myCall;
  @Nullable private final PsiType myType;
  @NotNull private final PsiExpression[] myArgs;
  private final boolean myShouldFlushFields;
  @NotNull private final PsiExpression myContext;
  private final MethodType myMethodType;
  @Nullable private final DfaValue myPrecalculatedReturnValue;
  public enum MethodType {
    BOXING, UNBOXING, REGULAR_METHOD_CALL, CAST
  }

  public MethodCallInstruction(@NotNull PsiExpression context, MethodType methodType, @Nullable PsiType resultType) {
    myContext = context;
    myMethodType = methodType;
    myCall = null;
    myArgs = PsiExpression.EMPTY_ARRAY;
    myType = resultType;
    myShouldFlushFields = false;
    myPrecalculatedReturnValue = null;
  }

  public MethodCallInstruction(@NotNull PsiCallExpression context, @Nullable DfaValue precalculatedReturnValue) {
    myContext = context;
    myMethodType = MethodType.REGULAR_METHOD_CALL;
    myCall = context;
    final PsiExpressionList argList = context.getArgumentList();
    myArgs = argList != null ? argList.getExpressions() : PsiExpression.EMPTY_ARRAY;
    myType = myCall.getType();
    myShouldFlushFields = !(myCall instanceof PsiNewExpression && myType != null && myType.getArrayDimensions() > 0);
    myPrecalculatedReturnValue = precalculatedReturnValue;
  }

  @Nullable
  public PsiType getResultType() {
    return myType;
  }

  @NotNull
  public PsiExpression[] getArgs() {
    return myArgs;
  }

  public MethodType getMethodType() {
    return myMethodType;
  }

  public boolean shouldFlushFields() {
    return myShouldFlushFields;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitMethodCall(this, runner, stateBefore);
  }

  @Nullable
  public PsiCallExpression getCallExpression() {
    return myCall;
  }

  @NotNull
  public PsiExpression getContext() {
    return myContext;
  }

  @Nullable
  public DfaValue getPrecalculatedReturnValue() {
    return myPrecalculatedReturnValue;
  }

  public String toString() {
    return myMethodType == MethodType.UNBOXING
           ? "UNBOX"
           : myMethodType == MethodType.BOXING
             ? "BOX" :
             "CALL_METHOD: " + (myCall == null ? "null" : myCall.getText());
  }
}
