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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.java.JavaDfaValueFactory;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DfaCallArguments)) return false;
    DfaCallArguments that = (DfaCallArguments)o;
    return myQualifier == that.myQualifier &&
           myMutation.equals(that.myMutation) &&
           Arrays.equals(myArguments, that.myArguments);
  }

  @Override
  public int hashCode() {
    return (Objects.hashCode(myQualifier) * 31 + Arrays.hashCode(myArguments))*31+myMutation.hashCode();
  }
  
  public DfaValue[] toArray() {
    if (myArguments == null || myQualifier == null) return new DfaValue[0];
    return ArrayUtil.prepend(myQualifier, myArguments);
  }

  public void flush(@NotNull DfaMemoryState state, @NotNull DfaValueFactory factory, @Nullable PsiMethod method) {
    SideEffectHandlers.SideEffectHandler handler = SideEffectHandlers.getHandler(method);
    if (handler != null) {
      handler.handleSideEffect(factory, state, this);
      return;
    }
    if (myMutation.isPure()) {
      if (myQualifier instanceof DfaVariableValue) {
        DfaVariableValue qualifier = (DfaVariableValue)myQualifier;
        // We assume that even pure call may modify private fields (e.g., to cache something)
        state.flushVariables(v -> v.getQualifier() == qualifier &&
                                  v.getPsiVariable() instanceof PsiMember &&
                                  ((PsiMember)v.getPsiVariable()).hasModifierProperty(PsiModifier.PRIVATE));
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
    PsiParameter[] parameters = method.getParameterList().getParameters();
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
