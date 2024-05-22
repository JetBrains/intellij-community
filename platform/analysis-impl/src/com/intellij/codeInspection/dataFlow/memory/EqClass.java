// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.memory;

import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

/**
 * Represents an ordered class of equivalent variables inside {@link DfaMemoryState}.
 * The interface is a read-only view over a mutable structure, so other code may modify it.
 */
public sealed interface EqClass extends Iterable<DfaVariableValue> permits EqClassImpl {
  /**
   * @param index index of a variable within class (0..size-1)
   * @return variable at a given index
   */
  @NotNull DfaVariableValue getVariable(int index);

  /**
   * @return a copy of this class in the form of the list
   */
  @NotNull List<@NotNull DfaVariableValue> asList();

  /**
   * @return number of variables within the class
   */
  int size();

  /**
   * Checks if the class is empty.
   *
   * @return true if the class is empty, false otherwise.
   */
  default boolean isEmpty() {
    return size() == 0;
  }

  @Nullable
  DfaVariableValue getCanonicalVariable();

  /**
   * @return an iterator over the variables of this class.
   */
  @Override
  @NotNull
  Iterator<@NotNull DfaVariableValue> iterator();

  /**
   * Checks if the given DfaValue is contained in this class.
   *
   * @param value the DfaValue to check
   * @return true if the class contains the given value, otherwise false
   */
  boolean contains(@NotNull DfaValue value);

  /**
   * Checks if the current EqClass contains all elements of the given EqClass.
   *
   * @param that the EqClass to check against.
   * @return true if the current EqClass contains all elements of the given EqClass,
   *         false otherwise.
   */
  boolean containsAll(@NotNull EqClass that);
}
