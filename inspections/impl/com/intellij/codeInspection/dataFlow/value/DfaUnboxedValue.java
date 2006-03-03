package com.intellij.codeInspection.dataFlow.value;

import com.intellij.psi.PsiVariable;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;

import java.util.Map;

public class DfaUnboxedValue extends DfaValue {
  private final DfaVariableValue myVariable;

  DfaUnboxedValue(DfaVariableValue valueToWrap, DfaValueFactory factory) {
    super(factory);
    myVariable = valueToWrap;
  }

  @NonNls
  public String toString() {
    return "Unboxed "+myVariable.toString();
  }

  public DfaValue getVariable() {
    return myVariable;
  }

  public static class Factory {
    private final Map<PsiVariable, DfaUnboxedValue> cachedUnboxedValues = new THashMap<PsiVariable, DfaUnboxedValue>();
    private final DfaValueFactory myFactory;

    public Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    public DfaValue create(DfaValue valueToWrap) {
      if (valueToWrap instanceof DfaBoxedValue) {
        return ((DfaBoxedValue)valueToWrap).getWrappedValue();
      }
      DfaValue result;
      if (valueToWrap instanceof DfaVariableValue) {
        PsiVariable var = ((DfaVariableValue)valueToWrap).getPsiVariable();
        result = cachedUnboxedValues.get(var);
        if (result == null) {
          result = new DfaUnboxedValue((DfaVariableValue)valueToWrap, myFactory);
          cachedUnboxedValues.put(var, (DfaUnboxedValue)result);
        }
      }
      else {
        result = DfaUnknownValue.getInstance();
      }
      return result;
    }
  }
}
