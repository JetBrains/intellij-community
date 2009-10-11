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

import com.intellij.psi.PsiVariable;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
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
    private final Map<Object, DfaBoxedValue> cachedValues = new HashMap<Object, DfaBoxedValue>();
    private final Map<Object, DfaBoxedValue> cachedNegatedValues = new HashMap<Object, DfaBoxedValue>();
    private final DfaValueFactory myFactory;

    public Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    @Nullable
    public DfaValue createBoxed(DfaValue valueToWrap) {
      if (valueToWrap instanceof DfaUnboxedValue) return ((DfaUnboxedValue)valueToWrap).getVariable();
      Object o = valueToWrap instanceof DfaConstValue
                 ? ((DfaConstValue)valueToWrap).getValue()
                 : valueToWrap instanceof DfaVariableValue ? ((DfaVariableValue)valueToWrap).getPsiVariable() : null;
      if (o == null) return null;
      Map<Object, DfaBoxedValue> map = valueToWrap instanceof DfaVariableValue && ((DfaVariableValue)valueToWrap).isNegated() ? cachedNegatedValues : cachedValues;
      DfaBoxedValue boxedValue = map.get(o);
      if (boxedValue == null) {
        boxedValue = new DfaBoxedValue(valueToWrap, myFactory);
        map.put(o, boxedValue);
      }
      return boxedValue;
    }

    private final Map<PsiVariable, DfaUnboxedValue> cachedUnboxedValues = new THashMap<PsiVariable, DfaUnboxedValue>();
    private final Map<PsiVariable, DfaUnboxedValue> cachedNegatedUnboxedValues = new THashMap<PsiVariable, DfaUnboxedValue>();

    @NotNull
    public DfaValue createUnboxed(DfaValue value) {
      if (value instanceof DfaBoxedValue) {
        return ((DfaBoxedValue)value).getWrappedValue();
      }
      if (value instanceof DfaConstValue) {
        if (value == value.myFactory.getConstFactory().getNull()) return DfaUnknownValue.getInstance();
        return value;
      }
      DfaValue result;
      if (value instanceof DfaVariableValue) {
        PsiVariable var = ((DfaVariableValue)value).getPsiVariable();
        Map<PsiVariable, DfaUnboxedValue> map = ((DfaVariableValue)value).isNegated() ? cachedNegatedUnboxedValues : cachedUnboxedValues;
        result = map.get(var);
        if (result == null) {
          result = new DfaUnboxedValue((DfaVariableValue)value, myFactory);
          map.put(var, (DfaUnboxedValue)result);
        }
      }
      else {
        result = DfaUnknownValue.getInstance();
      }
      return result;
    }

  }
}
