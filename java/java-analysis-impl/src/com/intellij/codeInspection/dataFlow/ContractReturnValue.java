// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Representation of {@link MethodContract} return value. It may represent the concrete value (e.g. "false") or pose some constraints
 * to the method return value (e.g. "!null").
 */
public abstract class ContractReturnValue {
  private final @NotNull String myName;
  private final int myOrdinal;

  private ContractReturnValue(@NotNull String name, int ordinal) {
    myName = name;
    myOrdinal = ordinal;
  }

  /**
   * @return a hashcode which is stable across VM restart
   */
  @Override
  public int hashCode() {
    return myOrdinal;
  }

  /**
   * @return a string which is used to represent this return value inside {@link org.jetbrains.annotations.Contract} annotation.
   */
  @Override
  public String toString() {
    return myName;
  }

  /**
   * Checks whether this return value makes sense for the supplied return type. E.g. "true" contract value makes sense for {@code boolean}
   * return type, but does not make sense for {@code int} return type. This method can be used to check the contract correctness.
   *
   * @param returnType return type to check
   * @return true if this contract return value makes sense for the supplied return type.
   */
  public abstract boolean isReturnTypeCompatible(@Nullable PsiType returnType);

  /**
   * Converts this return value to the most suitable {@link DfaValue} which represents the same constraints.
   *
   * @param factory a {@link DfaValueFactory} which can be used to create new values if necessary
   * @param defaultValue a default method return type value in the absence of the contracts (may contain method type information)
   * @return a value which represents the constraints of this contract return value.
   */
  public abstract DfaValue toDfaValue(DfaValueFactory factory, DfaValue defaultValue);

  /**
   * Returns true if the supplied {@link DfaValue} could be compatible with this return value. If false is returned, then
   * returning given {@link DfaValue} would violate the contract with this return value. This method can be used
   * to check the contract correctness.
   *
   * @param state memory state to use
   * @param value value to check
   * @return whether the supplied value could be compatible with this return value.
   */
  public abstract boolean isValueCompatible(DfaMemoryState state, DfaValue value);

  /**
   * Returns a unique non-negative number which identifies this return value. Can be used for serialization. For two return values
   * {@code a} and {@code b} the following holds:
   * {@code a.ordinal() == b.ordinal() <=> a.equals(b)}.
   *
   * @return a unique non-negative number which identifies this return value.
   */
  public int ordinal() {
    return myOrdinal;
  }

  /**
   * @return true if this return value represents a non-null object value (possibly with some other restrictions)
   */
  public boolean isNotNull() {
    return false;
  }

  /**
   * @return true if this return value represents a null
   */
  public boolean isNull() {
    return this == NULL_VALUE;
  }

  /**
   * @return true if this return value represents a failure (that is, method must throw to fulfill this contract)
   */
  public boolean isFail() {
    return this == FAIL_VALUE;
  }

  /**
   * @return true if this return value represents a boolean value (either "true" or "false")
   */
  public boolean isBoolean() {
    return this instanceof BooleanReturnValue;
  }

  /**
   * Returns a {@code ContractReturnValue} which corresponds to given ordinal index. For any return value {@code x}
   * the following holds: {@code ContractReturnValue.valueOf(x.ordinal()).equals(x)}.
   *
   * @param ordinal ordinal to create a ContractReturnValue object from
   * @return a ContractReturnValue object. Returns an object which represents any possible value if the supplied ordinal does not
   * correspond to any valid ContractReturnValue.
   */
  @NotNull
  public static ContractReturnValue valueOf(int ordinal) {
    switch (ordinal) {
      case 0:
      default:
        return returnAny();
      case 1:
        return returnNull();
      case 2:
        return returnNotNull();
      case 3:
        return returnTrue();
      case 4:
        return returnFalse();
      case 5:
        return fail();
    }
  }

  /**
   * Returns a {@code ContractReturnValue} which corresponds to given string representation. For any return value {@code x}
   * the following holds: {@code ContractReturnValue.valueOf(x.toString()).equals(x)} and for string {@code str} the following holds:
   * {@code ContractReturnValue.valueOf(str) == null || ContractReturnValue.valueOf(str).toString().equals(str)}.
   *
   * @param value string representation of return value
   * @return ContractReturnValue object which corresponds to given string representation; null if given value is not supported.
   */
  @Nullable
  public static ContractReturnValue valueOf(@NotNull String value) {
    switch (value) {
      case "_":
        return returnAny();
      case "fail":
        return fail();
      case "true":
        return returnTrue();
      case "false":
        return returnFalse();
      case "null":
        return returnNull();
      case "!null":
        return returnNotNull();
    }
    return null;
  }

