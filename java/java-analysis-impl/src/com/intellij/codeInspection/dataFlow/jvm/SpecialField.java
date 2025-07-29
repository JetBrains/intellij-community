// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.jvm;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfStreamStateType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DerivedVariableDescriptor;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.*;

import java.util.Arrays;
import java.util.function.Function;

import static com.intellij.codeInspection.dataFlow.ContractReturnValue.returnFalse;
import static com.intellij.codeInspection.dataFlow.ContractReturnValue.returnTrue;
import static com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint.NULL_VALUE;
import static com.intellij.psi.CommonClassNames.*;

/**
 * Represents a derived JVM field
 *
 * @author Tagir Valeev
 */
public enum SpecialField implements DerivedVariableDescriptor {
  ARRAY_LENGTH("length", "special.field.array.length", true) {
    @Override
    public boolean isMyQualifierType(DfType type) {
      return TypeConstraint.fromDfType(type).isArray();
    }

    @Override
    boolean isMyAccessor(PsiMember accessor) {
      return accessor instanceof PsiField && "length".equals(accessor.getName()) && PsiUtil.isArrayClass(accessor.getContainingClass());
    }

    @Override
    public @NotNull DfType getInitialDfType(@NotNull DfaVariableValue thisValue, @Nullable PsiElement context) {
      return getDfType(thisValue.getQualifier()).meet(fromInitializer(thisValue, context, this::fromInitializer));
    }

    @NotNull
    DfType fromInitializer(PsiExpression initializer) {
      if (initializer instanceof PsiArrayInitializerExpression) {
        return DfTypes.intValue(((PsiArrayInitializerExpression)initializer).getInitializers().length);
      }
      if (initializer instanceof PsiNewExpression) {
        PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)initializer).getArrayInitializer();
        if (arrayInitializer != null) {
          return DfTypes.intValue(arrayInitializer.getInitializers().length);
        }
        PsiExpression[] dimensions = ((PsiNewExpression)initializer).getArrayDimensions();
        if (dimensions.length > 0) {
          Object length = ExpressionUtils.computeConstantExpression(dimensions[0]);
          if (length instanceof Integer) {
            return DfTypes.intValue(((Integer)length).intValue());
          }
        }
      }
      return DfType.TOP;
    }
  },
  STRING_LENGTH("length", "special.field.string.length", true) {
    @Override
    public @NotNull DfType getInitialDfType(@NotNull DfaVariableValue thisValue, @Nullable PsiElement context) {
      return getDfType(thisValue.getQualifier()).meet(
        fromInitializer(thisValue, context, initializer -> fromConstant(ExpressionUtils.computeConstantExpression(initializer))));
    }

    @Override
    public boolean isMyQualifierType(DfType type) {
      return TypeConstraint.fromDfType(type).isExact(JAVA_LANG_STRING);
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
    public @NotNull DfType fromConstant(@Nullable Object obj) {
      return obj instanceof String ? DfTypes.intValue(((String)obj).length()) : DfType.TOP;
    }
  },
  COLLECTION_SIZE("size", "special.field.collection.size", false) {
    private static final CallMatcher SIZE_METHODS =
      CallMatcher.anyOf(CallMatcher.instanceCall(JAVA_UTIL_COLLECTION, "size").parameterCount(0),
                        CallMatcher.instanceCall(JAVA_UTIL_MAP, "size").parameterCount(0));

    @Override
    public boolean isMyQualifierType(DfType type) {
      TypeConstraint constraint = TypeConstraint.fromDfType(type);
      return constraint.isSubtypeOf(JAVA_UTIL_MAP) || constraint.isSubtypeOf(JAVA_UTIL_COLLECTION);
    }

    @Override
    boolean isMyAccessor(PsiMember accessor) {
      return accessor instanceof PsiMethod && SIZE_METHODS.methodMatches((PsiMethod)accessor);
    }

    @Override
    public @NotNull DfType fromConstant(@Nullable Object obj) {
      if (obj instanceof PsiField && DfaUtil.isEmptyCollectionConstantField((PsiVariable)obj)) {
        return DfTypes.intValue(0);
      }
      return super.fromConstant(obj);
    }
  },
  UNBOX("value", "special.field.unboxed.value", true) {
    private static final CallMatcher UNBOXING_CALL = CallMatcher.anyOf(
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
    public @NotNull DfType getDfType(@Nullable DfaVariableValue qualifier) {
      if (qualifier == null) return DfType.TOP;
      TypeConstraint constraint = TypeConstraint.fromDfType(qualifier.getDfType());
      return constraint.getUnboxedType();
    }

    @Override
    public @NotNull DfType getFromQualifier(@NotNull DfType dfType) {
      DfType fromQualifier = super.getFromQualifier(dfType);
      DfType unboxedType = TypeConstraint.fromDfType(dfType).getUnboxedType();
      if (unboxedType != DfType.BOTTOM) {
        return fromQualifier.meet(unboxedType);
      }
      return fromQualifier;
    }

    @Override
    public @NotNull DfType getDefaultValue() {
      return DfType.TOP;
    }

    @Override
    public boolean isMyQualifierType(DfType type) {
      return TypeConstraint.fromDfType(type).isPrimitiveWrapper();
    }

    @Override
    boolean isMyAccessor(PsiMember accessor) {
      return accessor instanceof PsiMethod && UNBOXING_CALL.methodMatches((PsiMethod)accessor);
    }

    @Override
    public boolean equalityImpliesQualifierEquality() {
      return true;
    }
  },
  OPTIONAL_VALUE("value", "special.field.optional.value", true) {
    @Override
    public @NotNull DfType getDfType(@Nullable DfaVariableValue qualifier) {
      if (qualifier == null) return DfType.TOP;
      TypeConstraint qualifierType = TypeConstraint.fromDfType(qualifier.getDfType());
      String type = null;
      if (qualifierType.isExact(OptionalUtil.OPTIONAL_INT)) {
        type = JAVA_LANG_INTEGER;
      }
      else if (qualifierType.isExact(OptionalUtil.OPTIONAL_LONG)) {
        type = JAVA_LANG_LONG;
      }
      else if (qualifierType.isExact(OptionalUtil.OPTIONAL_DOUBLE)) {
        type = JAVA_LANG_DOUBLE;
      }
      if (type != null) {
        return DfTypes.typedObject(JavaPsiFacade.getElementFactory(qualifier.getFactory().getProject())
                                     .createTypeFromText(type, null), Nullability.UNKNOWN);
      }
      return DfTypes.OBJECT_OR_NULL;
    }

    @Override
    public @NotNull DfType getInitialDfType(@NotNull DfaVariableValue thisValue,
                                            @Nullable PsiElement context) {
      return getDfType(thisValue.getQualifier()).meet(DfaNullability.NULLABLE.asDfType());
    }

    @Override
    public @NotNull DfType getDefaultValue() {
      return DfaNullability.NULLABLE.asDfType();
    }

    @Override
    public boolean isMyQualifierType(DfType type) {
      TypeConstraint constraint = TypeConstraint.fromDfType(type);
      return constraint.isExact(JAVA_UTIL_OPTIONAL) ||
             constraint.isExact(OptionalUtil.OPTIONAL_DOUBLE) ||
             constraint.isExact(OptionalUtil.OPTIONAL_INT) ||
             constraint.isExact(OptionalUtil.OPTIONAL_LONG) ||
             constraint.isSubtypeOf(OptionalUtil.GUAVA_OPTIONAL);
    }

    @Override
    public String getPresentationText(@NotNull DfType dfType, @Nullable PsiType type) {
      if (dfType == DfTypes.NULL) {
        return JavaAnalysisBundle.message("dftype.presentation.empty.optional");
      }
      if ((!dfType.isSuperType(DfTypes.NULL))) {
        return JavaAnalysisBundle.message("dftype.presentation.present.optional");
      }
      return "";
    }
  },
  ENUM_ORDINAL("ordinal", "special.field.enum.ordinal", true) {
    private static final CallMatcher ENUM_ORDINAL_METHOD = CallMatcher.instanceCall(JAVA_LANG_ENUM, "ordinal").parameterCount(0);

    @Override
    public @NotNull DfType getDfType(@Nullable DfaVariableValue qualifier) {
      if (qualifier != null) {
        TypeConstraint constraint = TypeConstraint.fromDfType(qualifier.getDfType());
        if (constraint.isExact()) {
          PsiClass cls = PsiUtil.resolveClassInClassTypeOnly(constraint.getPsiType(qualifier.getFactory().getProject()));
          if (cls instanceof PsiEnumConstantInitializer) {
            cls = cls.getSuperClass();
          }
          if (cls != null) {
            long count = Arrays.stream(cls.getFields()).filter(field -> field instanceof PsiEnumConstant).count();
            // Keep +1 ordinal for possible enum changes
            return DfTypes.intRange(LongRangeSet.range(0, count));
          }
        }
      }
      return super.getDfType(qualifier);
    }

    @Override
    public @NotNull DfType fromConstant(@Nullable Object obj) {
      if (obj instanceof PsiEnumConstant constant) {
        PsiClass psiClass = constant.getContainingClass();
        if (psiClass != null) {
          int ordinal = 0;
          for (PsiField field : psiClass.getFields()) {
            if (field == constant) return DfTypes.intValue(ordinal);
            if (field instanceof PsiEnumConstant) {
              ordinal++;
            }
          }
        }
      }
      return super.fromConstant(obj);
    }

    @Override
    public boolean isMyQualifierType(DfType type) {
      TypeConstraint constraint = TypeConstraint.fromDfType(type);
      return constraint.isEnum() || constraint.isSubtypeOf(JAVA_LANG_ENUM);
    }

    @Override
    boolean isMyAccessor(PsiMember accessor) {
      return accessor instanceof PsiMethod && ENUM_ORDINAL_METHOD.methodMatches((PsiMethod)accessor);
    }
  },
  CONSUMED_STREAM("linkedOrConsumed", "special.field.consumed.stream", false) {
    @Override
    public boolean isMyQualifierType(DfType type) {
      TypeConstraint constraint = TypeConstraint.fromDfType(type);
      return constraint.isSubtypeOf(JAVA_UTIL_STREAM_BASE_STREAM);
    }

    @Override
    public @NotNull DfType getDefaultValue() {
      return DfStreamStateType.UNKNOWN;
    }
  },
  INSTANTIABLE_CLASS("instantiable", "special.field.instantiable.class", true) {
    @Override
    public @NotNull DfType fromConstant(@Nullable Object obj) {
      if (obj instanceof PsiType type) {
        return DfTypes.booleanValue(TypeConstraints.exact(type) != TypeConstraints.BOTTOM);
      }
      return super.fromConstant(obj);
    }

    @Override
    public boolean isMyQualifierType(DfType type) {
      TypeConstraint constraint = TypeConstraint.fromDfType(type);
      return constraint.isExact(JAVA_LANG_CLASS);
    }

    @Override
    public @NotNull DfType getDefaultValue() {
      return DfTypes.BOOLEAN;
    }
  };

  private static final SpecialField[] VALUES = values();
  private final String myTitle;
  private final @PropertyKey(resourceBundle = JavaAnalysisBundle.BUNDLE) String myTitleKey;
  private final boolean myFinal;

  SpecialField(String title, @PropertyKey(resourceBundle = JavaAnalysisBundle.BUNDLE) String titleKey, boolean isFinal) {
    myTitle = title;
    myTitleKey = titleKey;
    myFinal = isFinal;
  }

  @Override
  public boolean isStable() {
    return myFinal;
  }

  @Override
  public boolean isImplicitReadPossible() {
    return true;
  }

  public abstract boolean isMyQualifierType(DfType type);

  /**
   * Checks whether supplied accessor (field or method) can be used to read this special field
   *
   * @param accessor accessor to test
   * @return true if supplied accessor can be used to read this special field
   */
  boolean isMyAccessor(PsiMember accessor) {
    return false;
  }

  public @Nls String getPresentationText(@NotNull DfType dfType, @Nullable PsiType type) {
    if (getDefaultValue().equals(dfType)) {
      return "";
    }
    return dfType.toString();
  }

  /**
   * Finds a special field which corresponds to given accessor (method or field)
   * @param accessor accessor to find a special field for
   * @return found special field or null if accessor cannot be used to access a special field
   */
  @Contract("null -> null")
  public static @Nullable SpecialField findSpecialField(PsiElement accessor) {
    if (!(accessor instanceof PsiMember member)) return null;
    for (SpecialField sf : VALUES) {
      if (sf.isMyAccessor(member)) {
        return sf;
      }
    }
    return null;
  }

  static @NotNull DfType fromInitializer(@NotNull DfaVariableValue thisValue,
                                         @Nullable PsiElement context,
                                         @NotNull Function<PsiExpression, DfType> typeByInitializer) {
    DfaVariableValue qualifier = thisValue.getQualifier();
    if (qualifier != null) {
      PsiField psiVariable = ObjectUtils.tryCast(qualifier.getPsiVariable(), PsiField.class);
      if (psiVariable != null &&
          FieldChecker.getChecker(context).canTrustFieldInitializer(psiVariable) &&
          psiVariable.hasModifierProperty(PsiModifier.STATIC) &&
          psiVariable.hasModifierProperty(PsiModifier.FINAL)) {
        PsiExpression initializer = PsiFieldImpl.getDetachedInitializer(psiVariable);
        if (initializer != null) {
          return typeByInitializer.apply(initializer);
        }
      }
    }
    return DfType.TOP;
  }

  @Override
  public @NotNull DfType getDefaultValue() {
    return DfTypes.intRange(JvmPsiRangeSetUtil.indexRange());
  }

  @Override
  public @NotNull DfType getDfType(@Nullable DfaVariableValue qualifier) {
    return getDefaultValue();
  }

  public @NotNull DfType fromConstant(@Nullable Object obj) {
    return DfType.TOP;
  }

  /**
   * @return an array of method contracts which equivalent to checking this special field for zero
   */
  public MethodContract[] getEmptyContracts() {
    ContractValue thisValue = ContractValue.qualifier().specialField(this);
    return new MethodContract[]{
      MethodContract.singleConditionContract(thisValue, RelationType.EQ, ContractValue.zero(), returnTrue()),
      MethodContract.trivialContract(returnFalse())};
  }

  public MethodContract[] getEqualsContracts() {
    return new MethodContract[]{new StandardMethodContract(new StandardMethodContract.ValueConstraint[]{NULL_VALUE}, returnFalse()),
                         MethodContract.singleConditionContract(
                           ContractValue.qualifier().specialField(this), RelationType.NE,
                           ContractValue.argument(0).specialField(this), returnFalse())};
  }

  @Override
  public @NotNull DfType asDfType(@NotNull DfType fieldValue) {
    DfType defaultType = this == OPTIONAL_VALUE ? DfTypes.OBJECT_OR_NULL : getDefaultValue();
    DfType clamped = fieldValue.meet(defaultType);
    if (clamped.equals(defaultType)) return DfTypes.NOT_NULL_OBJECT;
    if (clamped.equals(DfType.BOTTOM)) return DfType.BOTTOM;
    return DfTypes.customObject(TypeConstraints.TOP, DfaNullability.NOT_NULL, Mutability.UNKNOWN, this, clamped);
  }

  @Override
  public @NotNull DfType asDfType(@NotNull DfType qualifierType, @NotNull DfType fieldValue) {
    if (this == STRING_LENGTH && fieldValue.isConst(0)) {
      return DfTypes.referenceConstant("", TypeConstraint.fromDfType(qualifierType));
    }
    if (this == ENUM_ORDINAL) {
      Integer constValue = fieldValue.getConstantOfType(Integer.class);
      if (constValue != null) {
        TypeConstraint qualifier = TypeConstraint.fromDfType(qualifierType);
        PsiEnumConstant constant = qualifier.getEnumConstant(constValue);
        if (constant != null) {
          return DfTypes.referenceConstant(constant, TypeConstraint.fromDfType(qualifierType));
        }
      }
    }
    return asDfType(fieldValue).meet(qualifierType);
  }

  /**
   * Returns a special field which corresponds to given qualifier type
   * (currently it's assumed that only one special field may exist for given qualifier type)
   *
   * @param type a qualifier df type
   * @return a special field; null if no special field is available for given type
   */
  public static @Nullable SpecialField fromQualifierType(@NotNull DfType type) {
    if (type instanceof DfReferenceType && ((DfReferenceType)type).getSpecialField() != null) {
      return ((DfReferenceType)type).getSpecialField();
    }
    for (SpecialField value : VALUES) {
      if (value.isMyQualifierType(type)) {
        return value;
      }
    }
    return null;
  }

  public @NotNull @Nls String getPresentationName() {
    return JavaAnalysisBundle.message(myTitleKey);
  }

  @Override
  public String toString() {
    return myTitle;
  }
}
