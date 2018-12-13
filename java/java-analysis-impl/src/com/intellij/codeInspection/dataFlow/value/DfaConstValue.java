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

import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class DfaConstValue extends DfaValue {
  private static final Throwable ourThrowable = new Throwable();
  private static final Object SENTINEL = ObjectUtils.sentinel("SENTINEL");
  public static class Factory {
    private final DfaConstValue dfaNull;
    private final DfaConstValue dfaFalse;
    private final DfaConstValue dfaTrue;
    private final DfaConstValue dfaFail;
    private final DfaConstValue dfaSentinel;
    private final DfaValueFactory myFactory;
    private final Map<Object, DfaConstValue> myValues = ContainerUtil.newHashMap();

    Factory(DfaValueFactory factory) {
      myFactory = factory;
      dfaNull = new DfaConstValue(null, PsiType.NULL, factory);
      dfaFalse = new DfaConstValue(Boolean.FALSE, PsiType.BOOLEAN, factory);
      dfaTrue = new DfaConstValue(Boolean.TRUE, PsiType.BOOLEAN, factory);
      dfaFail = new DfaConstValue(ourThrowable, PsiType.VOID, factory);
      dfaSentinel = new DfaConstValue(SENTINEL, PsiType.VOID, factory);
    }

    @Nullable
    public DfaValue create(PsiLiteralExpression expr) {
      PsiType type = expr.getType();
      if (type == null) return null;
      if (PsiType.NULL.equals(type)) return dfaNull;
      Object value = expr.getValue();
      if (value == null) return null;
      return createFromValue(value, type);
    }

    @Nullable
    public DfaValue create(PsiVariable variable) {
      if (DfaUtil.ignoreInitializer(variable)) return null;
      Object value = variable.computeConstantValue();
      PsiType type = variable.getType();
      if (value == null) {
        Boolean boo = computeJavaLangBooleanFieldReference(variable);
        if (boo != null) {
          DfaConstValue unboxed = createFromValue(boo, PsiType.BOOLEAN);
          return myFactory.getBoxedFactory().createBoxed(unboxed, PsiType.BOOLEAN.getBoxedType(variable));
        }
        PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(variable.getInitializer());
        if (initializer instanceof PsiLiteralExpression && initializer.textMatches(PsiKeyword.NULL)) {
          return dfaNull;
        }
        if (variable instanceof PsiField && variable.hasModifierProperty(PsiModifier.STATIC) && ExpressionUtils.isNewObject(initializer)) {
          return createFromValue(variable, type);
        }
        return null;
      }
      return createFromValue(value, type);
    }

    @Nullable
    private static Boolean computeJavaLangBooleanFieldReference(final PsiVariable variable) {
      if (!(variable instanceof PsiField)) return null;
      PsiClass psiClass = ((PsiField)variable).getContainingClass();
      if (psiClass == null || !CommonClassNames.JAVA_LANG_BOOLEAN.equals(psiClass.getQualifiedName())) return null;
      @NonNls String name = variable.getName();
      return "TRUE".equals(name) ? Boolean.TRUE : "FALSE".equals(name) ? Boolean.FALSE : null;
    }

    /**
     * Creates a constant which corresponds to the default value of given type
     *
     * @param type type to get the default value for
     * @return a constant (e.g. 0 from int, false for boolean, null for reference type).
     */
    @NotNull
    public DfaConstValue createDefault(@NotNull PsiType type) {
      return createFromValue(PsiTypesUtil.getDefaultValue(type), type);
    }

    @NotNull
    public DfaConstValue createFromValue(Object value, @NotNull PsiType type) {
      if (Boolean.TRUE.equals(value)) return dfaTrue;
      if (Boolean.FALSE.equals(value)) return dfaFalse;
      if (value == null) return dfaNull;

      if (TypeConversionUtil.isNumericType(type) && !TypeConversionUtil.isFloatOrDoubleType(type)) {
        type = PsiType.LONG;
        Object numeric = TypeConversionUtil.computeCastTo(value, type);
        if (numeric != null) {
          value = numeric;
        }
      }
      if (value instanceof Float) {
        value = ((Float)value).doubleValue();
      }
      DfaConstValue instance = myValues.get(value);
      if (instance == null) {
        instance = new DfaConstValue(value, type, myFactory);
        myValues.put(value, instance);
      }

      return instance;
    }

    public DfaConstValue getContractFail() {
      return dfaFail;
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

    /**
     * Sentinel value is special value used internally by dataflow. It cannot be stored to any variable, and equals to itself only
     * @return sentinel value
     */
    public DfaConstValue getSentinel() {
      return dfaSentinel;
    }
  }

  private final Object myValue;
  @NotNull private final PsiType myType;

  private DfaConstValue(Object value, @NotNull PsiType type, DfaValueFactory factory) {
    super(factory);
    myValue = value;
    myType = type;
  }

  public String toString() {
    if (myValue == null) return "null";
    return myValue.toString();
  }

  @Override
  @NotNull
  public PsiType getType() {
    return myType;
  }

  public Object getValue() {
    return myValue;
  }

  @Override
  public DfaValue createNegated() {
    if (this == myFactory.getConstFactory().getTrue()) return myFactory.getConstFactory().getFalse();
    if (this == myFactory.getConstFactory().getFalse()) return myFactory.getConstFactory() .getTrue();
    return DfaUnknownValue.getInstance();
  }

  /**
   * Checks whether given value is a special value representing method failure, according to its contract
   *
   * @param value value to check
   * @return true if specified value represents method failure
   */
  @Contract("null -> false")
  public static boolean isContractFail(DfaValue value) {
    return value instanceof DfaConstValue && ((DfaConstValue)value).getValue() == ourThrowable;
  }
}
