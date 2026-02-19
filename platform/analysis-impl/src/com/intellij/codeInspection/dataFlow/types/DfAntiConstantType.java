// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.types;

import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a type that maintains a set of constants that excluded from this type
 */
public abstract class DfAntiConstantType<T> implements DfType {
  protected final @NotNull Set<T> myNotValues;

  protected DfAntiConstantType(@NotNull Set<T> notValues) {
    myNotValues = notValues;
  }

  /**
   * @return set of excluded constants
   */
  public @NotNull Set<T> getNotValues() {
    return Collections.unmodifiableSet(myNotValues);
  }

  @Override
  public int hashCode() {
    return myNotValues.hashCode()+1234;
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this || obj instanceof DfAntiConstantType && Objects.equals(((DfAntiConstantType<?>)obj).myNotValues, myNotValues);
  }

  protected String renderValue(T value) {
    return String.valueOf(value);
  }

  @Override
  public @NotNull String toString() {
    return "!= " + StreamEx.of(myNotValues).map(this::renderValue).joining(", ");
  }
}
