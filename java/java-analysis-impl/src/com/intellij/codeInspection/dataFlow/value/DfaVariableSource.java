// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a source of {@link DfaVariableValue}. Two variables are the same if they have the same source, qualifier and negation flag.
 * A source could be a PsiVariable, getter method, array element with given index, this expression, etc.
 * <p>
 * Subclasses must have proper {@link Object#equals(Object)} and implementation or instantiation must be controlled to prevent
 * creating equal objects. Also {@link #toString()} must return sane representation of the source.
 */
public interface DfaVariableSource {
  /**
   * @return a PSI element associated with given source or null if not applicable
   */
  @Nullable
  default PsiModifierListOwner getPsiElement() {
    return null;
  }

  /**
   * @return true if the value stored in this source cannot be changed implicitly (e.g. inside the unknown method call)
   */
  boolean isStable();

  /**
   * @return true if the value behind this source is a method call which result might be computed from other sources
   */
  default boolean isCall() {
    return false;
  }

  /**
   * Must be overridden to return stable string representation of the source.
   * In particular {@code source1.equals(source2)} implies that {@code source1.toString().equals(source2.toString())}
   */
  @Override
  String toString();
}
