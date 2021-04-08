// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.descriptors;

import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A descriptor that represents a PsiVariable (either local, or field -- may have a qualifier)
 */
public final class PlainDescriptor implements VariableDescriptor {
  private final @NotNull PsiVariable myVariable;

  public PlainDescriptor(@NotNull PsiVariable variable) {
    myVariable = variable;
  }

  @NotNull
  @Override
  public String toString() {
    return String.valueOf(myVariable.getName());
  }

  @Override
  public PsiType getType(@Nullable DfaVariableValue qualifier) {
    PsiType type = myVariable.getType();
    if (type instanceof PsiEllipsisType) {
      type = ((PsiEllipsisType)type).toArrayType();
    }
    return DfaPsiUtil.getSubstitutor(myVariable, qualifier).substitute(type);
  }

  @Override
  public PsiVariable getPsiElement() {
    return myVariable;
  }

  @Override
  public boolean isStable() {
    return PsiUtil.isJvmLocalVariable(myVariable) ||
           (myVariable.hasModifierProperty(PsiModifier.FINAL) && !DfaUtil.hasInitializationHacks(myVariable));
  }

  @NotNull
  @Override
  public DfaValue createValue(@NotNull DfaValueFactory factory, @Nullable DfaValue qualifier, boolean forAccessor) {
    if (myVariable.hasModifierProperty(PsiModifier.VOLATILE)) {
      PsiType type = getType(ObjectUtils.tryCast(qualifier, DfaVariableValue.class));
      return factory.getObjectType(type, DfaPsiUtil.getElementNullability(type, myVariable));
    }
    if (PsiUtil.isJvmLocalVariable(myVariable) ||
        (myVariable instanceof PsiField && myVariable.hasModifierProperty(PsiModifier.STATIC))) {
      return factory.getVarFactory().createVariableValue(this);
    }
    return VariableDescriptor.super.createValue(factory, qualifier, forAccessor);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myVariable.getName());
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this || obj instanceof PlainDescriptor && ((PlainDescriptor)obj).myVariable == myVariable;
  }

  @NotNull
  public static DfaVariableValue createVariableValue(@NotNull DfaValueFactory factory, @NotNull PsiVariable variable) {
    DfaVariableValue qualifier = null;
    if (variable instanceof PsiField && !(variable.hasModifierProperty(PsiModifier.STATIC))) {
      qualifier = ThisDescriptor.createThisValue(factory, ((PsiField)variable).getContainingClass());
    }
    return factory.getVarFactory().createVariableValue(new PlainDescriptor(variable), qualifier);
  }
}
