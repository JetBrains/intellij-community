// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.SpecialField;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

/**
 * A reference value whose SpecialField value is equal to some variable.
 * Exists on the stack only.
 */
public final class DfaWrappedValue extends DfaValue {
  private final @NotNull DfaVariableValue myWrappedValue;
  private final @NotNull SpecialField mySpecialField;
  private final @NotNull DfType myType;

  private DfaWrappedValue(@NotNull DfaVariableValue valueToWrap,
                          @NotNull SpecialField field,
                          @NotNull DfType type) {
    super(valueToWrap.getFactory());
    myWrappedValue = valueToWrap;
    mySpecialField = field;
    myType = type;
  }

  @NonNls
  public String toString() {
    return myType + " [with " + mySpecialField + "=" + myWrappedValue + "]";
  }

  @NotNull
  public DfaVariableValue getWrappedValue() {
    return myWrappedValue;
  }

  @NotNull
  public SpecialField getSpecialField() {
    return mySpecialField;
  }

  @Nullable
  @Override
  public PsiType getType() {
    return DfaTypeValue.toPsiType(myFactory.getProject(), myType);
  }

  @NotNull
  @Override
  public DfType getDfType() {
    return myType;
  }

  public static class Factory {
    private final Map<List<?>, DfaWrappedValue> cachedValues = new HashMap<>();

    private final DfaValueFactory myFactory;

    public Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    @NotNull
    public DfaValue createWrapper(@NotNull DfType qualifierType, @NotNull SpecialField specialField, @NotNull DfaValue specialFieldValue) {
      if (specialFieldValue instanceof DfaVariableValue && ((DfaVariableValue)specialFieldValue).getDescriptor() == specialField) {
        DfaVariableValue qualifier = ((DfaVariableValue)specialFieldValue).getQualifier();
        if (qualifier != null && qualifierType.isSuperType(qualifier.getDfType())) {
          return qualifier;
        }
      }
      if (specialFieldValue instanceof DfaTypeValue) {
        DfType dfType;
        DfType fieldValue = specialFieldValue.getDfType();
        if (specialField == SpecialField.STRING_LENGTH && fieldValue.isConst(0)) {
          dfType = DfTypes.constant("", JavaPsiFacade.getElementFactory(specialFieldValue.getFactory().getProject())
            .createTypeByFQClassName(JAVA_LANG_STRING));
        }
        else {
          dfType = qualifierType.meet(specialField.asDfType(fieldValue));
        }
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
