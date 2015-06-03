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

import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class DfaConstValue extends DfaValue {

  private static final Throwable ourThrowable = new Throwable();

  public static abstract class Factory {

    private final DfaValueFactory myFactory;
    protected final DfaConstValue dfaNull;
    protected final DfaConstValue dfaFalse;
    protected final DfaConstValue dfaTrue;
    protected final DfaConstValue dfaFail;
    protected final Map<Object, DfaConstValue> myValues = ContainerUtil.newHashMap();

    protected Factory(DfaValueFactory factory) {
      myFactory = factory;
      dfaNull = new DfaConstValue(null, factory, null);
      dfaFalse = new DfaConstValue(Boolean.FALSE, factory, null);
      dfaTrue = new DfaConstValue(Boolean.TRUE, factory, null);
      dfaFail = new DfaConstValue(ourThrowable, factory, null);
    }

    @Nullable
    public DfaValue create(PsiType type, Object value) {
      if (PsiType.NULL.equals(type)) return dfaNull;
      if (value == null) return null;
      return createFromValue(value, type, null);
    }

    @Nullable
    public abstract DfaValue create(PsiVariable variable);
    
    @NotNull
    public DfaConstValue createFromValue(Object value, final PsiType type, @Nullable PsiVariable constant) {
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

  private final DfaValueFactory myFactory;
  private final Object myValue;
  private final @Nullable PsiVariable myConstant;

  public DfaConstValue(Object value, DfaValueFactory factory, @Nullable PsiVariable constant) {
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
