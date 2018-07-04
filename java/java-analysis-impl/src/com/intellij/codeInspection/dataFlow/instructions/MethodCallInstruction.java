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

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class MethodCallInstruction extends Instruction {
  private static final Nullability[] EMPTY_NULLABILITY_ARRAY = new Nullability[0];

  @Nullable private final PsiType myType;
  private final int myArgCount;
  private final boolean myShouldFlushFields;
  @NotNull private final PsiElement myContext;
  @Nullable private final PsiMethod myTargetMethod;
  private final List<MethodContract> myContracts;
  private final MethodType myMethodType;
  @Nullable private final DfaValue myPrecalculatedReturnValue;
  private final boolean myVarArgCall;
  private final Nullability[] myArgRequiredNullability;
  private final Nullability myReturnNullability;

  public enum MethodType {
    BOXING, UNBOXING, REGULAR_METHOD_CALL, METHOD_REFERENCE_CALL, CAST
  }

  public MethodCallInstruction(@NotNull PsiExpression context, MethodType methodType, @Nullable PsiType resultType) {
    myContext = context;
    myContracts = Collections.emptyList();
    myMethodType = methodType;
    myArgCount = 0;
    myType = resultType;
    myShouldFlushFields = false;
    myPrecalculatedReturnValue = null;
    myTargetMethod = null;
    myVarArgCall = false;
    myArgRequiredNullability = EMPTY_NULLABILITY_ARRAY;
    myReturnNullability = Nullability.UNKNOWN;
  }

  public MethodCallInstruction(@NotNull PsiMethodReferenceExpression reference, @NotNull List<? extends MethodContract> contracts) {
    myContext = reference;
    myMethodType = MethodType.METHOD_REFERENCE_CALL;
    JavaResolveResult resolveResult = reference.advancedResolve(false);
    myTargetMethod = ObjectUtils.tryCast(resolveResult.getElement(), PsiMethod.class);
    myContracts = Collections.unmodifiableList(contracts);
    myArgCount = myTargetMethod == null ? 0 : myTargetMethod.getParameterList().getParametersCount();
    if (myTargetMethod == null) {
      myType = null;
      myReturnNullability = Nullability.UNKNOWN;
    }
    else {
      if (myTargetMethod.isConstructor()) {
        PsiClass containingClass = myTargetMethod.getContainingClass();
        myType = containingClass == null ? null : JavaPsiFacade.getElementFactory(myTargetMethod.getProject())
          .createType(containingClass, resolveResult.getSubstitutor());
        myReturnNullability = Nullability.NOT_NULL;
      }
      else {
        myType = resolveResult.getSubstitutor().substitute(myTargetMethod.getReturnType());
        myReturnNullability = DfaPsiUtil.getElementNullability(myType, myTargetMethod);
      }
    }
    myVarArgCall = false; // vararg method reference calls are not supported now
    myPrecalculatedReturnValue = null;
    myArgRequiredNullability = myTargetMethod == null
                               ? EMPTY_NULLABILITY_ARRAY
                               : calcArgRequiredNullability(resolveResult.getSubstitutor(),
                                                            myTargetMethod.getParameterList().getParameters());
    myShouldFlushFields = !isPureCall();
  }

  public MethodCallInstruction(@NotNull PsiCall call, @Nullable DfaValue precalculatedReturnValue, List<? extends MethodContract> contracts) {
    myContext = call;
    myContracts = Collections.unmodifiableList(contracts);
    myMethodType = MethodType.REGULAR_METHOD_CALL;
    final PsiExpressionList argList = call.getArgumentList();
    PsiExpression[] args = argList != null ? argList.getExpressions() : PsiExpression.EMPTY_ARRAY;
    myArgCount = args.length;
    myType = call instanceof PsiCallExpression ? ((PsiCallExpression)call).getType() : null;

    JavaResolveResult result = call.resolveMethodGenerics();
    myTargetMethod = (PsiMethod)result.getElement();

    PsiSubstitutor substitutor = result.getSubstitutor();
    if (argList != null && myTargetMethod != null) {
      PsiParameter[] parameters = myTargetMethod.getParameterList().getParameters();
      myVarArgCall = isVarArgCall(myTargetMethod, substitutor, args, parameters);
      myArgRequiredNullability = calcArgRequiredNullability(substitutor, parameters);
    } else {
      myVarArgCall = false;
      myArgRequiredNullability = EMPTY_NULLABILITY_ARRAY;
    }

    myShouldFlushFields = !(call instanceof PsiNewExpression && myType != null && myType.getArrayDimensions() > 0 || isPureCall());
    myPrecalculatedReturnValue = precalculatedReturnValue;
    myReturnNullability = call instanceof PsiNewExpression ? Nullability.NOT_NULL : DfaPsiUtil.getElementNullability(myType, myTargetMethod);
  }

  public boolean matches(CallMatcher matcher) {
    switch (myMethodType) {
      case REGULAR_METHOD_CALL:
        return myContext instanceof PsiMethodCallExpression && matcher.test((PsiMethodCallExpression)myContext);
      case METHOD_REFERENCE_CALL:
        return matcher.methodReferenceMatches((PsiMethodReferenceExpression)myContext);
      default:
        return false;
    }
  }

  /**
   * Returns a PsiElement which at best represents an argument with given index
   *
   * @param index an argument index, must be from 0 to {@link #getArgCount()}-1.
   * @return a PsiElement. Either argument expression or method reference if call is described by method reference
   */
  public PsiElement getArgumentAnchor(int index) {
    if (myMethodType == MethodType.REGULAR_METHOD_CALL && myContext instanceof PsiCall) {
      PsiExpressionList argumentList = ((PsiCall)myContext).getArgumentList();
      if (argumentList != null) {
        return argumentList.getExpressions()[index];
      }
    }
    if (myMethodType == MethodType.METHOD_REFERENCE_CALL && myContext instanceof PsiMethodReferenceExpression) {
      return ((PsiMethodReferenceExpression)myContext).getReferenceNameElement();
    }
    return myContext;
  }

  private Nullability[] calcArgRequiredNullability(PsiSubstitutor substitutor, PsiParameter[] parameters) {
    if (myArgCount == 0) {
      return EMPTY_NULLABILITY_ARRAY;
    }

    int checkedCount = Math.min(myArgCount, parameters.length) - (myVarArgCall ? 1 : 0);

    Nullability[] nullabilities = new Nullability[myArgCount];
    for (int i = 0; i < checkedCount; i++) {
      nullabilities[i] = DfaPsiUtil.getElementNullability(substitutor.substitute(parameters[i].getType()), parameters[i]);
    }

    if (myVarArgCall) {
      PsiType lastParamType = substitutor.substitute(parameters[parameters.length - 1].getType());
      if (isEllipsisWithNotNullElements(lastParamType)) {
        Arrays.fill(nullabilities, parameters.length - 1, myArgCount, Nullability.NOT_NULL);
      }
    }
    return nullabilities;
  }

  private static boolean isEllipsisWithNotNullElements(PsiType lastParamType) {
    return lastParamType instanceof PsiEllipsisType &&
           DfaPsiUtil.getElementNullability(((PsiEllipsisType)lastParamType).getComponentType(), null) == Nullability.NOT_NULL;
  }

  public static boolean isVarArgCall(PsiMethod method, PsiSubstitutor substitutor, PsiExpression[] args, PsiParameter[] parameters) {
    if (!method.isVarArgs()) {
      return false;
    }

    int argCount = args.length;
    int paramCount = parameters.length;
    if (argCount > paramCount) {
      return true;
    }

    if (paramCount > 0 && argCount == paramCount) {
      PsiType lastArgType = args[argCount - 1].getType();
      if (lastArgType != null && !substitutor.substitute(parameters[paramCount - 1].getType()).isAssignableFrom(lastArgType)) {
        return true;
      }
    }
    return false;
  }

  private boolean isPureCall() {
    if (myTargetMethod != null) {
      return JavaMethodContractUtil.isPure(myTargetMethod) || SpecialField.findSpecialField(myTargetMethod) != null;
    }
    if (!(myContext instanceof PsiNewExpression)) return false;
    PsiNewExpression newExpression = (PsiNewExpression)myContext;
    if (newExpression.getArgumentList() == null || !newExpression.getArgumentList().isEmpty()) return false;
    PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    if (classReference == null) return false;
    PsiClass clazz = ObjectUtils.tryCast(classReference.resolve(), PsiClass.class);
    if (clazz == null) return false;
    Set<PsiClass> visited = new HashSet<>();
    while (true) {
      for (PsiMethod ctor : clazz.getConstructors()) {
        if(ctor.getParameterList().isEmpty()) return JavaMethodContractUtil.isPure(ctor);
      }
      clazz = clazz.getSuperClass();
      if (clazz == null || !visited.add(clazz)) return false;
    }
  }

  @Nullable
  public PsiType getResultType() {
    return myType;
  }

  public int getArgCount() {
    return myArgCount;
  }

  public MethodType getMethodType() {
    return myMethodType;
  }

  public boolean shouldFlushFields() {
    return myShouldFlushFields;
  }

  @Nullable
  public PsiMethod getTargetMethod() {
    return myTargetMethod;
  }

  public boolean isVarArgCall() {
    return myVarArgCall;
  }

  @Nullable
  public Nullability getArgRequiredNullability(int index) {
    return index >= myArgRequiredNullability.length ? null : myArgRequiredNullability[index];
  }

  public List<MethodContract> getContracts() {
    return myContracts;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitMethodCall(this, runner, stateBefore);
  }

  @Nullable
  public PsiCall getCallExpression() {
    return myMethodType == MethodType.REGULAR_METHOD_CALL && myContext instanceof PsiCall ? (PsiCall)myContext : null;
  }

  @NotNull
  public PsiElement getContext() {
    return myContext;
  }

  /**
   * @return a nullability problem which will occur on call if qualifier is nullable.
   * May return null if nullability problem is impossible for this instruction.
   */
  @Nullable
  public NullabilityProblemKind.NullabilityProblem<?> getQualifierNullabilityProblem() {
    switch (getMethodType()) {
      case UNBOXING:
        return NullabilityProblemKind.unboxingNullable.problem(getContext());
      case METHOD_REFERENCE_CALL:
        return NullabilityProblemKind.callMethodRefNPE.problem((PsiMethodReferenceExpression)getContext());
      case REGULAR_METHOD_CALL:
        if (getContext() instanceof PsiMethodCallExpression) {
          return NullabilityProblemKind.callNPE.problem((PsiMethodCallExpression)getContext());
        }
        // If context is something else (e.g. PsiNewExpression), qualifier is not dereferenced
        return null;
      default:
        // Qualifier is not dereferenced for BOXING or CAST
        return null;
    }
  }

  @Nullable
  public DfaValue getPrecalculatedReturnValue() {
    return myPrecalculatedReturnValue;
  }

  @NotNull
  public Nullability getReturnNullability() {
    return myReturnNullability;
  }

  public String toString() {
    switch (myMethodType) {
      case UNBOXING:
        return "UNBOX";
      case BOXING:
        return "BOX";
      case CAST:
        return "CAST TO " + myType;
      case METHOD_REFERENCE_CALL:
        return "CALL_METHOD_REFERENCE: " + myContext.getText();
      case REGULAR_METHOD_CALL:
        return "CALL_METHOD: " + myContext.getText();
      default:
        throw new IllegalStateException("Unexpected method type: " + myMethodType);
    }
  }
}
