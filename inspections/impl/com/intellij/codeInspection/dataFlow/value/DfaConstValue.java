/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 6:31:23 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class DfaConstValue extends DfaValue {
  public static class Factory {
    private final DfaConstValue dfaNull;
    private final DfaConstValue dfaFalse;
    private final DfaConstValue dfaTrue;
    private final DfaValueFactory myFactory;
    private final Map<Object, DfaConstValue> myValues;

    Factory(DfaValueFactory factory) {
      myFactory = factory;
      myValues = new HashMap<Object, DfaConstValue>();
      dfaNull = new DfaConstValue(null, factory);
      dfaFalse = new DfaConstValue(Boolean.FALSE, factory);
      dfaTrue = new DfaConstValue(Boolean.TRUE, factory);
    }

    @Nullable
    public DfaValue create(PsiLiteralExpression expr) {
      PsiType type = expr.getType();
      if (type == PsiType.NULL) return dfaNull;
      Object value = expr.getValue();
      if (value == null) return null;
      return createFromValue(value, type);
    }

    @Nullable
    public DfaValue create(PsiVariable variable) {
      Object value = variable.computeConstantValue();
      PsiType type = variable.getType();
      if (value == null) {
        Boolean boo = computeJavaLangBooleanFieldReference(variable);
        if (boo != null) {
          DfaConstValue unboxed = createFromValue(boo, PsiType.BOOLEAN);
          return myFactory.getBoxedFactory().createBoxed(unboxed);
        }
        return null;
      }
      return createFromValue(value, type);
    }

    @Nullable
    private static Boolean computeJavaLangBooleanFieldReference(final PsiVariable variable) {
      if (!(variable instanceof PsiField)) return null;
      PsiClass psiClass = ((PsiField)variable).getContainingClass();
      if (psiClass == null || !"java.lang.Boolean".equals(psiClass.getQualifiedName())) return null;
      @NonNls String name = variable.getName();
      return "TRUE".equals(name) ? Boolean.TRUE : "FALSE".equals(name) ? Boolean.FALSE : null;
    }

    @NotNull
    public DfaConstValue createFromValue(Object value, final PsiType type) {
      if (value == Boolean.TRUE) return dfaTrue;
      if (value == Boolean.FALSE) return dfaFalse;

      if (TypeConversionUtil.isNumericType(type)) {
        if (type == PsiType.DOUBLE || type == PsiType.FLOAT) {
          //double dbVal = type == PsiType.DOUBLE ? ((Double)value).doubleValue() : ((Float)value).doubleValue();
          //// 5.0f == 5
          //if (Math.floor(dbVal) == dbVal) value = TypeConversionUtil.computeCastTo(value, PsiType.LONG);
        }
        else {
          value = TypeConversionUtil.computeCastTo(value, PsiType.LONG);
        }
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
