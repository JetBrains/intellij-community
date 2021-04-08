// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.descriptors;

import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A variable descriptor that represents 'this' reference
 */
public final class ThisDescriptor implements VariableDescriptor {
  @NotNull
  private final PsiClass myQualifier;

  public ThisDescriptor(@NotNull PsiClass qualifier) {
    myQualifier = qualifier;
  }

  @NotNull
  @Override
  public String toString() {
    return myQualifier.getName() + ".this";
  }

  @NotNull
  @Override
  public PsiType getType(@Nullable DfaVariableValue qualifier) {
    return new PsiImmediateClassType(myQualifier, PsiSubstitutor.EMPTY);
  }

  @Override
  public PsiClass getPsiElement() {
    return myQualifier;
  }

  @Override
  public boolean isStable() {
    return true;
  }

  @Override
  public boolean isImplicitReadPossible() {
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

  /**
   * Creates a variable representing "this" value with given class as a context
   * @param factory
   * @param aClass a class to bind "this" value to
   * @return a DFA variable
   */
  @Contract("_, null -> null; _, !null -> !null")
  public static DfaVariableValue createThisValue(@NotNull DfaValueFactory factory, @Nullable PsiClass aClass) {
    if (aClass == null) return null;
    return factory.getVarFactory().createVariableValue(new ThisDescriptor(aClass));
  }
}
