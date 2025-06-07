// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.java.JavaDfaValueFactory;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.ThisDescriptor;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class DfaCallArguments {
  final DfaValue myQualifier;
  final DfaValue[] myArguments;
  final @NotNull MutationSignature myMutation;

  public DfaCallArguments(DfaValue qualifier, DfaValue[] arguments, @NotNull MutationSignature mutation) {
    myQualifier = qualifier;
    myArguments = arguments;
    myMutation = mutation;
  }

  public DfaValue getQualifier() {
    return myQualifier;
  }

  public DfaValue[] getArguments() {
    return myArguments;
  }

  /**
   * @return pure equivalent of this 
   */
  public DfaCallArguments makeTransparent() {
    return myMutation == MutationSignature.transparent() ? this : new DfaCallArguments(myQualifier, myArguments, MutationSignature.transparent());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    return o instanceof DfaCallArguments that && 
           myQualifier == that.myQualifier &&
           myMutation.equals(that.myMutation) &&
           Arrays.equals(myArguments, that.myArguments);
  }

  @Override
  public int hashCode() {
    return (Objects.hashCode(myQualifier) * 31 + Arrays.hashCode(myArguments))*31+myMutation.hashCode();
  }

  @Override
  public String toString() {
    return myQualifier + ".call(" + StringUtil.join(Arrays.asList(myArguments), ", ") + ")";
  }

  public DfaValue[] toArray() {
    if (myArguments == null || myQualifier == null) return DfaValue.EMPTY_ARRAY;
    return ArrayUtil.prepend(myQualifier, myArguments);
  }

  public void flush(@NotNull DfaMemoryState state, @NotNull DfaValueFactory factory, @Nullable PsiMethod method) {
    SideEffectHandlers.SideEffectHandler handler = SideEffectHandlers.getHandler(method);
    if (handler != null) {
      handler.handleSideEffect(factory, state, this);
      return;
    }
    if (myMutation.isTransparent()) return;
    if (myMutation.isPure()) {
      if (myQualifier instanceof DfaVariableValue) {
        DfaValue qualifier;
        if (method != null && method.isConstructor()) {
          qualifier = ThisDescriptor.createThisValue(factory, method.getContainingClass());
        } else {
          qualifier = myQualifier;
        }
        // We assume that even pure call may modify private fields (e.g., to cache something)
        state.flushVariables(v -> v.getQualifier() == qualifier &&
                                  v.getPsiVariable() instanceof PsiMember member &&
                                  member != method &&
                                  member.hasModifierProperty(PsiModifier.PRIVATE) &&
                                  !member.hasModifierProperty(PsiModifier.FINAL));
      }
      return;
    }
    if (myMutation == MutationSignature.UNKNOWN || myArguments == null) {
      state.flushFields();
      return;
    }
    Set<DfaValue> qualifiers = new HashSet<>();
    if (myQualifier != null && myMutation.mutatesThis()) {
      qualifiers.add(myQualifier);
    }
    for (int i = 0; i < myArguments.length; i++) {
      if (myMutation.mutatesArg(i)) {
        qualifiers.add(myArguments[i]);
      }
    }
    state.flushFieldsQualifiedBy(qualifiers);
  }

  static @Nullable DfaCallArguments fromCall(DfaValueFactory factory, PsiCallExpression call) {
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    PsiExpressionList argumentList = call.getArgumentList();
    if (argumentList == null) return null;
    DfaValue qualifierValue = null;
    if (call instanceof PsiMethodCallExpression) {
      PsiExpression qualifier = ((PsiMethodCallExpression)call).getMethodExpression().getQualifierExpression();
      qualifierValue = JavaDfaValueFactory.getExpressionDfaValue(factory, qualifier);
    }
    if (qualifierValue == null) {
      qualifierValue = factory.getUnknown();
    }
    boolean varArgCall = MethodCallUtils.isVarArgCall(call);
    PsiExpression[] args = argumentList.getExpressions();
    PsiParameterList parameterList = method.getParameterList();
    if (parameterList.isEmpty()) {
      return new DfaCallArguments(qualifierValue, DfaValue.EMPTY_ARRAY, MutationSignature.fromCall(call)); 
    }
    PsiParameter[] parameters = parameterList.getParameters();
    DfaValue[] argValues = new DfaValue[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      DfaValue argValue = null;
      if (i < args.length && (!varArgCall || i < parameters.length - 1)) {
        argValue = JavaDfaValueFactory.getExpressionDfaValue(factory, args[i]);
      }
      if (argValue == null) {
        argValue = factory.getUnknown();
      }
      argValues[i] = argValue;
    }
    return new DfaCallArguments(qualifierValue, argValues, MutationSignature.fromCall(call));
  }
}
