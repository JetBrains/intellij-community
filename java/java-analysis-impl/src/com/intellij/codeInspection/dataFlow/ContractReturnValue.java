// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Representation of {@link MethodContract} return value. It may represent the concrete value (e.g. "false") or pose some constraints
 * to the method return value (e.g. "!null").
 */
public abstract class ContractReturnValue {
  private static final int PARAMETER_ORDINAL_BASE = 10;
  private static final int MAX_SUPPORTED_PARAMETER = 100;

  private interface Validator extends Function<PsiMethod, @Nls @Nullable String> {}
  
  private static final Validator NOT_CONSTRUCTOR =
    method -> method.isConstructor() ? JavaAnalysisBundle.message("contract.return.validator.not.applicable.for.constructor") : null;
  private static final Validator NOT_STATIC =
    method -> method.hasModifierProperty(PsiModifier.STATIC) ? JavaAnalysisBundle.message("contract.return.validator.not.applicable.static")
                                                             : null;
  private static final Validator NOT_PRIMITIVE_RETURN =
    method -> {
      PsiType returnType = method.getReturnType();
      return returnType instanceof PsiPrimitiveType
             ? JavaAnalysisBundle.message("contract.return.validator.not.applicable.primitive", returnType.getPresentableText())
             : null;
    };
  private static final Validator BOOLEAN_RETURN =
    method -> PsiType.BOOLEAN.equals(method.getReturnType()) ? null : JavaAnalysisBundle
      .message("contract.return.validator.return.type.must.be.boolean");

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
   * @return a string which is used to represent this return value inside {@link Contract} annotation.
   */
  @Override
  public String toString() {
    return myName;
  }

  /**
   * Checks whether this return value makes sense for the specified method signature. The method body is not checked.
   * E.g. "true" contract value makes sense for method returning {@code boolean}, but does not make sense for method returning {@code int}.
   * This method can be used to check the contract correctness.
   *
   * @param method method to check
   * @return null if this contract return value makes sense for the supplied return type.
   * Otherwise the human-readable error message is returned.
   */
  public final @InspectionMessage String getMethodCompatibilityProblem(PsiMethod method) {
    //noinspection HardCodedStringLiteral
    return validators().map(fn -> fn.apply(method)).filter(Objects::nonNull).findFirst()
                       .map((JavaAnalysisBundle.message("contract.return.value.validation.prefix", this)+' ')::concat)
                       .orElse(null);
  }

  /**
   * Checks whether this return value makes sense for the specified method signature. The method body is not checked.
   * E.g. "true" contract value makes sense for method returning {@code boolean}, but does not make sense for method returning {@code int}.
   * This method can be used to check the contract correctness.
   *
   * @param method method to check
   * @return true if this contract return value makes sense for the supplied return type.
   */
  public final boolean isMethodCompatible(PsiMethod method) {
    return validators().map(fn -> fn.apply(method)).allMatch(Objects::isNull);
  }

  abstract Stream<Validator> validators();

  public ContractReturnValue intersect(ContractReturnValue other) {
    if (this.equals(other) || other == ANY_VALUE) return this;
    if (this == ANY_VALUE) return other;
    if (this.isNotNull() && other.isNotNull()) return NOT_NULL_VALUE;
    return FAIL_VALUE;
  }

  public boolean isSuperValueOf(ContractReturnValue value) {
    return this == value || this == ANY_VALUE || (this == NOT_NULL_VALUE && value.isNotNull());
  }

  static DfaValue merge(DfaValue defaultValue, DfaValue newValue, DfaMemoryState memState) {
    if (defaultValue == null || DfaTypeValue.isUnknown(defaultValue)) return newValue;
    if (newValue == null || DfaTypeValue.isUnknown(newValue)) return defaultValue;
    DfType defaultType = memState.getDfType(defaultValue);
    DfType newType = memState.getDfType(newValue);
    DfType result = defaultType.meet(newType);
    if (result == DfTypes.BOTTOM) return newValue;
    if (newValue instanceof DfaVariableValue) {
      memState.meetDfType(newValue, result);
      return newValue;
    }
    if (defaultValue instanceof DfaWrappedValue && newType.isSuperType(defaultValue.getDfType())) {
      return defaultValue;
    }
    if (defaultValue instanceof DfaVariableValue) {
      memState.meetDfType(defaultValue, result);
      return defaultValue;
    }
    return defaultValue.getFactory().fromDfType(result);
  }

