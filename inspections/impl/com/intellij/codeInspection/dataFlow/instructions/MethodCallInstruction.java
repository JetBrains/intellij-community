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
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
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

  public MethodCallInstruction(@NotNull("!(context instanceof PsiCallExpression) means (implicit) xxxvalue() unboxing method call") 
                               PsiExpression context, DfaValueFactory factory) {
    myContext = context;
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
        myParametersNotNull[i] = isNotNullParameter(params[i]);
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

  private static boolean isNotNullParameter(PsiParameter parameter) {
    final PsiAnnotation[] annotations = parameter.getAnnotations();
    for (PsiAnnotation annotation : annotations) {
      if (AnnotationUtil.NOT_NULL.equals(annotation.getQualifiedName())) return true;
    }
    return false;
  }

  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    for (int i = 0; i < myArgs.length; i++) {
      final DfaValue arg = memState.pop();
      final int revIdx = myArgs.length - i - 1;
      if (myArgs.length <= myParametersNotNull.length && revIdx < myParametersNotNull.length && myParametersNotNull[revIdx] && !memState.applyNotNull(arg)) {
        runner.onPassingNullParameter(myArgs[revIdx]); // Parameters on stack are reverted.
        if (arg instanceof DfaVariableValue) {
          memState.setVarValue((DfaVariableValue)arg, myFactory.getNotNullFactory().create(((DfaVariableValue)arg).getPsiVariable().getType()));
        }
      }
    }

    final @NotNull DfaValue qualifier = memState.pop();
    try {
      if (!memState.applyNotNull(qualifier)) {
        if (myCall == null) {
          runner.onUnboxingNullable(myContext);
        }
        else {
          runner.onInstructionProducesNPE(this);
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

  private void pushResult(DfaMemoryState state, final DfaValue qualifier) {
    final DfaValue dfaValue;
    if (myType != null && (myType instanceof PsiClassType || myType.getArrayDimensions() > 0)) {
      dfaValue = myIsNotNull ? myFactory.getNotNullFactory().create(myType) : myFactory.getTypeFactory().create(myType, myIsNullable);
    }
    else if (myCall == null){
      // unboxing
      dfaValue = qualifier;
    }
    else {
      dfaValue = DfaUnknownValue.getInstance();
    }

    state.push(dfaValue);
  }

  public PsiCallExpression getCallExpression() {
    return myCall;
  }

  public String toString() {
    return myCall == null ? "Unboxing" : "CALL_METHOD: " + myCall.getText();
  }
}
