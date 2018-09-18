/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
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
    List<DfaVariableValue> vars = ContainerUtil.newArrayList();
    forEach(id -> {
      DfaValue value = myFactory.getValue(id);
      if (unwrap) {
        value = DfaMemoryStateImpl.unwrap(value);
      }
      if (value instanceof DfaVariableValue) {
        vars.add((DfaVariableValue)value);
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
  DfaValue findConstant(boolean wrapped) {
    Ref<DfaValue> result = new Ref<>();
    forEach(id -> {
      DfaValue value = myFactory.getValue(id);
      if (value instanceof DfaConstValue || wrapped && DfaMemoryStateImpl.unwrap(value) instanceof DfaConstValue) {
        result.set(value);
        return false;
      }
      return true;
    });
    return result.get();
  }

  @Nullable
  private static DfaConstValue asConstantValue(DfaValue value) {
    value = DfaMemoryStateImpl.unwrap(value);
    return value instanceof DfaConstValue ? (DfaConstValue)value : null;
  }

  boolean containsConstantsOnly() {
    for (int i = 0; i < size(); i++) {
      if (asConstantValue(myFactory.getValue(get(i))) == null) {
        return false;
      }
    }

    return true;
  }

}
