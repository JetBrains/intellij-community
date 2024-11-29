// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.descriptors;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.codeInspection.dataFlow.MutationSignature;
import com.intellij.codeInspection.dataFlow.TypeConstraints;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A variable descriptor that represents 'this' reference
 */
public final class ThisDescriptor extends PsiVarDescriptor {
  @NotNull
  private final PsiClass myQualifier;

  /**
   * Creates a descriptor that represents accessible 'this' variable of a specific class type
   * 
   * @param psiClass PSI class designating the corresponding 'this' variable. In case of an inner class, may refer to an outer one.
   */
  public ThisDescriptor(@NotNull PsiClass psiClass) {
    myQualifier = psiClass;
  }

  @NotNull
  @Override
  public String toString() {
    if (myQualifier instanceof PsiAnonymousClass) {
      return "(anonymous " + ((PsiAnonymousClass)myQualifier).getBaseClassReference().getText() + ").this";
    }
    return myQualifier.getName() + ".this";
  }

  @NotNull
  @Override
  PsiType getType(@Nullable DfaVariableValue qualifier) {
    return new PsiImmediateClassType(myQualifier, PsiSubstitutor.EMPTY);
  }

  @Override
  public @NotNull DfType getDfType(@Nullable DfaVariableValue qualifier) {
    return DfTypes.typedObject(getType(qualifier), Nullability.NOT_NULL);
  }

  @Override
  public @NotNull PsiClass getPsiElement() {
    return myQualifier;
  }

  @Override
  public boolean isStable() {
    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myQualifier.getQualifiedName());
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj || obj instanceof ThisDescriptor && ((ThisDescriptor)obj).myQualifier == myQualifier;
  }

  @Override
  public @NotNull DfType getInitialDfType(@NotNull DfaVariableValue thisValue,
                                          @Nullable PsiElement context) {
    DfType dfType = getDfType(thisValue.getQualifier());
    // In class initializer this variable is local until escaped
    if (context != null) {
      PsiMethod method = ObjectUtils.tryCast(context.getParent(), PsiMethod.class);
      if (method != null && myQualifier.equals(method.getContainingClass())) {
        if (myQualifier instanceof PsiEnumConstantInitializer) {
          PsiEnumConstant constant = ((PsiEnumConstantInitializer)myQualifier).getEnumConstant();
          return DfTypes.referenceConstant(constant, TypeConstraints.exactClass(myQualifier));
        }
        if (!method.isConstructor() && MutationSignature.fromMethod(method).preservesThis()) {
          return dfType.meet(Mutability.UNMODIFIABLE_VIEW.asDfType());
        }
        if (method.isConstructor()) {
          return dfType.meet(DfTypes.LOCAL_OBJECT);
        }
      }
      if (myQualifier.equals(context)) {
        return dfType.meet(DfTypes.LOCAL_OBJECT);
      }
    }
    return dfType;
  }

  /**
   * Creates a variable representing "this" value with given class as a context
   * @param aClass a class to bind "this" value to
   * @return a DFA variable
   */
  @Contract("_, null -> null; _, !null -> !null")
  public static DfaVariableValue createThisValue(@NotNull DfaValueFactory factory, @Nullable PsiClass aClass) {
    if (aClass == null) return null;
    return factory.getVarFactory().createVariableValue(new ThisDescriptor(aClass));
  }

  @Override
  public @NotNull DfaValue createValue(@NotNull DfaValueFactory factory, @Nullable DfaValue qualifier) {
    if (qualifier != null) return factory.getUnknown();
    return factory.getVarFactory().createVariableValue(this);
  }
}
