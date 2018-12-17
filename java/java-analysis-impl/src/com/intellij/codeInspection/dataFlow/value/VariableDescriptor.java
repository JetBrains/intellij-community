// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a descriptor of {@link DfaVariableValue}. Two variables are the same if they have the same descriptor and qualifier.
 * A descriptor could be a PsiVariable, getter method, array element with given index, this expression, etc.
 * <p>
 * Subclasses must have proper {@link Object#equals(Object)} and implementation or instantiation must be controlled to prevent
 * creating equal objects. Also {@link #toString()} must return sane representation of the descriptor.
 */
public interface VariableDescriptor {
  /**
   * @return a PSI element associated with this descriptor or null if not applicable
   */
  @Nullable
  default PsiModifierListOwner getPsiElement() {
    return null;
  }

  /**
   * @return true if the value stored in this descriptor cannot be changed implicitly (e.g. inside the unknown method call)
   */
  boolean isStable();

  /**
   * @return true if the value behind this descriptor is a method call which result might be computed from other sources
   */
  default boolean isCall() {
    return false;
  }

  /**
   * Must be overridden to return stable string representation of the descriptor.
   * In particular {@code desc1.equals(desc2)} implies that {@code desc1.toString().equals(desc2.toString())}
   */
  @Override
  String toString();

  /**
   * Returns a value which describes the field qualified by given qualifier and described by this descriptor
   * @param factory factory to use
   * @param qualifier qualifier to use
   * @param type field type
   * @return a field value
   */
  @NotNull
  default DfaValue createValue(@NotNull DfaValueFactory factory, @Nullable DfaValue qualifier, @Nullable PsiType type) {
    if (qualifier instanceof DfaVariableValue) {
      return factory.getVarFactory().createVariableValue(this, type, (DfaVariableValue)qualifier);
    }
    return factory.createTypeValue(type, DfaPsiUtil.getElementNullability(type, getPsiElement()));
  }
}
