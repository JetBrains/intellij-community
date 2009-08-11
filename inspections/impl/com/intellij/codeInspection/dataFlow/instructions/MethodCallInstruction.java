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
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class MethodCallInstruction extends Instruction {
  @Nullable private final PsiCallExpression myCall;
  @Nullable private PsiType myType;
  @NotNull private final PsiExpression[] myArgs;
  private boolean myShouldFlushFields;
  @NotNull private final PsiExpression myContext;
  private final MethodType myMethodType;
  public static enum MethodType {
    BOXING, UNBOXING, REGULAR_METHOD_CALL, CAST
  }

  public MethodCallInstruction(@NotNull PsiCallExpression callExpression) {
    this(callExpression, MethodType.REGULAR_METHOD_CALL);
  }

  public MethodCallInstruction(@NotNull PsiExpression context, MethodType methodType, PsiType resultType) {
    this(context, methodType);
    myType = resultType;
    myShouldFlushFields = false;
  }

  public MethodCallInstruction(@NotNull PsiExpression context, MethodType methodType) {
    myContext = context;
    myMethodType = methodType;
    myCall = context instanceof PsiCallExpression ? (PsiCallExpression)context : null;
    final PsiExpressionList argList = myCall == null ? null : myCall.getArgumentList();
    myArgs = argList != null ? argList.getExpressions() : PsiExpression.EMPTY_ARRAY;

    myType = myCall == null ? null : myCall.getType();

    myShouldFlushFields = true;
    if (myCall instanceof PsiNewExpression && myType != null && myType.getArrayDimensions() > 0) {
      myShouldFlushFields = false;
    }
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

  public String toString() {
    return myMethodType == MethodType.UNBOXING ? "UNBOX" : myMethodType == MethodType.BOXING ? "BOX" : "CALL_METHOD: " + myCall.getText();
  }
}
