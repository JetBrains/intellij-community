// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java;

import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfPrimitiveType;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.DfaWrappedValue;
import com.intellij.psi.CommonClassNames;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * Utility class to help interpreting the Java DFA
 */
public class JavaDfaHelpers {
  public static DfaValue dropLocality(DfaValue value, DfaMemoryState state) {
    if (!(value instanceof DfaVariableValue)) {
      DfType type = value.getDfType();
      if (type.isLocal() && type instanceof DfReferenceType) {
        DfReferenceType dfType = ((DfReferenceType)type).dropLocality();
        if (value instanceof DfaWrappedValue) {
          return value.getFactory().getWrapperFactory()
            .createWrapper(dfType, ((DfaWrappedValue)value).getSpecialField(), ((DfaWrappedValue)value).getWrappedValue());
        }
        return value.getFactory().fromDfType(dfType);
      }
      return value;
    }
    DfaVariableValue var = (DfaVariableValue)value;
    DfType dfType = state.getDfType(var);
    if (dfType instanceof DfReferenceType) {
      state.setDfType(var, ((DfReferenceType)dfType).dropLocality());
    }
    for (DfaVariableValue v : new ArrayList<>(var.getDependentVariables())) {
      dfType = state.getDfType(v);
      if (dfType instanceof DfReferenceType) {
        state.setDfType(v, ((DfReferenceType)dfType).dropLocality());
      }
    }
    return value;
  }

  public static boolean mayLeakFromType(@NotNull DfType type) {
    if (type == DfType.BOTTOM) return false;
    // Complex value from field or method return call may contain back-reference to the object, so
    // local value could leak. Do not drop locality only for some simple values.
    while (true) {
      TypeConstraint constraint = TypeConstraint.fromDfType(type);
      DfType arrayComponentType = constraint.getArrayComponentType();
      if (arrayComponentType == DfType.BOTTOM) {
        return !(type instanceof DfPrimitiveType) && !constraint.isExact(CommonClassNames.JAVA_LANG_STRING);
      }
      type = arrayComponentType;
    }
  }
}
