/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class DfaConstValue extends DfaValue {

  private static final Throwable ourThrowable = new Throwable();

  public static abstract class Factory {

    private final @NotNull DfaValueFactory myFactory;
    private final @NotNull DfaConstValue dfaNull;
    private final @NotNull DfaConstValue dfaFalse;
    private final @NotNull DfaConstValue dfaTrue;
    private final @NotNull DfaConstValue dfaFail;
    private final Map<Object, DfaConstValue> myValues = ContainerUtil.newHashMap();

    protected Factory(@NotNull DfaValueFactory factory) {
      myFactory = factory;
      dfaNull = new DfaConstValue(null, factory, null);
      dfaFalse = new DfaConstValue(Boolean.FALSE, factory, null);
      dfaTrue = new DfaConstValue(Boolean.TRUE, factory, null);
      dfaFail = new DfaConstValue(ourThrowable, factory, null);
    }

    @Nullable
    public DfaValue create(@Nullable PsiType type, @Nullable Object value) {
      if (PsiType.NULL.equals(type)) return dfaNull;
      if (value == null) return null;
      return createFromValue(value, type, null);
    }

    @Nullable
    public DfaValue create(@NotNull PsiVariable variable) {
      Object value = variable.computeConstantValue();
      PsiType type = variable.getType();
      if (value == null) {
        Boolean boo = computeJavaLangBooleanFieldReference(variable);
        if (boo != null) {
          DfaConstValue unboxed = createFromValue(boo, PsiType.BOOLEAN, variable);
          return myFactory.getBoxedFactory().createBoxed(unboxed);
        }
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null && initializer.getType() == PsiType.NULL && initializer.textMatches(PsiKeyword.NULL)) {
          return dfaNull;
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
    public DfaConstValue createFromValue(@Nullable Object value, final @Nullable PsiType type, @Nullable PsiVariable constant) {
      if (value == Boolean.TRUE) return dfaTrue;
      if (value == Boolean.FALSE) return dfaFalse;

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

    @NotNull
    public DfaConstValue getContractFail() {
      return dfaFail;
    }

    @NotNull
    public DfaConstValue getFalse() {
      return dfaFalse;
    }

    @NotNull
    public DfaConstValue getTrue() {
      return dfaTrue;
    }

    @NotNull
    public DfaConstValue getNull() {
      return dfaNull;
    }
  }

  private final @NotNull DfaValueFactory myFactory;
  private final @Nullable Object myValue;
  private final @Nullable PsiVariable myConstant;

  public DfaConstValue(@Nullable Object value, @NotNull DfaValueFactory factory, @Nullable PsiVariable constant) {
    super(factory);
    myFactory = factory;
    myValue = value;
    myConstant = constant;
  }

  @Override
  public String toString() {
    if (myValue == null) return "null";
    return myValue.toString();
  }

  @Nullable
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
