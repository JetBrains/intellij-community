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

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class DfaConstValue extends DfaValue {
  private static final Throwable ourThrowable = new Throwable();
  public static class Factory {
    private final DfaConstValue dfaNull;
    private final DfaConstValue dfaFalse;
    private final DfaConstValue dfaTrue;
    private final DfaConstValue dfaFail;
    private final DfaValueFactory myFactory;
    private final Map<Object, DfaConstValue> myValues = ContainerUtil.newHashMap();

    Factory(DfaValueFactory factory) {
      myFactory = factory;
      dfaNull = new DfaConstValue(null, factory, null);
      dfaFalse = new DfaConstValue(Boolean.FALSE, factory, null);
      dfaTrue = new DfaConstValue(Boolean.TRUE, factory, null);
      dfaFail = new DfaConstValue(ourThrowable, factory, null);
    }

    @Nullable
    public DfaValue create(PsiLiteralExpression expr) {
      PsiType type = expr.getType();
      if (PsiType.NULL.equals(type)) return dfaNull;
      Object value = expr.getValue();
      if (value == null) return null;
      return createFromValue(value, type, null);
    }

    @Nullable
    public DfaValue create(PsiVariable variable) {
      Object value = variable.computeConstantValue();
      PsiType type = variable.getType();
      if (value == null) {
        Boolean boo = computeJavaLangBooleanFieldReference(variable);
        if (boo != null) {
          DfaConstValue unboxed = createFromValue(boo, PsiType.BOOLEAN, variable);
          return myFactory.getBoxedFactory().createBoxed(unboxed);
        }
        PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(variable.getInitializer());
        if (initializer instanceof PsiLiteralExpression && initializer.textMatches(PsiKeyword.NULL)) {
          return dfaNull;
        }
        if (variable instanceof PsiField && variable.hasModifier(JvmModifier.STATIC) && ExpressionUtils.isNewObject(initializer)) {
          return createFromValue(variable, type, variable);
        }
        return null;
      }
      return createFromValue(value, type, variable);
    }

    @Nullable
    private static Boolean computeJavaLangBooleanFieldReference(final PsiVariable variable) {
      if (!(variable instanceof PsiField)) return null;
      PsiClass psiClass = ((PsiField)variable).getContainingClass();
      if (psiClass == null || !CommonClassNames.JAVA_LANG_BOOLEAN.equals(psiClass.getQualifiedName())) return null;
      @NonNls String name = variable.getName();
      return "TRUE".equals(name) ? Boolean.TRUE : "FALSE".equals(name) ? Boolean.FALSE : null;
    }

    @NotNull
    public DfaConstValue createFromValue(Object value, final PsiType type, @Nullable PsiVariable constant) {
      if (value == Boolean.TRUE) return dfaTrue;
      if (value == Boolean.FALSE) return dfaFalse;
      if (value == null) return dfaNull;

      if (TypeConversionUtil.isNumericType(type) && !TypeConversionUtil.isFloatOrDoubleType(type)) {
        value = TypeConversionUtil.computeCastTo(value, PsiType.LONG);
      }
      if (value instanceof Double || value instanceof Float) {
        double doubleValue = ((Number)value).doubleValue();
        if (doubleValue == -0.0) doubleValue = +0.0;
        value = new Double(doubleValue);
      }
      DfaConstValue instance = myValues.get(value);
      if (instance == null) {
        instance = new DfaConstValue(value, myFactory, constant);
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
  }

  private final Object myValue;
  @Nullable private final PsiVariable myConstant;

  private DfaConstValue(Object value, DfaValueFactory factory, @Nullable PsiVariable constant) {
    super(factory);
    myValue = value;
    myConstant = constant;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    if (myValue == null) return "null";
    return myValue.toString();
  }

  public Object getValue() {
    return myValue;
  }

  @Nullable
  public PsiVariable getConstant() {
    return myConstant;
  }

  @Override
  public DfaValue createNegated() {
    if (this == myFactory.getConstFactory().getTrue()) return myFactory.getConstFactory().getFalse();
    if (this == myFactory.getConstFactory().getFalse()) return myFactory.getConstFactory() .getTrue();
    return DfaUnknownValue.getInstance();
  }
}
