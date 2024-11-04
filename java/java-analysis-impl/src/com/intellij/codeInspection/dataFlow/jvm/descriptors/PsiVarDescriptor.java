// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.descriptors;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfIntegralType;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class PsiVarDescriptor extends JvmVariableDescriptor {
  abstract @Nullable PsiType getType(@Nullable DfaVariableValue qualifier);

  @NotNull
  static PsiSubstitutor getSubstitutor(PsiElement member, @Nullable DfaVariableValue qualifier) {
    if (member instanceof PsiMember && qualifier != null) {
      PsiClass fieldClass = ((PsiMember)member).getContainingClass();
      PsiVarDescriptor qualifierDescriptor = ObjectUtils.tryCast(qualifier.getDescriptor(), PsiVarDescriptor.class);
      PsiClassType classType = qualifierDescriptor == null ? null :
                               ObjectUtils.tryCast(qualifierDescriptor.getType(qualifier.getQualifier()), PsiClassType.class);
      if (classType != null && InheritanceUtil.isInheritorOrSelf(classType.resolve(), fieldClass, true)) {
        return TypeConversionUtil.getSuperClassSubstitutor(fieldClass, classType);
      }
    }
    return PsiSubstitutor.EMPTY;
  }

  @Override
  @NotNull
  public DfType getDfType(@Nullable DfaVariableValue qualifier) {
    return DfTypes.typedObject(getType(qualifier), Nullability.UNKNOWN);
  }

  @Override
  public @NotNull DfaValue createValue(@NotNull DfaValueFactory factory, @Nullable DfaValue qualifier) {
    if (qualifier instanceof DfaVariableValue) {
      return factory.getVarFactory().createVariableValue(this, (DfaVariableValue)qualifier);
    }
    PsiType type = getType(null);
    PsiModifierListOwner element = ObjectUtils.tryCast(getPsiElement(), PsiModifierListOwner.class);
    LongRangeSet range = JvmPsiRangeSetUtil.fromPsiElement(element);
    DfType dfType = DfTypes.typedObject(type, DfaPsiUtil.getElementNullabilityIgnoringParameterInference(type, element));
    if (dfType instanceof DfIntegralType) {
      dfType = ((DfIntegralType)dfType).meetRange(range);
    }
    return factory.fromDfType(dfType);
  }

  @Override
  public @NotNull DfType getInitialDfType(@NotNull DfaVariableValue thisValue,
                                          @Nullable PsiElement context) {
    DfType dfType = getDfType(thisValue.getQualifier());
    PsiModifierListOwner psi = ObjectUtils.tryCast(getPsiElement(), PsiModifierListOwner.class);
    if (psi == null) return dfType;
    if (dfType instanceof DfIntegralType integralType) {
      return integralType.meetRange(JvmPsiRangeSetUtil.fromPsiElement(psi));
    }
    if (dfType instanceof DfReferenceType) {
      Mutability mutability = Mutability.getMutability(psi);
      if (mutability == Mutability.MUST_NOT_MODIFY &&
          context != null && !PsiTreeUtil.isAncestor(context.getParent(), psi, false)) {
        // Pure method may return impure lambda, so method parameter may still be modified 
        // in nested lambdas/anonymous classes
        mutability = Mutability.UNKNOWN;
      }
      dfType = dfType.meet(mutability.asDfType());
      dfType = dfType.meet(calcCanBeNull(thisValue, context).asDfType());
    }
    return dfType;
  }

  @NotNull DfaNullability calcCanBeNull(@NotNull DfaVariableValue value, @Nullable PsiElement context) {
    return DfaNullability.UNKNOWN;
  }
}
