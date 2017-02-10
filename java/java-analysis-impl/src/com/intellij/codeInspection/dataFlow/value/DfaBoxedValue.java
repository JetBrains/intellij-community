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

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class DfaBoxedValue extends DfaValue {
  private final DfaValue myWrappedValue;

  private DfaBoxedValue(DfaValue valueToWrap, DfaValueFactory factory) {
    super(factory);
    myWrappedValue = valueToWrap;
  }

  @NonNls
  public String toString() {
    return "Boxed "+myWrappedValue.toString();
  }

  public DfaValue getWrappedValue() {
    return myWrappedValue;
  }

  public static class Factory {
    private final Map<Object, DfaBoxedValue> cachedValues = new HashMap<>();
    private final DfaValueFactory myFactory;

    public Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    @Nullable
    public DfaValue createBoxed(DfaValue valueToWrap) {
      if (valueToWrap instanceof DfaUnboxedValue) return ((DfaUnboxedValue)valueToWrap).getVariable();
      Object o = valueToWrap instanceof DfaConstValue
                 ? ((DfaConstValue)valueToWrap).getValue()
                 : valueToWrap instanceof DfaVariableValue ? valueToWrap : null;
      if (o == null) return null;
      DfaBoxedValue boxedValue = cachedValues.get(o);
      if (boxedValue == null) {
        cachedValues.put(o, boxedValue = new DfaBoxedValue(valueToWrap, myFactory));
      }
      return boxedValue;
    }

    private final Map<DfaVariableValue, DfaUnboxedValue> cachedUnboxedValues = ContainerUtil.newTroveMap();

    @NotNull
    public DfaValue createUnboxed(DfaValue value) {
      if (value instanceof DfaBoxedValue) {
        return ((DfaBoxedValue)value).getWrappedValue();
      }
      if (value instanceof DfaConstValue) {
        if (value == value.myFactory.getConstFactory().getNull()) return DfaUnknownValue.getInstance();
        return value;
      }
      if (value instanceof DfaVariableValue) {
        DfaVariableValue var = (DfaVariableValue)value;
        DfaUnboxedValue result = cachedUnboxedValues.get(var);
        if (result == null) {
          cachedUnboxedValues.put(var, result = new DfaUnboxedValue(var, myFactory));
        }
        return result;
      }
      return DfaUnknownValue.getInstance();
    }

  }
}