  /**
   * @return any possible return value ("top" element)
   */
  public static ContractReturnValue returnAny() {
    return ANY_VALUE;
  }

  /**
   * @return return value indicating that the method throws an exception ("bottom" element)
   */
  public static ContractReturnValue fail() {
    return FAIL_VALUE;
  }

  /**
   * @param value a boolean value to return
   * @return the corresponding boolean return value
   */
  public static BooleanReturnValue returnBoolean(boolean value) {
    return value ? returnTrue() : returnFalse();
  }

  /**
   * @return boolean "true" return value
   */
  public static BooleanReturnValue returnTrue() {
    return BooleanReturnValue.TRUE_VALUE;
  }

  /**
   * @return boolean "false" return value
   */
  public static BooleanReturnValue returnFalse() {
    return BooleanReturnValue.FALSE_VALUE;
  }

  /**
   * @return "null" return value
   */
  public static ContractReturnValue returnNull() {
    return NULL_VALUE;
  }

  /**
   * @return non-null return value
   */
  public static ContractReturnValue returnNotNull() {
    return NOT_NULL_VALUE;
  }

  private static final ContractReturnValue ANY_VALUE = new ContractReturnValue("_", 0) {
    @Override
    public boolean isReturnTypeCompatible(@Nullable PsiType returnType) {
      return true;
    }

    @Override
    public DfaValue toDfaValue(DfaValueFactory factory, DfaValue defaultValue) {
      return defaultValue;
    }

    @Override
    public boolean isValueCompatible(DfaMemoryState state, DfaValue value) {
      return true;
    }
  };

  private static final ContractReturnValue FAIL_VALUE = new ContractReturnValue("fail", 5) {
    @Override
    public boolean isReturnTypeCompatible(@Nullable PsiType returnType) {
      return true;
    }

    @Override
    public DfaValue toDfaValue(DfaValueFactory factory, DfaValue defaultValue) {
      return factory.getConstFactory().getContractFail();
    }

    @Override
    public boolean isValueCompatible(DfaMemoryState state, DfaValue value) {
      return false;
    }
  };

  private static final ContractReturnValue NULL_VALUE = new ContractReturnValue("null", 1) {
    @Override
    public boolean isReturnTypeCompatible(@Nullable PsiType returnType) {
      return returnType != null && !(returnType instanceof PsiPrimitiveType);
    }

    @Override
    public DfaValue toDfaValue(DfaValueFactory factory, DfaValue defaultValue) {
      return factory.getConstFactory().getNull();
    }

    @Override
    public boolean isValueCompatible(DfaMemoryState state, DfaValue value) {
      return !state.isNotNull(value);
    }
  };

  private static final ContractReturnValue NOT_NULL_VALUE = new ContractReturnValue("!null", 2) {
    @Override
    public boolean isReturnTypeCompatible(@Nullable PsiType returnType) {
      return returnType != null && !(returnType instanceof PsiPrimitiveType);
    }

    @Override
    public boolean isNotNull() {
      return true;
    }

    @Override
    public DfaValue toDfaValue(DfaValueFactory factory, DfaValue defaultValue) {
      return factory.withFact(defaultValue, DfaFactType.CAN_BE_NULL, false);
    }

    @Override
    public boolean isValueCompatible(DfaMemoryState state, DfaValue value) {
      return !state.isNull(value);
    }
  };

  /**
   * Boolean return value (either "true" or "false").
   */
  public static final class BooleanReturnValue extends ContractReturnValue {
    static final BooleanReturnValue TRUE_VALUE = new BooleanReturnValue(true, 3);
    static final BooleanReturnValue FALSE_VALUE = new BooleanReturnValue(false, 4);
    private final boolean myValue;

    private BooleanReturnValue(boolean value, int ordinal) {
      super(String.valueOf(value), ordinal);
      myValue = value;
    }

    /**
     * @return the return value opposite to this return value
     */
    public BooleanReturnValue negate() {
      return myValue ? FALSE_VALUE : TRUE_VALUE;
    }

    @Override
    public boolean isReturnTypeCompatible(@Nullable PsiType returnType) {
      return PsiType.BOOLEAN.equals(returnType);
    }

    @Override
    public DfaValue toDfaValue(DfaValueFactory factory, DfaValue defaultValue) {
      return factory.getBoolean(myValue);
    }

    @Override
    public boolean isValueCompatible(DfaMemoryState state, DfaValue value) {
      if (value instanceof DfaVariableValue) {
        value = state.getConstantValue((DfaVariableValue)value);
      }
      if (value instanceof DfaConstValue) {
        Object constant = ((DfaConstValue)value).getValue();
        return Boolean.valueOf(myValue).equals(constant);
      }
      return true;
    }
  }
}
