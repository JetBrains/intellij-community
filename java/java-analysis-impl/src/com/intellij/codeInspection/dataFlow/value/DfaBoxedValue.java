// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.SpecialField;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.psi.PsiType;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DfaBoxedValue extends DfaValue {
  private final @NotNull DfaVariableValue myWrappedValue;
  private final @Nullable PsiType myType;

  private DfaBoxedValue(@NotNull DfaVariableValue valueToWrap, @NotNull DfaValueFactory factory, @Nullable PsiType type) {
    super(factory);
    myWrappedValue = valueToWrap;
    myType = type;
  }

  @NonNls
  public String toString() {
    return "Boxed "+myWrappedValue.toString();
  }

  @NotNull
  public DfaVariableValue getWrappedValue() {
    return myWrappedValue;
  }

  @Nullable
  @Override
  public PsiType getType() {
    return myType;
  }

  @NotNull
  @Override
  public DfType getDfType() {
    return DfTypes.typedObject(myType, Nullability.NOT_NULL);
  }

  public static class Factory {
    private final TIntObjectHashMap<DfaBoxedValue> cachedValues = new TIntObjectHashMap<>();

    private final DfaValueFactory myFactory;

    public Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    @Nullable
    public DfaValue createBoxed(DfaValue valueToWrap, @Nullable PsiType type) {
      if (valueToWrap instanceof DfaVariableValue && ((DfaVariableValue)valueToWrap).getDescriptor() == SpecialField.UNBOX) {
        DfaVariableValue qualifier = ((DfaVariableValue)valueToWrap).getQualifier();
        if (qualifier != null && (type == null || type.equals(qualifier.getType()))) {
          return qualifier;
        }
      }
      if (valueToWrap instanceof DfaTypeValue) {
        DfType dfType = SpecialField.UNBOX.asDfType(valueToWrap.getDfType(), type);
        return myFactory.fromDfType(dfType);
      }
      if (valueToWrap instanceof DfaVariableValue) {
        int id = valueToWrap.getID();
        DfaBoxedValue boxedValue = cachedValues.get(id);
        if (boxedValue == null) {
          cachedValues.put(id, boxedValue = new DfaBoxedValue((DfaVariableValue)valueToWrap, myFactory, type));
        }
        return boxedValue;
      }
      return null;
    }
  }
}
