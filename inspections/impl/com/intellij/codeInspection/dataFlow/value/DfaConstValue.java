/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 6:31:23 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.HashMap;

import java.util.Map;

public class DfaConstValue extends DfaValue {
  public static class Factory {
    private DfaConstValue dfaNull;
    private DfaConstValue dfaFalse;
    private DfaConstValue dfaTrue;
    private final Map<Object, DfaConstValue> myValues;
    private DfaValueFactory myFactory;

    Factory(DfaValueFactory factory) {
      myFactory = factory;
      myValues = new HashMap<Object, DfaConstValue>();
      dfaNull = new DfaConstValue(null, factory);
      dfaFalse = new DfaConstValue(Boolean.FALSE, factory);
      dfaTrue = new DfaConstValue(Boolean.TRUE, factory);
    }

    public DfaConstValue create(PsiLiteralExpression expr) {
      PsiType type = expr.getType();
      if (type == PsiType.NULL) return dfaNull;
      Object value = expr.getValue();
      if (value == null) return null;
      return createFromValue(value, type);
    }

    public DfaConstValue create(PsiVariable variable) {
      Object value = variable.computeConstantValue();
      if (value == null) return null;
      return createFromValue(value, variable.getType());
    }

    public DfaConstValue createFromValue(Object value, final PsiType type) {
      if (value == Boolean.TRUE) return dfaTrue;
      if (value == Boolean.FALSE) return dfaFalse;

      if (TypeConversionUtil.isNumericType(type)) {
        value = TypeConversionUtil.computeCastTo(value, PsiType.DOUBLE);
      }
      DfaConstValue instance = myValues.get(value);
      if (instance == null) {
        instance = new DfaConstValue(value, myFactory);
        myValues.put(value, instance);
      }

      return instance;
    }

    public DfaConstValue getFalse() {
      return dfaFalse;
    }

    public DfaConstValue getTrue() {
      return dfaTrue;
    }

    public DfaConstValue getNull() {
      return dfaNull;
    }
  }

  private Object myValue;

  DfaConstValue(Object value, DfaValueFactory factory) {
    super(factory);
    myValue = value;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    if (myValue == null) return "null";
    return myValue.toString();
  }

  public Object getValue() {
    return myValue;
  }

  public DfaValue createNegated() {
    if (this == myFactory.getConstFactory().getTrue()) return myFactory.getConstFactory().getFalse();
    if (this == myFactory.getConstFactory().getFalse()) return myFactory.getConstFactory() .getTrue();
    return DfaUnknownValue.getInstance();
  }
}