  /**
   * Converts this return value to the most suitable {@link DfaValue} which represents the same constraints.
   *
   * @param factory a {@link DfaValueFactory} which can be used to create new values if necessary
   * @param defaultValue a default method return type value in the absence of the contracts (may contain method type information)
   * @param callState call state
   * @return a value which represents the constraints of this contract return value.
   */
  public abstract DfaValue getDfaValue(DfaValueFactory factory, DfaValue defaultValue, DfaCallState callState);

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
      case 6:
        return returnNew();
      case 7:
        return returnThis();
      default:
        if (ordinal >= PARAMETER_ORDINAL_BASE && ordinal <= PARAMETER_ORDINAL_BASE + MAX_SUPPORTED_PARAMETER) {
          return returnParameter(ordinal - PARAMETER_ORDINAL_BASE);
        }
        return returnAny();
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
      case "new":
        return returnNew();
      case "this":
        return returnThis();
    }
    if (value.startsWith("param")) {
      String suffix = value.substring("param".length());
      try {
        int paramNumber = Integer.parseInt(suffix) - 1;
        if (paramNumber >= 0 && paramNumber <= MAX_SUPPORTED_PARAMETER) {
          return new ParameterReturnValue(paramNumber);
        }
      }
      catch (NumberFormatException ignored) {
        // unexpected non-integer suffix: ignore
      }
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

  /**
   * @return non-null new object return value
   */
  public static ContractReturnValue returnNew() {
    return NEW_VALUE;
  }

  /**
   * @return non-null "this" return value (qualifier)
   */
  public static ContractReturnValue returnThis() {
    return THIS_VALUE;
  }

  /**
   * @return non-null parameter return value (parameter number is zero-based)
   */
  public static ContractReturnValue returnParameter(int n) {
    if (n < 0) {
      throw new IllegalArgumentException("Negative parameter: " + n);
    }
    if (n > MAX_SUPPORTED_PARAMETER) return ANY_VALUE;
    return new ParameterReturnValue(n);
  }

  private static final ContractReturnValue ANY_VALUE = new ContractReturnValue("_", 0) {
    @Override
    Stream<Validator> validators() {
      return Stream.of(NOT_CONSTRUCTOR);
    }

    @Override
    public DfaValue getDfaValue(DfaValueFactory factory, DfaValue defaultValue, DfaCallState callState) {
      return defaultValue;
    }

    @Override
    public boolean isValueCompatible(DfaMemoryState state, DfaValue value) {
      return true;
    }
  };

  private static final ContractReturnValue FAIL_VALUE = new ContractReturnValue("fail", 5) {
    @Override
    Stream<Validator> validators() {
      return Stream.empty();
    }

    @Override
    public DfaValue getDfaValue(DfaValueFactory factory, DfaValue defaultValue, DfaCallState callState) {
      return factory.fromDfType(DfTypes.FAIL);
    }

    @Override
    public boolean isValueCompatible(DfaMemoryState state, DfaValue value) {
      return false;
    }
  };

  private static final ContractReturnValue NULL_VALUE = new ContractReturnValue("null", 1) {
    @Override
    Stream<Validator> validators() {
      return Stream.of(NOT_CONSTRUCTOR, NOT_PRIMITIVE_RETURN);
    }

    @Override
    public DfaValue getDfaValue(DfaValueFactory factory, DfaValue defaultValue, DfaCallState callState) {
      return factory.getNull();
    }

    @Override
    public boolean isValueCompatible(DfaMemoryState state, DfaValue value) {
      return !state.isNotNull(value);
    }
  };

  private static final ContractReturnValue NOT_NULL_VALUE = new ContractReturnValue("!null", 2) {
    @Override
    Stream<Validator> validators() {
      return Stream.of(NOT_CONSTRUCTOR, NOT_PRIMITIVE_RETURN);
    }

    @Override
    public boolean isNotNull() {
      return true;
    }

    @Override
    public DfaValue getDfaValue(DfaValueFactory factory, DfaValue defaultValue, DfaCallState callState) {
      return merge(defaultValue, factory.fromDfType(DfTypes.NOT_NULL_OBJECT), callState.myMemoryState);
    }

    @Override
    public boolean isValueCompatible(DfaMemoryState state, DfaValue value) {
      return !state.isNull(value);
    }
  };

  private static final ContractReturnValue NEW_VALUE = new ContractReturnValue("new", 6) {
    @Override
    Stream<Validator> validators() {
      return Stream.of(NOT_CONSTRUCTOR, NOT_PRIMITIVE_RETURN);
    }

    @Override
    public DfaValue getDfaValue(DfaValueFactory factory, DfaValue defaultValue, DfaCallState callState) {
      DfType dfType = callState.myMemoryState.getDfType(defaultValue);
      dfType = dfType.meet(DfTypes.NOT_NULL_OBJECT);
      if (callState.myCallArguments.myMutation.isPure()) {
        boolean unmodifiableView = Mutability.fromDfType(dfType) == Mutability.UNMODIFIABLE_VIEW;
        // Unmodifiable view methods like Collections.unmodifiableList create new object, but their special field "size" is
        // actually a delegate, so we cannot trust it if the original value is not local
        if (!unmodifiableView) {
          dfType = dfType.meet(DfTypes.LOCAL_OBJECT);
        }
      }
      return merge(defaultValue, factory.fromDfType(dfType), callState.myMemoryState);
    }

    @Override
    public boolean isNotNull() {
      return true;
    }

    @Override
    public boolean isValueCompatible(DfaMemoryState state, DfaValue value) {
      return !state.isNull(value);
    }
  };

  private static final ContractReturnValue THIS_VALUE = new ContractReturnValue("this", 7) {
    @Override
    Stream<Validator> validators() {
      return Stream.of(NOT_CONSTRUCTOR, NOT_STATIC, NOT_PRIMITIVE_RETURN, method -> {
        PsiType returnType = method.getReturnType();
        if (returnType instanceof PsiClassType) {
          PsiClass aClass = method.getContainingClass();
          if (aClass != null && JavaPsiFacade.getElementFactory(method.getProject()).createType(aClass).isConvertibleFrom(returnType)) {
            return null;
          }
        }
        return JavaAnalysisBundle.message("contract.return.validator.method.return.incompatible.with.method.containing.class");
      });
    }

    @Override
    public boolean isNotNull() {
      return true;
    }

    @Override
    public DfaValue getDfaValue(DfaValueFactory factory, DfaValue defaultValue, DfaCallState callState) {
      DfaValue qualifier = callState.myCallArguments.myQualifier;
      if (qualifier != null && !DfaTypeValue.isUnknown(qualifier)) {
        return merge(defaultValue, qualifier, callState.myMemoryState);
      }
      return merge(defaultValue, factory.fromDfType(DfTypes.NOT_NULL_OBJECT), callState.myMemoryState);
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

    public boolean getValue() {
      return myValue;
    }

    /**
     * @return the return value opposite to this return value
     */
    public BooleanReturnValue negate() {
      return myValue ? FALSE_VALUE : TRUE_VALUE;
    }

    @Override
    Stream<Validator> validators() {
      return Stream.of(NOT_CONSTRUCTOR, BOOLEAN_RETURN);
    }

    @Override
    public DfaValue getDfaValue(DfaValueFactory factory, DfaValue defaultValue, DfaCallState callState) {
      return factory.getBoolean(myValue);
    }

    @Override
    public boolean isValueCompatible(DfaMemoryState state, DfaValue value) {
      DfType type = state.getUnboxedDfType(value);
      return type.isSuperType(DfTypes.booleanValue(myValue));
    }
  }

  public static final class ParameterReturnValue extends ContractReturnValue {
    private final int myParamNumber; // zero-based

    public ParameterReturnValue(int n) {
      super("param" + (n + 1), n + PARAMETER_ORDINAL_BASE);
      myParamNumber = n;
    }

    public int getParameterNumber() {
      return myParamNumber;
    }

    @Override
    Stream<Validator> validators() {
      return Stream.of(NOT_CONSTRUCTOR, method -> {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length <= myParamNumber) {
          return JavaAnalysisBundle
            .message("contract.return.validator.too.few.parameters", parameters.length);
        }
        PsiType parameterType = parameters[myParamNumber].getType();
        PsiType returnType = method.getReturnType();
        if (returnType != null && !returnType.isConvertibleFrom(parameterType)) {
          return JavaAnalysisBundle.message("contract.return.validator.incompatible.return.parameter.type", 
                                            returnType.getPresentableText(), parameterType.getPresentableText());
        }
        return null;
      });
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj || (obj instanceof ParameterReturnValue && ((ParameterReturnValue)obj).myParamNumber == myParamNumber);
    }

    @Override
    public DfaValue getDfaValue(DfaValueFactory factory, DfaValue defaultValue, DfaCallState callState) {
      if (callState.myCallArguments.myArguments != null && callState.myCallArguments.myArguments.length > myParamNumber) {
        DfaValue argument = callState.myCallArguments.myArguments[myParamNumber];
        return merge(defaultValue, argument, callState.myMemoryState);
      }
      return defaultValue;
    }

    @Override
    public boolean isValueCompatible(DfaMemoryState state, DfaValue value) {
      return true;
    }
  }
}
