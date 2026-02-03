// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.types.DfType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A reference value whose SpecialField value is equal to some variable.
 * Exists on the stack only.
 */
public final class DfaWrappedValue extends DfaValue {
  private final @NotNull DfaVariableValue myWrappedValue;
  private final @NotNull DerivedVariableDescriptor myDerivedVariableDescriptor;
  private final @NotNull DfType myType;

  private DfaWrappedValue(@NotNull DfaVariableValue valueToWrap,
                          @NotNull DerivedVariableDescriptor field,
                          @NotNull DfType type) {
    super(valueToWrap.getFactory());
    myWrappedValue = valueToWrap;
    myDerivedVariableDescriptor = field;
    myType = type;
  }

  @Override
  public @NonNls String toString() {
    return myType + " [with " + myDerivedVariableDescriptor + "=" + myWrappedValue + "]";
  }

  public @NotNull DfaVariableValue getWrappedValue() {
    return myWrappedValue;
  }

  public @NotNull DerivedVariableDescriptor getSpecialField() {
    return myDerivedVariableDescriptor;
  }

  @Override
  public DfaValue bindToFactory(@NotNull DfaValueFactory factory) {
    return factory.getWrapperFactory().createWrapper(myType, myDerivedVariableDescriptor, myWrappedValue.bindToFactory(factory));
  }

  @Override
  public boolean dependsOn(DfaVariableValue other) {
    return myWrappedValue.dependsOn(other);
  }

  @Override
  public @NotNull DfType getDfType() {
    return myType;
  }

  public static class Factory {
    private final Map<List<?>, DfaWrappedValue> cachedValues = new HashMap<>();

    private final DfaValueFactory myFactory;

    public Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    public @NotNull DfaValue createWrapper(@NotNull DfType qualifierType, @NotNull DerivedVariableDescriptor specialField, @NotNull DfaValue specialFieldValue) {
      if (specialFieldValue instanceof DfaVariableValue && ((DfaVariableValue)specialFieldValue).getDescriptor() == specialField) {
        DfaVariableValue qualifier = ((DfaVariableValue)specialFieldValue).getQualifier();
        if (qualifier != null && qualifierType.isSuperType(qualifier.getDfType())) {
          return qualifier;
        }
      }
      if (specialFieldValue instanceof DfaTypeValue || specialFieldValue instanceof DfaWrappedValue) {
        DfType fieldValue = specialFieldValue.getDfType();
        DfType dfType = specialField.asDfType(qualifierType, fieldValue);
        return myFactory.fromDfType(dfType);
      }
      if (specialFieldValue instanceof DfaVariableValue) {
        return cachedValues.computeIfAbsent(Arrays.asList(specialFieldValue, specialField, qualifierType),
                                            k -> new DfaWrappedValue((DfaVariableValue)specialFieldValue, specialField, qualifierType));
      }
      return myFactory.fromDfType(qualifierType);
    }
  }
}
