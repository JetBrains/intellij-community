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

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.psi.PsiType;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DfaBoxedValue extends DfaValue {
  private final @NotNull DfaVariableValue myWrappedValue;
  private final @Nullable PsiType myType;

  private DfaBoxedValue(@NotNull DfaVariableValue valueToWrap, DfaValueFactory factory, @Nullable PsiType type) {
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

  public static class Factory {
    private final TIntObjectHashMap<DfaBoxedValue> cachedValues = new TIntObjectHashMap<>();

    private final DfaValueFactory myFactory;

    public Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    public DfaBoxedValue getBoxedIfExists(DfaVariableValue variable) {
      return cachedValues.get(variable.getID());
    }

    @Nullable
    public DfaValue createBoxed(DfaValue valueToWrap, @Nullable PsiType type) {
      if (valueToWrap instanceof DfaVariableValue && ((DfaVariableValue)valueToWrap).getDescriptor() == SpecialField.UNBOX) {
        return ((DfaVariableValue)valueToWrap).getQualifier();
      }
      if (valueToWrap instanceof DfaConstValue || valueToWrap instanceof DfaFactMapValue) {
        DfaFactMap facts = DfaFactMap.EMPTY
          .with(DfaFactType.TYPE_CONSTRAINT, type == null ? null : TypeConstraint.exact(myFactory.createDfaType(type)))
          .with(DfaFactType.NULLABILITY, DfaNullability.NOT_NULL)
          .with(DfaFactType.SPECIAL_FIELD_VALUE, SpecialField.UNBOX.withValue(valueToWrap));
        return myFactory.getFactFactory().createValue(facts);
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
