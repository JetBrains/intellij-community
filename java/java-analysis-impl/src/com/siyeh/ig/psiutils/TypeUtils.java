/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class TypeUtils {
  private static final String[] EQUAL_CONTRACT_CLASSES = {CommonClassNames.JAVA_UTIL_LIST,
    CommonClassNames.JAVA_UTIL_SET, CommonClassNames.JAVA_UTIL_MAP, CommonClassNames.JAVA_UTIL_MAP_ENTRY};

  private static final Map<PsiType, Integer> typePrecisions = new HashMap<>(7);

  static {
    typePrecisions.put(PsiTypes.byteType(), 1);
    typePrecisions.put(PsiTypes.charType(), 2);
    typePrecisions.put(PsiTypes.shortType(), 2);
    typePrecisions.put(PsiTypes.intType(), 3);
    typePrecisions.put(PsiTypes.longType(), 4);
    typePrecisions.put(PsiTypes.floatType(), 5);
    typePrecisions.put(PsiTypes.doubleType(), 6);
  }

  private TypeUtils() {}

  @Contract("_, null -> false")
  public static boolean typeEquals(@NonNls @NotNull String typeName, @Nullable PsiType targetType) {
    return targetType != null && targetType.equalsToText(typeName);
  }

  public static PsiClassType getType(@NotNull String fqName, @NotNull PsiElement context) {
    final Project project = context.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final GlobalSearchScope scope = context.getResolveScope();
    return factory.createTypeByFQClassName(fqName, scope);
  }

  public static PsiClassType getType(@NotNull PsiClass aClass) {
    return JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass);
  }

  public static PsiClassType getObjectType(@NotNull PsiElement context) {
    return getType(CommonClassNames.JAVA_LANG_OBJECT, context);
  }

  public static PsiClassType getStringType(@NotNull PsiElement context) {
    return getType(CommonClassNames.JAVA_LANG_STRING, context);
  }

  /**
   * JLS 5.1.3. Narrowing Primitive Conversion
   */
  public static boolean isNarrowingConversion(@Nullable PsiType sourceType, @Nullable PsiType targetType) {
    final Integer sourcePrecision = typePrecisions.get(sourceType);
    final Integer targetPrecision = typePrecisions.get(targetType);
    return sourcePrecision != null && targetPrecision != null && targetPrecision.intValue() < sourcePrecision.intValue();
  }

  @Contract("null -> false")
  public static boolean isJavaLangObject(@Nullable PsiType targetType) {
    return typeEquals(CommonClassNames.JAVA_LANG_OBJECT, targetType);
  }

  @Contract("null -> false")
  public static boolean isJavaLangString(@Nullable PsiType targetType) {
    return typeEquals(CommonClassNames.JAVA_LANG_STRING, targetType);
  }

  @Contract("null -> false")
  public static boolean isOptional(@Nullable PsiType type) {
    return isOptional(PsiUtil.resolveClassInClassTypeOnly(type));
  }

  @Contract("null -> false")
  public static boolean isOptional(PsiClass aClass) {
    if (aClass == null) {
      return false;
    }
    final String qualifiedName = aClass.getQualifiedName();
    return CommonClassNames.JAVA_UTIL_OPTIONAL.equals(qualifiedName)
           || OptionalUtil.OPTIONAL_DOUBLE.equals(qualifiedName)
           || OptionalUtil.OPTIONAL_INT.equals(qualifiedName)
           || OptionalUtil.OPTIONAL_LONG.equals(qualifiedName)
           || OptionalUtil.GUAVA_OPTIONAL.equals(qualifiedName);
  }

  public static boolean isExpressionTypeAssignableWith(@NotNull PsiExpression expression, @NotNull Iterable<String> rhsTypeTexts) {
    final PsiType type = expression.getType();
    if (type == null) {
      return false;
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());
    for (String rhsTypeText : rhsTypeTexts) {
      final PsiClassType rhsType = factory.createTypeByFQClassName(rhsTypeText, expression.getResolveScope());
      if (type.isAssignableFrom(rhsType)) {
        return true;
      }
    }
    return false;
  }

  @Contract("null, _ -> false")
  public static boolean expressionHasTypeOrSubtype(@Nullable PsiExpression expression, @NonNls @NotNull String typeName) {
    return expressionHasTypeOrSubtype(expression, new String[] {typeName}) != null;
  }

  //getTypeIfOneOfOrSubtype
  public static String expressionHasTypeOrSubtype(@Nullable PsiExpression expression, @NonNls String @NotNull ... typeNames) {
    if (expression == null) {
      return null;
    }
    PsiType type = FunctionalExpressionUtils.getFunctionalExpressionType(expression);
    if (type == null) {
      return null;
    }
    if (type instanceof PsiDisjunctionType) {
      type = ((PsiDisjunctionType)type).getLeastUpperBound();
    }
    for (String typeName : typeNames) {
      if (InheritanceUtil.isInheritor(type, typeName)) {
        return typeName;
      }
    }
    return null;
  }

  public static boolean expressionHasTypeOrSubtype(@Nullable PsiExpression expression, @NonNls @NotNull Iterable<String> typeNames) {
    if (expression == null) {
      return false;
    }
    PsiType type = FunctionalExpressionUtils.getFunctionalExpressionType(expression);
    if (type == null) {
      return false;
    }
    if (type instanceof PsiDisjunctionType) {
      type = ((PsiDisjunctionType)type).getLeastUpperBound();
    }
    for (String typeName : typeNames) {
      if (InheritanceUtil.isInheritor(type, typeName)) {
        return true;
      }
    }
    return false;
  }

  public static boolean variableHasTypeOrSubtype(@Nullable PsiVariable variable, @NonNls String @NotNull ... typeNames) {
    if (variable == null) {
      return false;
    }
    PsiType type = variable.getType();
    if (type instanceof PsiDisjunctionType) {
      type = ((PsiDisjunctionType)type).getLeastUpperBound();
    }
    for (String typeName : typeNames) {
      if (InheritanceUtil.isInheritor(type, typeName)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasFloatingPointType(@Nullable PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    final PsiType type = expression.getType();
    return type != null && (PsiTypes.floatType().equals(type) || PsiTypes.doubleType().equals(type));
  }

  public static boolean areConvertible(PsiType type1, PsiType type2) {
    if (TypeConversionUtil.areTypesConvertible(type1, type2)) {
      return true;
    }
    final PsiType comparedTypeErasure = TypeConversionUtil.erasure(type1);
    final PsiType comparisonTypeErasure = TypeConversionUtil.erasure(type2);
    if (comparedTypeErasure == null || comparisonTypeErasure == null ||
        TypeConversionUtil.areTypesConvertible(comparedTypeErasure, comparisonTypeErasure)) {
      if (type1 instanceof PsiClassType classType1 && type2 instanceof PsiClassType classType2) {
        final PsiType[] parameters1 = classType1.getParameters();
        final PsiType[] parameters2 = classType2.getParameters();
        if (parameters1.length != parameters2.length) {
          return ((PsiClassType)type1).isRaw() || ((PsiClassType)type2).isRaw();
        }
        for (int i = 0; i < parameters1.length; i++) {
          if (!areConvertible(parameters1[i], parameters2[i])) {
            return false;
          }
        }
      }
      return true;
    }
    return false;
  }

  public static boolean isTypeParameter(PsiType type) {
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    return aClass instanceof PsiTypeParameter;
  }

  /**
   * JLS 5.6.1 Unary Numeric Promotion
   */
  public static PsiType unaryNumericPromotion(PsiType type) {
    if (type == null) {
      return null;
    }
    if (type.equalsToText("java.lang.Byte") || type.equalsToText("java.lang.Short") ||
        type.equalsToText("java.lang.Character") || type.equalsToText("java.lang.Integer") ||
        type.equals(PsiTypes.byteType()) || type.equals(PsiTypes.shortType()) || type.equals(PsiTypes.charType())) {
      return PsiTypes.intType();
    }
    else if (type.equalsToText("java.lang.Long")) {
      return PsiTypes.longType();
    }
    else if (type.equalsToText("java.lang.Float")) {
      return PsiTypes.floatType();
    }
    else if (type.equalsToText("java.lang.Double")) {
      return PsiTypes.doubleType();
    }
    return type;
  }

  @Contract("null -> null")
  public static @Nullable String resolvedClassName(@Nullable PsiType type) {
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    return aClass == null ? null : aClass.getQualifiedName();
  }

  /**
   * Returns true if instances of two given types may be equal according to equals method contract even if they belong to
   * inconvertible classes (e.g. {@code ArrayList} and {@code LinkedList}). This method does not check any type parameters that
   * may be present.
   *
   * @param type1 first type
   * @param type2 second type
   * @return true if instances of given types may be equal
   */
  public static boolean mayBeEqualByContract(PsiType type1, PsiType type2) {
    return ContainerUtil.or(EQUAL_CONTRACT_CLASSES, className -> areConvertibleSubtypesOf(type1, type2, className));
  }

  private static boolean areConvertibleSubtypesOf(PsiType type1, PsiType type2, String className) {
    PsiClass class1 = PsiUtil.resolveClassInClassTypeOnly(type1);
    if (class1 == null) return false;
    PsiClass class2 = PsiUtil.resolveClassInClassTypeOnly(type2);
    if (class2 == null) return false;
    PsiClass superClass = JavaPsiFacade.getInstance(class1.getProject()).findClass(className, class1.getResolveScope());
    if (superClass == null) return false;
    return InheritanceUtil.isInheritorOrSelf(class1, superClass, true) && InheritanceUtil.isInheritorOrSelf(class2, superClass, true);
  }

  /**
   * Returns true if instances of two given types cannot be equal according to equals method contract
   * (e.g. {@code java.util.Set} and {@code java.util.List}).
   *
   * @param type1 first type
   * @param type2 second type
   * @return true if instances of given types cannot be equal
   */
  public static boolean cannotBeEqualByContract(PsiType type1, PsiType type2) {
    // java.util.Set and java.util.List cannot be equal by contract
    if (InheritanceUtil.isInheritor(type1, CommonClassNames.JAVA_UTIL_SET) && InheritanceUtil.isInheritor(type2, CommonClassNames.JAVA_UTIL_LIST)) {
      return true;
    }
    if (InheritanceUtil.isInheritor(type1, CommonClassNames.JAVA_UTIL_LIST) && InheritanceUtil.isInheritor(type2, CommonClassNames.JAVA_UTIL_SET)) {
      return true;
    }
    return false;
  }

  /**
   * Returns a textual representation of default value representable by given type
   * @param type type to get the default value for
   * @return the textual representation of default value
   */
  public static @NonNls @NotNull String getDefaultValue(PsiType type) {
    if (PsiTypes.intType().equals(type)) {
      return "0";
    }
    else if (PsiTypes.longType().equals(type)) {
      return "0L";
    }
    else if (PsiTypes.doubleType().equals(type)) {
      return "0.0";
    }
    else if (PsiTypes.floatType().equals(type)) {
      return "0.0F";
    }
    else if (PsiTypes.shortType().equals(type)) {
      return "(short)0";
    }
    else if (PsiTypes.byteType().equals(type)) {
      return "(byte)0";
    }
    else if (PsiTypes.booleanType().equals(type)) {
      return JavaKeywords.FALSE;
    }
    else if (PsiTypes.charType().equals(type)) {
      return "'\0'";
    }
    return JavaKeywords.NULL;
  }
}