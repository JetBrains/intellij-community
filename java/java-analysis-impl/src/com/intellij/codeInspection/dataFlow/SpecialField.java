// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.dataFlow.ContractReturnValue.returnFalse;
import static com.intellij.codeInspection.dataFlow.ContractReturnValue.returnTrue;
import static com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint.NULL_VALUE;
import static com.intellij.psi.CommonClassNames.*;

/**
 * Represents a method which is handled as a field in DFA.
 *
 * @author Tagir Valeev
 */
public enum SpecialField implements VariableDescriptor {
  ARRAY_LENGTH("length", true) {
    @Override
    boolean isMyQualifierType(PsiType type) {
      return type instanceof PsiArrayType;
    }

    @Override
    boolean isMyAccessor(PsiMember accessor) {
      return accessor instanceof PsiField && "length".equals(accessor.getName()) && PsiUtil.isArrayClass(accessor.getContainingClass());
    }

    @Override
    DfaValue fromInitializer(DfaValueFactory factory, PsiExpression initializer) {
      if (initializer instanceof PsiArrayInitializerExpression) {
        return factory.getInt(((PsiArrayInitializerExpression)initializer).getInitializers().length);
      }
      if (initializer instanceof PsiNewExpression) {
        PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)initializer).getArrayInitializer();
        if (arrayInitializer != null) {
          return factory.getInt(arrayInitializer.getInitializers().length);
        }
        PsiExpression[] dimensions = ((PsiNewExpression)initializer).getArrayDimensions();
        if (dimensions.length > 0) {
          Object length = ExpressionUtils.computeConstantExpression(dimensions[0]);
          if (length instanceof Integer) {
            return factory.getInt(((Integer)length).intValue());
          }
        }
      }
      return null;
    }
  },
  STRING_LENGTH("length", true) {
    @Override
    DfaValue fromInitializer(DfaValueFactory factory, PsiExpression initializer) {
      return fromConstant(factory, ExpressionUtils.computeConstantExpression(initializer));
    }

    @Override
    boolean isMyQualifierType(PsiType type) {
      return TypeUtils.isJavaLangString(type);
    }

    @Override
    boolean isMyAccessor(PsiMember accessor) {
      if (!(accessor instanceof PsiMethod) || !"length".equals(accessor.getName()) || !((PsiMethod)accessor).getParameterList().isEmpty()) {
        return false;
      }
      PsiClass containingClass = accessor.getContainingClass();
      return containingClass != null && JAVA_LANG_STRING.equals(containingClass.getQualifiedName());
    }

    @Override
    public DfaValue fromConstant(DfaValueFactory factory, @Nullable Object obj) {
      return obj instanceof String ? factory.getInt(((String)obj).length()) : null;
    }
  },
  COLLECTION_SIZE("size", false) {
    private final CallMatcher SIZE_METHODS = CallMatcher.anyOf(CallMatcher.instanceCall(JAVA_UTIL_COLLECTION, "size").parameterCount(0),
                                                               CallMatcher.instanceCall(JAVA_UTIL_MAP, "size").parameterCount(0));
    private final CallMatcher MAP_COLLECTIONS = CallMatcher.instanceCall(JAVA_UTIL_MAP, "keySet", "entrySet", "values")
      .parameterCount(0);

    @Override
    boolean isMyQualifierType(PsiType type) {
      return InheritanceUtil.isInheritor(type, JAVA_UTIL_MAP) || InheritanceUtil.isInheritor(type, JAVA_UTIL_COLLECTION);
    }

    @Override
    boolean isMyAccessor(PsiMember accessor) {
      return accessor instanceof PsiMethod && SIZE_METHODS.methodMatches((PsiMethod)accessor);
    }

    @NotNull
    @Override
    public DfaValue createValue(@NotNull DfaValueFactory factory, @Nullable DfaValue qualifier, boolean forAccessor) {
      if (qualifier instanceof DfaVariableValue) {
        DfaVariableValue var = (DfaVariableValue)qualifier;
        PsiModifierListOwner owner = var.getPsiVariable();
        if (var.getQualifier() != null && owner instanceof PsiMethod && MAP_COLLECTIONS.methodMatches((PsiMethod)owner)) {
          return super.createValue(factory, var.getQualifier(), forAccessor);
        }
      }
      return super.createValue(factory, qualifier, forAccessor);
    }
  },
  UNBOX("value", true) {
    private final CallMatcher UNBOXING_CALL = CallMatcher.anyOf(
      CallMatcher.exactInstanceCall(JAVA_LANG_INTEGER, "intValue").parameterCount(0),
      CallMatcher.exactInstanceCall(JAVA_LANG_LONG, "longValue").parameterCount(0),
      CallMatcher.exactInstanceCall(JAVA_LANG_SHORT, "shortValue").parameterCount(0),
      CallMatcher.exactInstanceCall(JAVA_LANG_BYTE, "byteValue").parameterCount(0),
      CallMatcher.exactInstanceCall(JAVA_LANG_CHARACTER, "charValue").parameterCount(0),
      CallMatcher.exactInstanceCall(JAVA_LANG_BOOLEAN, "booleanValue").parameterCount(0),
      CallMatcher.exactInstanceCall(JAVA_LANG_FLOAT, "floatValue").parameterCount(0),
      CallMatcher.exactInstanceCall(JAVA_LANG_DOUBLE, "doubleValue").parameterCount(0)
    );

    @Override
    public PsiPrimitiveType getType(DfaVariableValue variableValue) {
      return PsiPrimitiveType.getUnboxedType(variableValue.getType());
    }

    @NotNull
    @Override
    public DfaValue getDefaultValue(DfaValueFactory factory, boolean forAccessor) {
      return DfaUnknownValue.getInstance();
    }

    @NotNull
    @Override
    public DfaValue createValue(@NotNull DfaValueFactory factory, @Nullable DfaValue qualifier, boolean forAccessor) {
      if (qualifier instanceof DfaBoxedValue) {
        return ((DfaBoxedValue)qualifier).getWrappedValue();
      }
      return super.createValue(factory, qualifier, forAccessor);
    }

    @Override
    boolean isMyQualifierType(PsiType type) {
      return TypeConversionUtil.isPrimitiveWrapper(type);
    }

    @Override
    boolean isMyAccessor(PsiMember accessor) {
      return accessor instanceof PsiMethod && UNBOXING_CALL.methodMatches((PsiMethod)accessor);
    }
  },
  OPTIONAL_VALUE("value", true) {
    @Override
    public PsiType getType(DfaVariableValue variableValue) {
      return OptionalUtil.getOptionalElementType(variableValue.getType());
    }

    @NotNull
    @Override
    public DfaValue getDefaultValue(DfaValueFactory factory, boolean forAccessor) {
      return factory.getFactValue(DfaFactType.NULLABILITY, forAccessor ? DfaNullability.NOT_NULL : DfaNullability.NULLABLE);
    }

    @Override
    boolean isMyQualifierType(PsiType type) {
      return TypeUtils.isOptional(type);
    }

    @Override
    public String getPresentationText(@NotNull DfaValue value, @Nullable PsiType type) {
      if (value instanceof DfaConstValue && ((DfaConstValue)value).getValue() == null) {
        return "empty Optional";
      }
      if (value instanceof DfaFactMapValue) {
        DfaNullability nullability = ((DfaFactMapValue)value).get(DfaFactType.NULLABILITY);
        if (nullability == DfaNullability.NOT_NULL) {
          return "present Optional";
        }
        return "";
      }
      return super.getPresentationText(value, type);
    }

    @Override
    boolean isMyAccessor(PsiMember accessor) {
      return accessor instanceof PsiMethod && OptionalUtil.OPTIONAL_GET.methodMatches((PsiMethod)accessor);
    }
  };

  private static final SpecialField[] VALUES = values();
  private final String myTitle;
  private final boolean myFinal;

  SpecialField(String title, boolean isFinal) {
    myTitle = title;
    myFinal = isFinal;
  }

  @Override
  public boolean isStable() {
    return myFinal;
  }
  
  abstract boolean isMyQualifierType(PsiType type);

  /**
   * Checks whether supplied accessor (field or method) can be used to read this special field
   *
   * @param accessor accessor to test to test
   * @return true if supplied accessor can be used to read this special field
   */
  abstract boolean isMyAccessor(PsiMember accessor);

  public String getPresentationText(@NotNull DfaValue value, @Nullable PsiType type) {
    return value.toString();
  }

  /**
   * Finds a special field which corresponds to given accessor (method or field)
   * @param accessor accessor to find a special field for
   * @return found special field or null if accessor cannot be used to access a special field
   */
  @Contract("null -> null")
  @Nullable
  public static SpecialField findSpecialField(PsiElement accessor) {
    if (!(accessor instanceof PsiMember)) return null;
    PsiMember member = (PsiMember)accessor;
    for (SpecialField sf : VALUES) {
      if (sf.isMyAccessor(member)) {
        return sf;
      }
    }
    return null;
  }

  /**
   * Returns a DfaValue which represents this special field
   *
   * @param factory a factory to create new values if necessary
   * @param qualifier a known qualifier value
   * @return a DfaValue which represents this special field
   */
  @Override
  @NotNull
  public final DfaValue createValue(@NotNull DfaValueFactory factory, @Nullable DfaValue qualifier) {
    return createValue(factory, qualifier, false);
  }

  @NotNull
  @Override
  public DfaValue createValue(@NotNull DfaValueFactory factory, @Nullable DfaValue qualifier, boolean forAccessor) {
    if (qualifier instanceof DfaVariableValue) {
      DfaVariableValue variableValue = (DfaVariableValue)qualifier;
      PsiModifierListOwner psiVariable = variableValue.getPsiVariable();
      if (psiVariable instanceof PsiField &&
          factory.canTrustFieldInitializer((PsiField)psiVariable) &&
          psiVariable.hasModifierProperty(PsiModifier.STATIC) &&
          psiVariable.hasModifierProperty(PsiModifier.FINAL)) {
        PsiExpression initializer = ((PsiField)psiVariable).getInitializer();
        if (initializer != null) {
          DfaValue value = fromInitializer(factory, initializer);
          if (value != null) {
            return value;
          }
        }
      }
      return VariableDescriptor.super.createValue(factory, qualifier, forAccessor);
    }
    if(qualifier instanceof DfaFactMapValue) {
      SpecialFieldValue sfValue = ((DfaFactMapValue)qualifier).get(DfaFactType.SPECIAL_FIELD_VALUE);
      if (sfValue != null && sfValue.getField() == this) {
        return sfValue.getValue();
      }
    }
    if(qualifier instanceof DfaConstValue) {
      Object obj = ((DfaConstValue)qualifier).getValue();
      if(obj != null) {
        DfaValue value = fromConstant(factory, obj);
        if(value != null) {
          return value;
        }
      }
    }
    return getDefaultValue(factory, forAccessor);
  }

  /**
   * Creates a DfaValue which describes any possible value this special field may have
   * 
   * @param factory {@link DfaValueFactory} to use
   * @param forAccessor if true, the default value for accessor result should be returned 
   *                    (may differ from internal representation of value) 
   * @return a default value, could be unknown
   */
  @NotNull
  public DfaValue getDefaultValue(DfaValueFactory factory, boolean forAccessor) {
    return factory.getFactValue(DfaFactType.RANGE, LongRangeSet.indexRange());
  }

  @Override
  public PsiType getType(DfaVariableValue variableValue) {
    return PsiType.INT;
  }

  DfaValue fromInitializer(DfaValueFactory factory, PsiExpression initializer) {
    return null;
  }

  DfaValue fromConstant(DfaValueFactory factory, @Nullable Object obj) {
    return null;
  }

  /**
   * @return a list of method contracts which equivalent to checking this special field for zero
   */
  public MethodContract[] getEmptyContracts() {
    ContractValue thisValue = ContractValue.qualifier().specialField(this);
    return new MethodContract[]{
      MethodContract.singleConditionContract(thisValue, DfaRelationValue.RelationType.EQ, ContractValue.zero(), returnTrue()),
      MethodContract.trivialContract(returnFalse())};
  }

  public MethodContract[] getEqualsContracts() {
    return new MethodContract[]{new StandardMethodContract(new StandardMethodContract.ValueConstraint[]{NULL_VALUE}, returnFalse()),
                         MethodContract.singleConditionContract(
                           ContractValue.qualifier().specialField(this), DfaRelationValue.RelationType.NE,
                           ContractValue.argument(0).specialField(this), returnFalse())};
  }

  public SpecialFieldValue withValue(DfaValue value) {
    return new SpecialFieldValue(this, value);
  }

  /**
   * Returns a value from given SpecialFieldValue if it's bound to this special field
   * @param sfValue {@link SpecialFieldValue} to extract the value from
   * @return en extracted value, or null if argument is null or it's bound to different special field
   */
  @Contract("null -> null")
  @Nullable
  public DfaValue extract(@Nullable SpecialFieldValue sfValue) {
    return sfValue != null && sfValue.getField() == this ? sfValue.getValue() : null;
  }

  /**
   * Returns a special field which corresponds to given qualifier type
   * (currently it's assumed that only one special field may exist for given qualifier type)
   * 
   * @param type a qualifier type
   * @return a special field; null if no special field is available for given type
   */
  @Nullable
  public static SpecialField fromQualifierType(PsiType type) {
    for (SpecialField value : VALUES) {
      if (value.isMyQualifierType(type)) {
        return value;
      }
    }
    return null;
  }
  
  @Override
  public String toString() {
    return myTitle;
  }
}
