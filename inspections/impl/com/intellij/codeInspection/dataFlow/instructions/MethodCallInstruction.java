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
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class MethodCallInstruction extends Instruction {
  @Nullable private final PsiCallExpression myCall;
  private final DfaValueFactory myFactory;
  private boolean myIsNullable;
  private boolean myIsNotNull;
  private final boolean[] myParametersNotNull;
  @Nullable private PsiType myType;
  @NotNull private final PsiExpression[] myArgs;
  private boolean myShouldFlushFields;
  @NotNull private final PsiExpression myContext;
  private final MethodType myMethodType;
  public static enum MethodType {
    BOXING, UNBOXING, REGULAR_METHOD_CALL, CAST
  }

  public MethodCallInstruction(@NotNull PsiCallExpression callExpression, DfaValueFactory factory) {
    this(callExpression, factory, MethodType.REGULAR_METHOD_CALL);
  }

  public MethodCallInstruction(@NotNull PsiExpression context, DfaValueFactory factory, MethodType methodType, PsiType resultType) {
    this(context, factory, methodType);
    myType = resultType;
    myShouldFlushFields = false;
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

  @NotNull
  public PsiExpression[] getArgs() {
    return myArgs;
  }

  public boolean[] getParametersNotNull() {
    return myParametersNotNull;
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
  public void pushResult(DfaMemoryState state, final DfaValue oldValue) {
    DfaValue dfaValue = null;
    if (myType != null && (myType instanceof PsiClassType || myType.getArrayDimensions() > 0)) {
      dfaValue = myIsNotNull ? myFactory.getNotNullFactory().create(myType) : myFactory.getTypeFactory().create(myType, myIsNullable);
    }
    else if (myMethodType == MethodType.UNBOXING) {
      dfaValue = myFactory.getBoxedFactory().createUnboxed(oldValue);
    }
    else if (myMethodType == MethodType.BOXING) {
      dfaValue = myFactory.getBoxedFactory().createBoxed(oldValue);
    }
    else if (myMethodType == MethodType.CAST) {
      if (oldValue instanceof DfaConstValue) {
        final DfaConstValue constValue = (DfaConstValue)oldValue;
        Object o = constValue.getValue();
        if (o instanceof Double || o instanceof Float) {
          double dbVal = o instanceof Double ? ((Double)o).doubleValue() : ((Float)o).doubleValue();
          // 5.0f == 5
          if (Math.floor(dbVal) == dbVal) o = TypeConversionUtil.computeCastTo(o, PsiType.LONG);
        }
        else {
          o = TypeConversionUtil.computeCastTo(o, PsiType.LONG);
        }

        dfaValue = myFactory.getConstFactory().createFromValue(o, myType);
      }
      else {
        dfaValue = oldValue;
      }
    }

    state.push(dfaValue == null ? DfaUnknownValue.getInstance() : dfaValue);
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
