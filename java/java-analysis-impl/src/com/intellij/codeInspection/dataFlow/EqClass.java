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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
class EqClass extends SortedIntSet {
  private final DfaValueFactory myFactory;

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
    for (DfaValue value : getMemberValues()) {
      if (unwrap) {
        value = DfaMemoryStateImpl.unwrap(value);
      }
      if (value instanceof DfaVariableValue) {
        vars.add((DfaVariableValue)value);
      }
    }
    return vars;
  }

  List<DfaValue> getMemberValues() {
    final List<DfaValue> result = new ArrayList<>(size());
    forEach(c1 -> {
      DfaValue value = myFactory.getValue(c1);
      result.add(value);
      return true;
    });
    return result;
  }

  @Nullable
  DfaValue findConstant(boolean wrapped) {
    for (DfaValue value : getMemberValues()) {
      if (value instanceof DfaConstValue || wrapped && DfaMemoryStateImpl.unwrap(value) instanceof DfaConstValue) {
        return value;
      }
    }
    return null;
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
