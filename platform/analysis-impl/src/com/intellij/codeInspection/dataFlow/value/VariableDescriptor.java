// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a descriptor of {@link DfaVariableValue}. Two variables are the same if they have the same descriptor and qualifier.
 * A descriptor could be a PsiVariable, getter method, array element with given index, this expression, etc.
 * <p>
 * Subclasses must have proper {@link Object#equals(Object)} and {@link Object#hashCode()} implementation or instantiation must be
 * controlled to prevent creating equal objects. Also {@link #toString()} must return sane representation of the descriptor.
 * <p>
 * VariableDescriptors must be independent from {@link DfaValueFactory} instance, as they could be reused with another factory.
 */
public interface VariableDescriptor {
  /**
   * @return a PSI element associated with this descriptor or null if not applicable
   */
  default @Nullable PsiElement getPsiElement() {
    return null;
  }

  /**
   * @return true if the value stored in this descriptor cannot be changed implicitly (e.g. inside the unknown method call)
   */
  boolean isStable();

  /**
   * @return true if the value can be captured in closure
   */
  default boolean canBeCapturedInClosure() {
    return isStable();
  }

  /**
   * @return true if the value behind this descriptor is a method call which result might be computed from other sources
   */
  default boolean isCall() {
    return false;
  }

  /**
   * Returns a value which describes the field qualified by given qualifier and described by this descriptor
   * @param factory factory to use
   * @param qualifier qualifier to use
   * @return a field value
   */
  default @NotNull DfaValue createValue(@NotNull DfaValueFactory factory, @Nullable DfaValue qualifier) {
    if (qualifier instanceof DfaVariableValue) {
      return factory.getVarFactory().createVariableValue(this, (DfaVariableValue)qualifier);
    }
    return factory.getUnknown();
  }

  /**
   * @return true if the variable having this descriptor could be read implicitly, so it should not be auto-flushed.
   */
  default boolean isImplicitReadPossible() {
    return false;
  }

  /**
   * Returns the DfType of the value which is qualified by given qualifier and described by this descriptor
   * @param qualifier qualifier (may be null if absent)
   * @return DfType of the value. Every instance of the variable described by this descriptor must be a subtype
   * of this type.
   */
  @NotNull
  DfType getDfType(@Nullable DfaVariableValue qualifier);

  /**
   * Returns the DfType the value with this descriptor has at the beginning of the interpretation in a given context.
   *
   * @param thisValue DfaVariableValue representing the current variable
   * @param context analysis context. Usually method body, lambda body, some block, or class (for class initializers analysis)
   * @return Initial DfType of the value. May differ from {@link #getDfType(DfaVariableValue)} result,
   * as additional information like initial nullability, range, or locality may be known from annotations or context, which
   * may change later if the value is reassigned.
   */
  default @NotNull DfType getInitialDfType(@NotNull DfaVariableValue thisValue,
                                  @Nullable PsiElement context) {
    return getDfType(thisValue.getQualifier());
  }

  /**
   * @return false if variable described by this descriptor may be not equal to itself when applying the {@link RelationType#EQ}.
   * This could be possible if variable is backed by a pure method that always returns a new object. 
   * @param type variable type
   */
  default boolean alwaysEqualsToItself(@NotNull DfType type) {
    return true;
  }

  /**
   * @param state memory state (must not be modified)
   * @param value value of the variable described by this descriptor
   * @return a constraint on the qualified implied by a given value 
   */
  default @NotNull DfType getQualifierConstraintFromValue(@NotNull DfaMemoryState state, @NotNull DfaValue value) {
    return DfType.TOP;
  }
}
