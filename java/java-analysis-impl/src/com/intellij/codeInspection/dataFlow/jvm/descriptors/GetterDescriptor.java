// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.descriptors;

import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.TypeConstraints;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfAntiConstantType;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.types.DfPrimitiveType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A descriptor that represents a getter-like method (without arguments)
 */
public final class GetterDescriptor extends PsiVarDescriptor {
  private static final CallMatcher STABLE_METHODS = CallMatcher.anyOf(
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "getClass").parameterCount(0),
    CallMatcher.instanceCall("java.lang.reflect.Member", "getName", "getModifiers", "getDeclaringClass", "isSynthetic"),
    CallMatcher.instanceCall("java.lang.reflect.Executable", "getParameterCount", "isVarArgs"),
    CallMatcher.instanceCall("java.lang.reflect.Field", "getType"),
    CallMatcher.instanceCall("java.lang.reflect.Method", "getReturnType"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_CLASS, "getName", "isInterface", "isArray", "isPrimitive", "isSynthetic",
                             "isAnonymousClass", "isLocalClass", "isMemberClass", "getDeclaringClass", "getEnclosingClass",
                             "getSimpleName", "getCanonicalName")
  );
  private final @NotNull PsiMethod myGetter;
  private final boolean myStable;

  public GetterDescriptor(@NotNull PsiMethod getter) {
    myGetter = getter;
    if (STABLE_METHODS.methodMatches(getter) || getter instanceof LightRecordMethod) {
      myStable = true;
    }
    else {
      PsiField field = PsiUtil.canBeOverridden(getter) ? null : PropertyUtil.getFieldOfGetter(getter);
      myStable = field != null && field.hasModifierProperty(PsiModifier.FINAL);
    }
  }

  @NotNull
  @Override
  public String toString() {
    return myGetter.getName();
  }

  @Nullable
  @Override
  PsiType getType(@Nullable DfaVariableValue qualifier) {
    return getSubstitutor(myGetter, qualifier).substitute(myGetter.getReturnType());
  }

  @NotNull
  @Override
  public PsiMethod getPsiElement() {
    return myGetter;
  }

  @Override
  public boolean isStable() {
    return myStable;
  }

  @Override
  public boolean isCall() {
    return true;
  }

  @NotNull
  @Override
  public DfaValue createValue(@NotNull DfaValueFactory factory, @Nullable DfaValue qualifier) {
    if (myGetter.hasModifierProperty(PsiModifier.STATIC)) {
      return factory.getVarFactory().createVariableValue(this);
    }
    return super.createValue(factory, qualifier);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myGetter.getName());
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this || (obj instanceof GetterDescriptor && ((GetterDescriptor)obj).myGetter == myGetter);
  }

  @Override
  @NotNull DfaNullability calcCanBeNull(@NotNull DfaVariableValue value, @Nullable PsiElement context) {
    PsiType type = getType(value.getQualifier());
    return DfaNullability.fromNullability(DfaPsiUtil.getElementNullabilityIgnoringParameterInference(type, myGetter));
  }

  @Override
  public boolean alwaysEqualsToItself(@NotNull DfType type) {
    if (!super.alwaysEqualsToItself(type)) return false;
    if (type instanceof DfPrimitiveType || type instanceof DfConstantType) return true;
    if (PropertyUtilBase.isSimplePropertyGetter(myGetter) || myGetter instanceof LightRecordMethod) return true;
    return false;
  }

  @Override
  public @NotNull DfType getQualifierConstraintFromValue(@NotNull DfaMemoryState state, @NotNull DfaValue value) {
    if (!PsiTypesUtil.isGetClass(myGetter)) return DfType.TOP;
    DfType type = state.getDfType(value);
    DfType constraint = DfType.TOP;
    PsiType cls = type.getConstantOfType(PsiType.class);
    if (cls != null) {
      constraint = TypeConstraints.exact(cls).asDfType();
    }
    else if (type instanceof DfAntiConstantType) {
      constraint = StreamEx.of(((DfAntiConstantType<?>)type).getNotValues()).select(PsiType.class)
        .map(t -> TypeConstraints.exact(t).tryNegate())
        .nonNull()
        .reduce(TypeConstraint::meet).orElse(TypeConstraints.TOP).asDfType();
    }
    if (value instanceof DfaVariableValue && ((DfaVariableValue)value).getDescriptor().equals(this)) {
      DfaVariableValue qualifier = ((DfaVariableValue)value).getQualifier();
      if (qualifier != null) {
        constraint = constraint.meet(TypeConstraint.fromDfType(state.getDfType(qualifier)).asDfType());
      }
    }
    return constraint;
  }
}
