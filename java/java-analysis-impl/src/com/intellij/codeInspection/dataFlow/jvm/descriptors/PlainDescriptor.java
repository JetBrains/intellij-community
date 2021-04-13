// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.descriptors;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.dataFlow.jvm.FieldChecker;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A descriptor that represents a PsiVariable (either local, or field -- may have a qualifier)
 */
public final class PlainDescriptor extends PsiVarDescriptor {
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
  PsiType getType(@Nullable DfaVariableValue qualifier) {
    PsiType type = myVariable.getType();
    if (type instanceof PsiEllipsisType) {
      type = ((PsiEllipsisType)type).toArrayType();
    }
    return getSubstitutor(myVariable, qualifier).substitute(type);
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
    return super.createValue(factory, qualifier, forAccessor);
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

  @Override
  @NotNull DfaNullability calcCanBeNull(@NotNull PsiModifierListOwner var,
                                        @NotNull DfaVariableValue value,
                                        @Nullable PsiElement context) {
    if (var instanceof PsiField && DfaUtil.hasInitializationHacks((PsiField)var)) {
      return DfaNullability.FLUSHED;
    }

    PsiType type = getType(value.getQualifier());
    Nullability nullability = DfaPsiUtil.getElementNullabilityIgnoringParameterInference(type, var);
    if (nullability != Nullability.UNKNOWN) {
      return DfaNullability.fromNullability(nullability);
    }

    if (var instanceof PsiParameter && var.getParent() instanceof PsiForeachStatement) {
      PsiExpression iteratedValue = ((PsiForeachStatement)var.getParent()).getIteratedValue();
      if (iteratedValue != null) {
        PsiType itemType = JavaGenericsUtil.getCollectionItemType(iteratedValue);
        if (itemType != null) {
          return DfaNullability.fromNullability(DfaPsiUtil.getElementNullability(itemType, var));
        }
      }
    }

    if (var instanceof PsiField && FieldChecker.getChecker(context).canTrustFieldInitializer((PsiField)var)) {
      return DfaNullability.fromNullability(NullabilityUtil.getNullabilityFromFieldInitializers((PsiField)var).second);
    }
    return DfaNullability.UNKNOWN;
  }
}
