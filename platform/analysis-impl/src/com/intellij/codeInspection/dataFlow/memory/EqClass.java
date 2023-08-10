// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.memory;

import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class EqClass extends SortedIntSet implements Iterable<DfaVariableValue> {
  private final DfaValueFactory myFactory;

  /**
   * A comparator which allows to select a "canonical" variable of several variables (which is minimal of them).
   * Variables with shorter qualifier chain are preferred to be canonical.
   */
  public static final Comparator<DfaVariableValue> CANONICAL_VARIABLE_COMPARATOR =
    Comparator.nullsFirst((v1, v2) -> {
      int result = EqClass.CANONICAL_VARIABLE_COMPARATOR.compare(v1.getQualifier(), v2.getQualifier());
      if (result != 0) return result;
      return Integer.compare(v1.getID(), v2.getID());
    });

  public EqClass(DfaValueFactory factory) {
    myFactory = factory;
  }

  public EqClass(@NotNull EqClass toCopy) {
    super(toCopy.toNativeArray());
    myFactory = toCopy.myFactory;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append("(");
    for (int i = 0; i < size(); i++) {
      if (i > 0) buf.append(", ");
      int value = get(i);
      DfaValue dfaValue = myFactory.getValue(value);
      buf.append(dfaValue);
    }
    buf.append(")");
    return buf.toString();
  }

  public DfaVariableValue getVariable(int index) {
    return (DfaVariableValue)myFactory.getValue(get(index));
  }

  /**
   * @return copy of variables from this class as a list. Use this method if you expect
   * class updates during the iteration.
   */
  public List<DfaVariableValue> asList() {
    List<DfaVariableValue> vars = new ArrayList<>(size());
    forValues(id -> vars.add((DfaVariableValue)myFactory.getValue(id)));
    return vars;
  }

  /**
   * @return the "canonical" variable for this class (according to {@link #CANONICAL_VARIABLE_COMPARATOR}) or
   * null if the class does not contain variables.
   */
  @Nullable
  public DfaVariableValue getCanonicalVariable() {
    if (size() == 1) {
      return getVariable(0);
    }
    return StreamEx.of(iterator()).min(CANONICAL_VARIABLE_COMPARATOR).orElse(null);
  }

  @NotNull
  @Override
  public Iterator<DfaVariableValue> iterator() {
    return new Iterator<>() {
      int pos;

      @Override
      public boolean hasNext() {
        return pos < size();
      }

      @Override
      public DfaVariableValue next() {
        if (pos >= size()) throw new NoSuchElementException();
        return (DfaVariableValue)myFactory.getValue(get(pos++));
      }
    };
  }
}
