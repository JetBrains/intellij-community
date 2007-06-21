/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2002
 * Time: 10:48:52 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class MethodCallInstruction extends Instruction {
  @Nullable private final PsiCallExpression myCall;
  private DfaValueFactory myFactory;
  private boolean myIsNullable;
  private boolean myIsNotNull;
  private boolean[] myParametersNotNull;
  private @Nullable PsiType myType;
  private @NotNull PsiExpression[] myArgs;
  private boolean myShouldFlushFields;
  @NotNull private final PsiExpression myContext;
  private final MethodType myMethodType;
  public static enum MethodType {
    BOXING, UNBOXING, REGULAR_METHOD_CALL
  }

  public MethodCallInstruction(@NotNull PsiCallExpression callExpression, DfaValueFactory factory) {
    this(callExpression, factory, MethodType.REGULAR_METHOD_CALL);
  }
  public MethodCallInstruction(@NotNull PsiExpression context, DfaValueFactory factory, MethodType methodType) {
    myContext = context;
    myMethodType = methodType;
    myCall = context instanceof PsiCallExpression ? (PsiCallExpression)context : null;
    myFactory = factory;
    final PsiMethod callee = myCall == null ? null : myCall.resolveMethod();
    final PsiExpressionList argList = myCall == null ? null : myCall.getArgumentList();
    myArgs = argList != null ? argList.getExpressions() : PsiExpression.EMPTY_ARRAY;

    if (callee != null) {
      myIsNullable = AnnotationUtil.isNullable(callee);
      myIsNotNull = AnnotationUtil.isNotNull(callee);
      final PsiParameter[] params = callee.getParameterList().getParameters();
      myParametersNotNull = new boolean[params.length];
      for (int i = 0; i < params.length; i++) {
        myParametersNotNull[i] = AnnotationUtil.isAnnotated(params[i], AnnotationUtil.NOT_NULL, false);
      }
    }
    else {
      myParametersNotNull = ArrayUtil.EMPTY_BOOLEAN_ARRAY;
    }
    myType = myCall == null ? null : myCall.getType();

    myShouldFlushFields = true;
    if (myCall instanceof PsiNewExpression) {
      myIsNullable = false;
      myIsNotNull = true;
      if (myType != null && myType.getArrayDimensions() > 0) {
        myShouldFlushFields = false;
      }
    }
  }

  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    for (int i = 0; i < myArgs.length; i++) {
      final DfaValue arg = memState.pop();
      final int revIdx = myArgs.length - i - 1;
      if (myArgs.length <= myParametersNotNull.length && revIdx < myParametersNotNull.length && myParametersNotNull[revIdx] && !memState.applyNotNull(arg)) {
        onPassingNullParameter(runner, myArgs[revIdx]); // Parameters on stack are reverted.
        if (arg instanceof DfaVariableValue) {
          memState.setVarValue((DfaVariableValue)arg, myFactory.getNotNullFactory().create(((DfaVariableValue)arg).getPsiVariable().getType()));
        }
      }
    }

    final @NotNull DfaValue qualifier = memState.pop();
    try {
      if (!memState.applyNotNull(qualifier)) {
        if (myMethodType == MethodType.UNBOXING) {
          onUnboxingNullable(runner);
        }
        else {
          onInstructionProducesNPE(runner);
        }
        if (qualifier instanceof DfaVariableValue) {
          memState.setVarValue((DfaVariableValue)qualifier, myFactory.getNotNullFactory().create(((DfaVariableValue)qualifier).getPsiVariable().getType()));
        }
      }

      return new DfaInstructionState[]{new DfaInstructionState(runner.getInstruction(getIndex() + 1), memState)};
    }
    finally {
      pushResult(memState, qualifier);
      if (myShouldFlushFields) {
        memState.flushFields(runner);
      }
    }
  }

  protected void onInstructionProducesNPE(final DataFlowRunner runner) {
  }

  protected void onUnboxingNullable(final DataFlowRunner runner) {
  }

  protected void onPassingNullParameter(final DataFlowRunner runner, final PsiExpression expression) {
  }

  private void pushResult(DfaMemoryState state, final DfaValue oldValue) {
    final DfaValue dfaValue;
    if (myType != null && (myType instanceof PsiClassType || myType.getArrayDimensions() > 0)) {
      dfaValue = myIsNotNull ? myFactory.getNotNullFactory().create(myType) : myFactory.getTypeFactory().create(myType, myIsNullable);
    }
    else if (myMethodType == MethodType.UNBOXING) {
      dfaValue = myFactory.getBoxedFactory().createUnboxed(oldValue);
    }
    else if (myMethodType == MethodType.BOXING) {
      dfaValue = myFactory.getBoxedFactory().createBoxed(oldValue);
    }
    else {
      dfaValue = DfaUnknownValue.getInstance();
    }

    state.push(dfaValue);
  }

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
