/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.SpecialField;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class DfaBoxedValue extends DfaValue {
  private final DfaValue myWrappedValue;
  private final @Nullable PsiType myType;

  private DfaBoxedValue(DfaValue valueToWrap, DfaValueFactory factory, @Nullable PsiType type) {
    super(factory);
    myWrappedValue = valueToWrap;
    myType = type;
  }

  @NonNls
  public String toString() {
    return "Boxed "+myWrappedValue.toString();
  }

  public DfaValue getWrappedValue() {
    return myWrappedValue;
  }

  @Nullable
  @Override
  public PsiType getType() {
    return myType;
  }

  public static class Factory {
    private final Map<Object, DfaBoxedValue> cachedValues = new HashMap<>();

    private final DfaValueFactory myFactory;

    public Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    public DfaValue getBoxedIfExists(DfaVariableValue variable) {
      return cachedValues.get(variable);
    }

    @Nullable
    public DfaValue createBoxed(DfaValue valueToWrap, @Nullable PsiType type) {
      if (valueToWrap instanceof DfaVariableValue && ((DfaVariableValue)valueToWrap).getSource() == SpecialField.UNBOX) {
        return ((DfaVariableValue)valueToWrap).getQualifier();
      }
      Object o = valueToWrap instanceof DfaConstValue
                 ? ((DfaConstValue)valueToWrap).getValue()
                 : valueToWrap instanceof DfaVariableValue ? valueToWrap : null;
      if (o == null) return null;
      DfaBoxedValue boxedValue = cachedValues.get(o);
      if (boxedValue == null) {
        cachedValues.put(o, boxedValue = new DfaBoxedValue(valueToWrap, myFactory, type));
      }
      return boxedValue;
    }

    @NotNull
    public DfaValue createUnboxed(DfaValue value, PsiPrimitiveType targetType) {
      if (value instanceof DfaBoxedValue) {
        return ((DfaBoxedValue)value).getWrappedValue();
      }
      if (value instanceof DfaConstValue) {
        return TypeConversionUtil.isPrimitiveAndNotNull(((DfaConstValue)value).getType()) ? value : DfaUnknownValue.getInstance();
      }
      if (value instanceof DfaVariableValue) {
        return SpecialField.UNBOX.createValue(myFactory, value, targetType);
      }
      return DfaUnknownValue.getInstance();
    }

  }
}
