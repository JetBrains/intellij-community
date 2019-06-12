// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ObjectUtils;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author peter
 */
class EqClass extends SortedIntSet {
  private final DfaValueFactory myFactory;

  /**
   * A comparator which allows to select a "canonical" variable of several variables (which is minimal of them).
   * Variables with shorter qualifier chain are preferred to be canonical.
   */
  static final Comparator<DfaVariableValue> CANONICAL_VARIABLE_COMPARATOR =
    Comparator.nullsFirst((v1, v2) -> {
      int result = EqClass.CANONICAL_VARIABLE_COMPARATOR.compare(v1.getQualifier(), v2.getQualifier());
      if (result != 0) return result;
      return Integer.compare(v1.getID(), v2.getID());
    });

  EqClass(DfaValueFactory factory) {
    myFactory = factory;
  }

  EqClass(@NotNull EqClass toCopy) {
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

  List<DfaVariableValue> getVariables(boolean unwrap) {
    List<DfaVariableValue> vars = new ArrayList<>();
    forEach(id -> {
      DfaValue value = myFactory.getValue(id);
      if (value instanceof DfaVariableValue) {
        vars.add((DfaVariableValue)value);
      }
      else if (unwrap && value instanceof DfaBoxedValue) {
        vars.add(((DfaBoxedValue)value).getWrappedValue());
      }
      return true;
    });
    return vars;
  }

  /**
   * @return the "canonical" variable for this class (according to {@link #CANONICAL_VARIABLE_COMPARATOR}) or
   * null if the class does not contain variables.
   */
  @Nullable
  DfaVariableValue getCanonicalVariable() {
    if (size() == 1) {
      return ObjectUtils.tryCast(myFactory.getValue(get(0)), DfaVariableValue.class);
    }
    return IntStreamEx.range(size()).mapToObj(idx -> myFactory.getValue(get(idx)))
      .select(DfaVariableValue.class).min(CANONICAL_VARIABLE_COMPARATOR).orElse(null);
  }

  List<DfaValue> getMemberValues() {
    final List<DfaValue> result = new ArrayList<>(size());
    forEach(id -> {
      DfaValue value = myFactory.getValue(id);
      result.add(value);
      return true;
    });
    return result;
  }

  @Nullable
  DfaConstValue findConstant() {
    Ref<DfaConstValue> result = new Ref<>();
    forEach(id -> {
      DfaValue value = myFactory.getValue(id);
      if (value instanceof DfaConstValue) {
        result.set((DfaConstValue)value);
        return false;
      }
      return true;
    });
    return result.get();
  }

  boolean containsConstantsOnly() {
    int size = size();
    return size <= 1 && (size == 0 || myFactory.getValue(get(0)) instanceof DfaConstValue);
  }

}
