// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.*;

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
    boolean isMyQualifierType(DfType type) {
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
    boolean isMyQualifierType(DfType type) {
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

    @NotNull
    @Override
    public DfType fromConstant(@Nullable Object obj) {
      return obj instanceof String ? DfTypes.intValue(((String)obj).length()) : DfType.TOP;
    }
  },
  COLLECTION_SIZE("size", "special.field.collection.size", false) {
    private final CallMatcher SIZE_METHODS = CallMatcher.anyOf(CallMatcher.instanceCall(JAVA_UTIL_COLLECTION, "size").parameterCount(0),
                                                               CallMatcher.instanceCall(JAVA_UTIL_MAP, "size").parameterCount(0));
    @Override
    boolean isMyQualifierType(DfType type) {
      TypeConstraint constraint = TypeConstraint.fromDfType(type);
      return constraint.isSubtypeOf(JAVA_UTIL_MAP) || constraint.isSubtypeOf(JAVA_UTIL_COLLECTION);
    }

    @Override
    boolean isMyAccessor(PsiMember accessor) {
      return accessor instanceof PsiMethod && SIZE_METHODS.methodMatches((PsiMethod)accessor);
    }

    @NotNull
    @Override
    public DfType fromConstant(@Nullable Object obj) {
      if (obj instanceof PsiField && DfaUtil.isEmptyCollectionConstantField((PsiVariable)obj)) {
        return DfTypes.intValue(0);
      }
      return super.fromConstant(obj);
    }
  },
  UNBOX("value", "special.field.unboxed.value", true) {
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

    @NotNull
    @Override
    public DfType getDefaultValue() {
      return DfType.TOP;
    }

    @Override
    boolean isMyQualifierType(DfType type) {
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

    @NotNull
    @Override
    public DfType getDefaultValue() {
      return DfaNullability.NULLABLE.asDfType();
    }

    @Override
    boolean isMyQualifierType(DfType type) {
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

    @Override
    boolean isMyAccessor(PsiMember accessor) {
      return false;
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

  abstract boolean isMyQualifierType(DfType type);

  /**
   * Checks whether supplied accessor (field or method) can be used to read this special field
   *
   * @param accessor accessor to test to test
   * @return true if supplied accessor can be used to read this special field
   */
  abstract boolean isMyAccessor(PsiMember accessor);

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
    if (qualifier instanceof DfaWrappedValue && ((DfaWrappedValue)qualifier).getSpecialField() == this) {
      return ((DfaWrappedValue)qualifier).getWrappedValue();
    }
    if (qualifier instanceof DfaVariableValue) {
      return factory.getVarFactory().createVariableValue(this, (DfaVariableValue)qualifier);
    }
    DfType dfType = qualifier == null ? DfType.TOP : getFromQualifier(qualifier.getDfType());
    return factory.fromDfType(dfType.meet(getDefaultValue()));
  }

  @NotNull
  static DfType fromInitializer(@NotNull DfaVariableValue thisValue,
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

  /**
   * Returns a dfType that describes any possible value this special field may have
   *
   * @return a dfType for the default value
   */
  @NotNull
  public DfType getDefaultValue() {
    return DfTypes.intRange(JvmPsiRangeSetUtil.indexRange());
  }

  @Override
  public @NotNull DfType getDfType(@Nullable DfaVariableValue qualifier) {
    return getDefaultValue();
  }

  @NotNull
  public DfType fromConstant(@Nullable Object obj) {
    return DfType.TOP;
  }

  /**
   * @return a list of method contracts which equivalent to checking this special field for zero
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
  @NotNull
  public DfType asDfType(@NotNull DfType fieldValue) {
    DfType defaultType = this == OPTIONAL_VALUE ? DfTypes.OBJECT_OR_NULL : getDefaultValue();
    DfType clamped = fieldValue.meet(defaultType);
    if (clamped.equals(defaultType)) return DfTypes.NOT_NULL_OBJECT;
    if (clamped.equals(DfType.BOTTOM)) return DfType.BOTTOM;
    return DfTypes.customObject(TypeConstraints.TOP, DfaNullability.NOT_NULL, Mutability.UNKNOWN, this, clamped);
  }

  @Override
  @NotNull
  public DfType asDfType(@NotNull DfType fieldValue, @NotNull Project project) {
    if (this == STRING_LENGTH && fieldValue.isConst(0)) {
      return DfTypes.referenceConstant("", JavaPsiFacade.getElementFactory(project)
        .createTypeByFQClassName(JAVA_LANG_STRING));
    }
    return asDfType(fieldValue);
  }

  /**
   * Returns a special field which corresponds to given qualifier type
   * (currently it's assumed that only one special field may exist for given qualifier type)
   *
   * @param type a qualifier df type
   * @return a special field; null if no special field is available for given type
   */
  @Nullable
  public static SpecialField fromQualifierType(@NotNull DfType type) {
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

  /**
   * Returns a special field which corresponds to given qualifier
   *
   * @param value a qualifier value
   * @return a special field; null if no special field is detected to be related to given qualifier
   */
  @Nullable
  public static SpecialField fromQualifier(@NotNull DfaValue value) {
    return fromQualifierType(value.getDfType());
  }

  public @NotNull @Nls String getPresentationName() {
    return JavaAnalysisBundle.message(myTitleKey);
  }

  @Override
  public String toString() {
    return myTitle;
  }
}
