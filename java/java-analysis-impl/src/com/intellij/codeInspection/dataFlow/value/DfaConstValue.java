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
  public static class Factory {
    private final DfaConstValue dfaNull;
    private final DfaConstValue dfaFalse;
    private final DfaConstValue dfaTrue;
    private final DfaValueFactory myFactory;
    private final Map<Object, DfaConstValue> myValues = ContainerUtil.newHashMap();

    Factory(DfaValueFactory factory) {
      myFactory = factory;
      dfaNull = new DfaConstValue(null, factory, null);
      dfaFalse = new DfaConstValue(Boolean.FALSE, factory, null);
      dfaTrue = new DfaConstValue(Boolean.TRUE, factory, null);
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

      if (TypeConversionUtil.isNumericType(type) && !TypeConversionUtil.isFloatOrDoubleType(type)) {
        value = TypeConversionUtil.computeCastTo(value, PsiType.LONG);
      }
      Object key = constant != null ? constant : value;
      DfaConstValue instance = myValues.get(key);
      if (instance == null) {
        instance = new DfaConstValue(value, myFactory, constant);
        myValues.put(key, instance);
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
