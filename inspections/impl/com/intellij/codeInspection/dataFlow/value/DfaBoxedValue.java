package com.intellij.codeInspection.dataFlow.value;

import com.intellij.psi.PsiVariable;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;

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
    private final DfaValueFactory myFactory;

    public Factory(DfaValueFactory factory) {
      myFactory = factory;
    }

    public DfaBoxedValue createBoxed(DfaValue valueToWrap) {
      DfaBoxedValue boxedValue;
      if (isValueTooLargeToCacheInPrimitiveWrapper(valueToWrap)) {
        boxedValue = new DfaBoxedValue(valueToWrap, myFactory);
      }
      else {
        Object o = valueToWrap instanceof DfaConstValue ? ((DfaConstValue)valueToWrap).getValue()
                   : valueToWrap instanceof DfaVariableValue ? ((DfaVariableValue)valueToWrap).getPsiVariable() : null;
        if (o == null) return null;
        boxedValue = cachedValues.get(o);
        if (boxedValue == null) {
          boxedValue = new DfaBoxedValue(valueToWrap, myFactory);
          cachedValues.put(o, boxedValue);
        }
      }
      return boxedValue;
    }
    private final Map<PsiVariable, DfaUnboxedValue> cachedUnboxedValues = new THashMap<PsiVariable, DfaUnboxedValue>();

    public DfaValue createUnboxed(DfaValue valueToWrap) {
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

    private static boolean isValueTooLargeToCacheInPrimitiveWrapper(final DfaValue dfaValue) {
      if (!(dfaValue instanceof DfaConstValue)) return false;
      Object value = ((DfaConstValue)dfaValue).getValue();
      return box(value) != box(value);
    }

    private static Object box(final Object value) {
      Object newBoxedValue = null;
      if (value instanceof Integer) newBoxedValue = Integer.valueOf(((Integer)value).intValue());
      if (value instanceof Byte) newBoxedValue = Byte.valueOf(((Byte)value).byteValue());
      if (value instanceof Short) newBoxedValue = Short.valueOf(((Short)value).shortValue());
      if (value instanceof Long) newBoxedValue = Long.valueOf(((Long)value).longValue());
      if (value instanceof Boolean) newBoxedValue = Boolean.valueOf(((Boolean)value).booleanValue());
      if (value instanceof Character) newBoxedValue = Character.valueOf(((Character)value).charValue());
      return newBoxedValue;
    }

  }
}
