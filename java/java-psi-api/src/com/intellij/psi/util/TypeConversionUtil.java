/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.*;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

public class TypeConversionUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.TypeConversionUtil");

  private static final boolean[][] IS_ASSIGNABLE_BIT_SET = {
    {true, true, false, true, true, true, true}, // byte
    {false, true, false, true, true, true, true}, // short
    {false, false, true, true, true, true, true}, // char
    {false, false, false, true, true, true, true}, // int
    {false, false, false, false, true, true, true}, // long
    {false, false, false, false, false, true, true}, // float
    {false, false, false, false, false, false, true}, // double
  };

  private static final TObjectIntHashMap<PsiType> TYPE_TO_RANK_MAP = new TObjectIntHashMap<>();

  public static final int BYTE_RANK = 1;
  public static final int SHORT_RANK = 2;
  public static final int CHAR_RANK = 3;
  public static final int INT_RANK = 4;
  public static final int LONG_RANK = 5;
  private static final int FLOAT_RANK = 6;
  private static final int DOUBLE_RANK = 7;
  private static final int BOOL_RANK = 10;
  private static final int STRING_RANK = 100;
  private static final int MAX_NUMERIC_RANK = DOUBLE_RANK;
  public static final PsiType NULL_TYPE = new PsiEllipsisType(PsiType.NULL) {
    @Override
    public boolean isValid() {
      return true;
    }

    @NotNull
    @Override
    public String getPresentableText(boolean annotated) {
      return "FAKE TYPE";
    }
  };
  private static final Key<PsiElement> ORIGINAL_CONTEXT = Key.create("ORIGINAL_CONTEXT");

  static {
    TYPE_TO_RANK_MAP.put(PsiType.BYTE, BYTE_RANK);
    TYPE_TO_RANK_MAP.put(PsiType.SHORT, SHORT_RANK);
    TYPE_TO_RANK_MAP.put(PsiType.CHAR, CHAR_RANK);
    TYPE_TO_RANK_MAP.put(PsiType.INT, INT_RANK);
    TYPE_TO_RANK_MAP.put(PsiType.LONG, LONG_RANK);
    TYPE_TO_RANK_MAP.put(PsiType.FLOAT, FLOAT_RANK);
    TYPE_TO_RANK_MAP.put(PsiType.DOUBLE, DOUBLE_RANK);
    TYPE_TO_RANK_MAP.put(PsiType.BOOLEAN, BOOL_RANK);
  }

  private TypeConversionUtil() { }

  /**
   * @return true if fromType can be casted to toType
   */
  public static boolean areTypesConvertible(@NotNull PsiType fromType, @NotNull PsiType toType) {
    return areTypesConvertible(fromType, toType, null);
  }

  /**
   * @return true if fromType can be casted to toType
   */
  public static boolean areTypesConvertible(@NotNull PsiType fromType, @NotNull PsiType toType, @Nullable LanguageLevel languageLevel) {
    if (fromType == toType) return true;
    final boolean fromIsPrimitive = isPrimitiveAndNotNull(fromType);
    final boolean toIsPrimitive = isPrimitiveAndNotNull(toType);
    if (fromIsPrimitive || toIsPrimitive) {
      if (isVoidType(fromType) || isVoidType(toType)) return false;
      final int fromTypeRank = getTypeRank(fromType);
      final int toTypeRank = getTypeRank(toType);
      if (!toIsPrimitive) {
        if (fromTypeRank == toTypeRank) return true;
        if (toType instanceof PsiIntersectionType) {
          for (PsiType type : ((PsiIntersectionType)toType).getConjuncts()) {
            if (!areTypesConvertible(fromType, type)) return false;
          }
          return true;
        }
        // JLS 5.5: A value of a primitive type can be cast to a reference type by boxing conversion(see 5.1.7)
        if (!(toType instanceof PsiClassType)) return false;
        PsiClass toClass = ((PsiClassType)toType).resolve();
        if (toClass == null || toClass instanceof PsiTypeParameter) return false;
        PsiClassType boxedType = ((PsiPrimitiveType)fromType).getBoxedType(toClass.getManager(), toType.getResolveScope());
        return boxedType != null && areTypesConvertible(boxedType, toType);
      }
      if (!fromIsPrimitive) {
        // 5.5. Casting Contexts
        if ((fromTypeRank == SHORT_RANK || fromTypeRank == BYTE_RANK) && toTypeRank == CHAR_RANK) return false;

        if (fromType instanceof PsiClassType) {
          if (languageLevel == null) {
            languageLevel = ((PsiClassType)fromType).getLanguageLevel();
          }

          if (languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
            final PsiClassType classType = (PsiClassType)fromType;
            final PsiClass psiClass = classType.resolve();
            if (psiClass == null) return false;
            final PsiClassType boxedType = ((PsiPrimitiveType)toType).getBoxedType(psiClass.getManager(), psiClass.getResolveScope());
            if (boxedType != null && isNarrowingReferenceConversionAllowed(fromType, boxedType)) {
              return true;
            }
          }
        }
        return fromTypeRank == toTypeRank ||
               fromTypeRank <= MAX_NUMERIC_RANK && toTypeRank <= MAX_NUMERIC_RANK && fromTypeRank < toTypeRank;
      }
      return fromTypeRank == toTypeRank ||
             fromTypeRank <= MAX_NUMERIC_RANK && toTypeRank <= MAX_NUMERIC_RANK;
    }

    //type can be casted via widening reference conversion
    if (isAssignable(toType, fromType)) return true;

    if (isNullType(fromType) || isNullType(toType)) return true;

    // or narrowing reference conversion
    return isNarrowingReferenceConversionAllowed(fromType, toType);
  }

  /**
   * see JLS 5.1.5, JLS3 5.1.6
   */
  private static boolean isNarrowingReferenceConversionAllowed(@NotNull PsiType fromType, @NotNull PsiType toType) {
    if (toType instanceof PsiPrimitiveType || fromType instanceof PsiPrimitiveType) return fromType.equals(toType);
    //Done with primitives
    if (toType instanceof PsiDiamondType || fromType instanceof PsiDiamondType) return false;
    if (toType instanceof PsiArrayType && !(fromType instanceof PsiArrayType)) {
      if (fromType instanceof PsiClassType) {
        final PsiClass resolved = ((PsiClassType)fromType).resolve();
        if (resolved instanceof PsiTypeParameter) {
          for (final PsiClassType boundType : resolved.getExtendsListTypes()) {
            if (!isNarrowingReferenceConversionAllowed(boundType, toType)) return false;
          }
          return true;
        }
      }
      if (fromType instanceof PsiCapturedWildcardType) {
        return isNarrowingReferenceConversionAllowed(((PsiCapturedWildcardType)fromType).getUpperBound(), toType);
      }
      return isAssignable(fromType, toType);
    }
    if (fromType instanceof PsiArrayType) {
      if (toType instanceof PsiClassType) {
        final PsiClass resolved = ((PsiClassType)toType).resolve();
        if (resolved instanceof PsiTypeParameter) {
          for (final PsiClassType boundType : resolved.getExtendsListTypes()) {
            if (!areTypesConvertible(fromType, boundType)) return false;
          }
          return true;
        }
      }
      return toType instanceof PsiArrayType
             && isNarrowingReferenceConversionAllowed(((PsiArrayType)fromType).getComponentType(),
                                                      ((PsiArrayType)toType).getComponentType());
    }
    //Done with array types

    if (fromType instanceof PsiIntersectionType) {
      final PsiType[] conjuncts = ((PsiIntersectionType)fromType).getConjuncts();
      for (PsiType conjunct : conjuncts) {
        if (isNarrowingReferenceConversionAllowed(conjunct, toType)) return true;
      }
      return false;
    }
    else if (toType instanceof PsiIntersectionType) {
      if (fromType instanceof PsiClassType && ((PsiClassType)fromType).getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_8)) {
        for (PsiType conjunct : ((PsiIntersectionType)toType).getConjuncts()) {
          if (!isNarrowingReferenceConversionAllowed(fromType, conjunct)) return false;
        }
        return true;
      }
      return false;
    }

    if (fromType instanceof PsiDisjunctionType) {
      return isNarrowingReferenceConversionAllowed(((PsiDisjunctionType)fromType).getLeastUpperBound(), toType);
    }
    if (toType instanceof PsiDisjunctionType) {
      return false;
    }

    if (fromType instanceof PsiWildcardType) {
      final PsiWildcardType fromWildcard = (PsiWildcardType)fromType;
      final PsiType bound = fromWildcard.getBound();
      if (bound == null) return true;
      if (fromWildcard.isSuper()) {
        return isAssignable(toType, bound);
      }
      return isNarrowingReferenceConversionAllowed(bound, toType);
    }
    if (toType instanceof PsiWildcardType) {
      final PsiWildcardType toWildcard = (PsiWildcardType)toType;
      if (toWildcard.isSuper()) return false;
      final PsiType bound = toWildcard.getBound();
      return bound == null || isNarrowingReferenceConversionAllowed(fromType, bound);
    }

    if (toType instanceof PsiCapturedWildcardType) {
      return isNarrowingReferenceConversionAllowed(fromType, ((PsiCapturedWildcardType)toType).getUpperBound());
    }
    if (fromType instanceof PsiCapturedWildcardType) {
      return isNarrowingReferenceConversionAllowed(((PsiCapturedWildcardType)fromType).getUpperBound(), toType);
    }

    if (isAssignable(fromType, toType)) return true;

    if (!(fromType instanceof PsiClassType) || !(toType instanceof PsiClassType)) return false;
    PsiClassType fromClassType = (PsiClassType)fromType;
    PsiClassType toClassType = (PsiClassType)toType;

    PsiClassType.ClassResolveResult fromResult = fromClassType.resolveGenerics();
    final PsiClass fromClass = fromResult.getElement();
    if (fromClass == null) return false;
    if (fromClass instanceof PsiTypeParameter) return isNarrowingReferenceConversionAllowed(obtainSafeSuperType((PsiTypeParameter)fromClass), toType);

    PsiClassType.ClassResolveResult toResult = toClassType.resolveGenerics();
    final PsiClass toClass = toResult.getElement();
    if (toClass == null) return false;
    if (toClass instanceof PsiTypeParameter) return isNarrowingReferenceConversionAllowed(fromType, obtainSafeSuperType((PsiTypeParameter)toClass));
    //Done with type parameters

    PsiManager manager = fromClass.getManager();
    final LanguageLevel languageLevel = toClassType.getLanguageLevel();
    if (!fromClass.isInterface()) {
      if (toClass.isInterface()) {
        return (!fromClass.hasModifierProperty(PsiModifier.FINAL) || fromClass.isInheritor(toClass, true))&&
               checkSuperTypesWithDifferentTypeArguments(toResult, fromClass, manager, fromResult.getSubstitutor(), null, languageLevel);
      }
      else {
        if (manager.areElementsEquivalent(fromClass, toClass)) {
          return areSameParameterTypes(fromClassType, toClassType);
        }

        if (toClass.isInheritor(fromClass, true)) {
          return checkSuperTypesWithDifferentTypeArguments(fromResult, toClass, manager, toResult.getSubstitutor(), null, languageLevel);
        }
        else if (fromClass.isInheritor(toClass, true)) {
          return checkSuperTypesWithDifferentTypeArguments(toResult, fromClass, manager, fromResult.getSubstitutor(), null, languageLevel);
        }

        return false;
      }
    }
    else if (!toClass.isInterface()) {
      if (!toClass.hasModifierProperty(PsiModifier.FINAL)) {
        return checkSuperTypesWithDifferentTypeArguments(fromResult, toClass, manager, toResult.getSubstitutor(), null, languageLevel);
      }
      else {
        PsiSubstitutor toSubstitutor = getMaybeSuperClassSubstitutor(fromClass, toClass, toResult.getSubstitutor(), null);
        return toSubstitutor != null && areSameArgumentTypes(fromClass, fromResult.getSubstitutor(), toSubstitutor);
      }
    }
    else if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) {
      //In jls2 check for method in both interfaces with the same signature but different return types.
      Collection<HierarchicalMethodSignature> fromClassMethodSignatures = fromClass.getVisibleSignatures();
      Collection<HierarchicalMethodSignature> toClassMethodSignatures = toClass.getVisibleSignatures();

      for (HierarchicalMethodSignature fromMethodSignature : fromClassMethodSignatures) {
        for (HierarchicalMethodSignature toMethodSignature : toClassMethodSignatures) {
          if (fromMethodSignature.equals(toMethodSignature)) {
            final PsiType fromClassReturnType = fromMethodSignature.getMethod().getReturnType();
            final PsiType toClassReturnType = toMethodSignature.getMethod().getReturnType();
            if (fromClassReturnType != null
                && toClassReturnType != null
                && !fromClassReturnType.equals(toClassReturnType)) {
              return false;
            }
          }
        }
      }
      return true;
    }
    else {
      //In jls3 check for super interface with distinct type arguments
      PsiClassType.ClassResolveResult baseResult;
      PsiClass derived;
      PsiSubstitutor derivedSubstitutor;
      if (toClass.isInheritor(fromClass, true)) {
        baseResult = fromResult;
        derived = toClass;
        derivedSubstitutor = toResult.getSubstitutor();
      }
      else {
        baseResult = toResult;
        derived = fromClass;
        derivedSubstitutor = fromResult.getSubstitutor();
      }
      return checkSuperTypesWithDifferentTypeArguments(baseResult, derived, manager, derivedSubstitutor, null, languageLevel);
    }
  }

  @NotNull
  private static PsiClassType obtainSafeSuperType(@NotNull PsiTypeParameter typeParameter) {
    final PsiClassType superType = typeParameter.getSuperTypes()[0];
    final PsiClassType.ClassResolveResult result = superType.resolveGenerics();
    final PsiClass superClass = result.getElement();
    if (superClass != null) {
      final PsiSubstitutor substitutor = result.getSubstitutor().put(typeParameter, null);
      return JavaPsiFacade.getInstance(typeParameter.getProject()).getElementFactory().createType(superClass, substitutor);
    }
    return superType;
  }

  private static boolean checkSuperTypesWithDifferentTypeArguments(@NotNull PsiClassType.ClassResolveResult baseResult,
                                                                   @NotNull PsiClass derived,
                                                                   @NotNull PsiManager manager,
                                                                   @NotNull PsiSubstitutor derivedSubstitutor,
                                                                   Set<PsiClass> visited,
                                                                   @NotNull LanguageLevel languageLevel) {
    if (visited != null && visited.contains(derived)) return true;

    if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) return true;
    PsiClass base = baseResult.getElement();
    PsiClass[] supers = derived.getSupers();
    if (manager.areElementsEquivalent(base, derived)) {
      derivedSubstitutor = getSuperClassSubstitutor(derived, derived, derivedSubstitutor);
      return areSameArgumentTypes(derived, baseResult.getSubstitutor(), derivedSubstitutor, 1);
    }
    else {
      PsiSubstitutor baseSubstitutor = getMaybeSuperClassSubstitutor(derived, base, baseResult.getSubstitutor(), null);
      if (baseSubstitutor != null) {
        derivedSubstitutor = getSuperClassSubstitutor(derived, derived, derivedSubstitutor);
        if (!areSameArgumentTypes(derived, baseSubstitutor, derivedSubstitutor)) return false;
      }
    }

    if (visited == null) visited = new THashSet<>();
    visited.add(derived);
    for (PsiClass aSuper : supers) {
      PsiSubstitutor s = getSuperClassSubstitutor(aSuper, derived, derivedSubstitutor);
      if (!checkSuperTypesWithDifferentTypeArguments(baseResult, aSuper, manager, s, visited, languageLevel)) return false;
    }

    return true;
  }

  private static boolean areSameParameterTypes(@NotNull PsiClassType type1, @NotNull PsiClassType type2) {
    PsiClassType.ClassResolveResult resolveResult1 = type1.resolveGenerics();
    PsiClassType.ClassResolveResult resolveResult2 = type2.resolveGenerics();
    final PsiClass aClass = resolveResult1.getElement();
    final PsiClass bClass = resolveResult2.getElement();
    return aClass != null &&
           bClass != null &&
           aClass.getManager().areElementsEquivalent(aClass, bClass) &&
           areSameArgumentTypes(aClass, resolveResult1.getSubstitutor(), resolveResult2.getSubstitutor(), 1);
  }

  private static boolean areSameArgumentTypes(@NotNull PsiClass aClass, @NotNull PsiSubstitutor substitutor1, @NotNull PsiSubstitutor substitutor2) {
    return areSameArgumentTypes(aClass, substitutor1, substitutor2, 0);
  }

  private static boolean areSameArgumentTypes(@NotNull PsiClass aClass,
                                              @NotNull PsiSubstitutor substitutor1,
                                              @NotNull PsiSubstitutor substitutor2,
                                              int level) {
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(aClass)) {
      PsiType typeArg1 = substitutor1.substitute(typeParameter);
      PsiType typeArg2 = substitutor2.substitute(typeParameter);
      if (typeArg1 == null || typeArg2 == null) return true;
      if (TypesDistinctProver.provablyDistinct(typeArg1, typeArg2, level)) return false;
    }

    return true;
  }

  public static boolean isPrimitiveAndNotNull(PsiType type) {
    if (type instanceof PsiCapturedWildcardType) {
      return isPrimitiveAndNotNull(((PsiCapturedWildcardType)type).getUpperBound());
    }
    return type instanceof PsiPrimitiveType && !isNullType(type);
  }

  public static boolean isEnumType(PsiType type) {
    if (type instanceof PsiCapturedWildcardType) {
      return isEnumType(((PsiCapturedWildcardType)type).getUpperBound());
    }
    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      return psiClass != null && psiClass.isEnum();
    }
    return false;
  }

  public static boolean isNullType(PsiType type) {
    return PsiType.NULL.equals(type);
  }

  public static boolean isFloatOrDoubleType(PsiType type) {
    return isFloatType(type) || isDoubleType(type);
  }
  public static boolean isDoubleType(PsiType type) {
    if (type instanceof PsiCapturedWildcardType) {
      return isDoubleType(((PsiCapturedWildcardType)type).getUpperBound());
    }
    return PsiType.DOUBLE.equals(type) || PsiType.DOUBLE.equals(PsiPrimitiveType.getUnboxedType(type));
  }

  public static boolean isFloatType(PsiType type) {
    if (type instanceof PsiCapturedWildcardType) {
      return isFloatType(((PsiCapturedWildcardType)type).getUpperBound());
    }
    return PsiType.FLOAT.equals(type) || PsiType.FLOAT.equals(PsiPrimitiveType.getUnboxedType(type));
  }

  public static boolean isLongType(PsiType type) {
    if (type instanceof PsiCapturedWildcardType) {
      return isLongType(((PsiCapturedWildcardType)type).getUpperBound());
    }
    return PsiType.LONG.equals(type) || PsiType.LONG.equals(PsiPrimitiveType.getUnboxedType(type));
  }

  public static boolean isVoidType(PsiType type) {
    return PsiType.VOID.equals(type);
  }

  public static boolean isBooleanType(@Nullable PsiType type) {
    if (type instanceof PsiCapturedWildcardType) {
      return isBooleanType(((PsiCapturedWildcardType)type).getUpperBound());
    }
    return PsiType.BOOLEAN.equals(type) || PsiType.BOOLEAN.equals(PsiPrimitiveType.getUnboxedType(type));
  }

  public static boolean isNumericType(int typeRank) {
    return typeRank <= MAX_NUMERIC_RANK;
  }
  public static boolean isNumericType(PsiType type) {
    return type != null && isNumericType(getTypeRank(type));
  }
  public static boolean isIntegralNumberType(PsiType type) {
    return type != null && getTypeRank(type) <= LONG_RANK;
  }

  /**
   * @return 1..MAX_NUMERIC_TYPE if type is primitive numeric type,
   *         BOOL_TYPE for boolean,
   *         STRING_TYPE for String,
   *         Integer.MAX_VALUE for others
   */
  public static int getTypeRank(@NotNull PsiType type) {
    if (type instanceof PsiCapturedWildcardType) {
      type = ((PsiCapturedWildcardType)type).getUpperBound();
    }
    PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(type);
    if (unboxedType != null) {
      type = unboxedType;
    }

    int rank = TYPE_TO_RANK_MAP.get(type);
    if (rank != 0) return rank;
    if (type.equalsToText(JAVA_LANG_STRING)) return STRING_RANK;
    return Integer.MAX_VALUE;
  }

  /**
   * @param tokenType JavaTokenType enumeration
   * @param strict    true if operator result type should be convertible to the left operand
   * @return true if lOperand operator rOperand expression is syntactically correct
   */
  public static boolean isBinaryOperatorApplicable(IElementType tokenType,
                                                   PsiExpression lOperand,
                                                   PsiExpression rOperand,
                                                   boolean strict) {
    if (lOperand == null || rOperand == null) return true;
    final PsiType ltype = lOperand.getType();
    final PsiType rtype = rOperand.getType();
    return isBinaryOperatorApplicable(tokenType, ltype, rtype, strict);
  }

  public static boolean isBinaryOperatorApplicable(final IElementType tokenType, final PsiType ltype, final PsiType rtype, final boolean strict) {
    if (ltype == null || rtype == null) return true;
    int resultTypeRank = BOOL_RANK;
    boolean isApplicable = false;
    final int ltypeRank = getTypeRank(ltype);
    final int rtypeRank = getTypeRank(rtype);
    Label:
    if (tokenType == JavaTokenType.LT || tokenType == JavaTokenType.LE || tokenType == JavaTokenType.GT || tokenType == JavaTokenType.GE) {
      if (isPrimitiveAndNotNullOrWrapper(ltype) && isPrimitiveAndNotNullOrWrapper(rtype)) {
        isApplicable = ltypeRank <= MAX_NUMERIC_RANK && rtypeRank <= MAX_NUMERIC_RANK;
      }
    }
    else if (tokenType == JavaTokenType.EQEQ || tokenType == JavaTokenType.NE) {
      if (isPrimitiveAndNotNullOrWrapper(ltype) && isPrimitiveAndNotNullOrWrapper(rtype) &&
          (isPrimitiveAndNotNull(ltype) || isPrimitiveAndNotNull(rtype))) {
        isApplicable = ltypeRank <= MAX_NUMERIC_RANK && rtypeRank <= MAX_NUMERIC_RANK
                       || ltypeRank == BOOL_RANK && rtypeRank == BOOL_RANK;
      }
      else {
        if (isPrimitiveAndNotNull(ltype)) {
          if (rtype instanceof PsiClassType) {
            final LanguageLevel languageLevel = ((PsiClassType)rtype).getLanguageLevel();
            if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5) &&
                !languageLevel.isAtLeast(LanguageLevel.JDK_1_8) &&
                areTypesConvertible(ltype, rtype)) {
              return true;
            }
          }
          return false;
        }
        if (isPrimitiveAndNotNull(rtype)) {
          if (ltype instanceof PsiClassType) {
            final LanguageLevel level = ((PsiClassType)ltype).getLanguageLevel();
            if (level.isAtLeast(LanguageLevel.JDK_1_7) && !level.isAtLeast(LanguageLevel.JDK_1_8) && areTypesConvertible(rtype, ltype)) {
              return true;
            }
          }
          return false;
        }
        isApplicable = areTypesConvertible(ltype, rtype) || areTypesConvertible(rtype, ltype);
      }
    }
    else if (tokenType == JavaTokenType.PLUS) {
      if (ltype.equalsToText(JAVA_LANG_STRING)) {
        isApplicable = !isVoidType(rtype);
        resultTypeRank = STRING_RANK;
        break Label;
      }
      else if (rtype.equalsToText(JAVA_LANG_STRING)) {
        isApplicable = !isVoidType(ltype);
        resultTypeRank = STRING_RANK;
        break Label;
      }
      //fallthrough




      if (isPrimitiveAndNotNullOrWrapper(ltype) && isPrimitiveAndNotNullOrWrapper(rtype)) {
        resultTypeRank = Math.max(ltypeRank, rtypeRank);
        isApplicable = ltypeRank <= MAX_NUMERIC_RANK && rtypeRank <= MAX_NUMERIC_RANK;
      }
    }
    else if (tokenType == JavaTokenType.ASTERISK || tokenType == JavaTokenType.DIV || tokenType == JavaTokenType.PERC ||
             tokenType == JavaTokenType.MINUS) {
      if (isPrimitiveAndNotNullOrWrapper(ltype) && isPrimitiveAndNotNullOrWrapper(rtype)) {
        resultTypeRank = Math.max(ltypeRank, rtypeRank);
        isApplicable = ltypeRank <= MAX_NUMERIC_RANK && rtypeRank <= MAX_NUMERIC_RANK;
      }
    }
    else if (tokenType == JavaTokenType.LTLT || tokenType == JavaTokenType.GTGT || tokenType == JavaTokenType.GTGTGT) {
      if (isPrimitiveAndNotNullOrWrapper(ltype) && isPrimitiveAndNotNullOrWrapper(rtype)) {
        isApplicable = ltypeRank <= LONG_RANK && rtypeRank <= LONG_RANK;
        resultTypeRank = INT_RANK;
      }
    }
    else if (tokenType == JavaTokenType.AND || tokenType == JavaTokenType.OR || tokenType == JavaTokenType.XOR) {
      if (isPrimitiveAndNotNullOrWrapper(ltype) && isPrimitiveAndNotNullOrWrapper(rtype)) {
        isApplicable = ltypeRank <= LONG_RANK && rtypeRank <= LONG_RANK
                       || isBooleanType(ltype) && isBooleanType(rtype);
        resultTypeRank = ltypeRank <= LONG_RANK ? INT_RANK : BOOL_RANK;
      }
    }
    else if (tokenType == JavaTokenType.ANDAND || tokenType == JavaTokenType.OROR) {
      if (isPrimitiveAndNotNullOrWrapper(ltype) && isPrimitiveAndNotNullOrWrapper(rtype)) {
        isApplicable = isBooleanType(ltype) && isBooleanType(rtype);
      }
    }
    if (isApplicable && strict) {
      if (resultTypeRank > MAX_NUMERIC_RANK) {
        isApplicable = ltypeRank == resultTypeRank || ltype.equalsToText(CommonClassNames.JAVA_LANG_OBJECT);
      }
      else {
        isApplicable = ltypeRank <= MAX_NUMERIC_RANK;
      }
    }
    return isApplicable;
  }

  public static boolean isPrimitiveAndNotNullOrWrapper(PsiType type) {
    if (type instanceof PsiCapturedWildcardType) {
      return isPrimitiveAndNotNullOrWrapper(((PsiCapturedWildcardType)type).getUpperBound());
    }
    if (type instanceof PsiClassType) {
      return PsiPrimitiveType.getUnboxedType(type) != null;
    }

    return isPrimitiveAndNotNull(type);
  }

  public static boolean isUnaryOperatorApplicable(@NotNull PsiJavaToken token, PsiExpression operand) {
    if (operand == null) return false;
    PsiType type = operand.getType();
    return type != null && isUnaryOperatorApplicable(token, type);
  }

  public static boolean isUnaryOperatorApplicable(@NotNull PsiJavaToken token, @NotNull PsiType type) {
    IElementType i = token.getTokenType();
    int typeRank = getTypeRank(type);
    if (i == JavaTokenType.MINUSMINUS || i == JavaTokenType.PLUSPLUS) {
      return typeRank <= MAX_NUMERIC_RANK;
    }
    if (i == JavaTokenType.MINUS || i == JavaTokenType.PLUS) {
      return typeRank <= MAX_NUMERIC_RANK;
    }
    if (i == JavaTokenType.TILDE) {
      return typeRank <= LONG_RANK;
    }
    if (i == JavaTokenType.EXCL) {
      return typeRank == BOOL_RANK;
    }
    LOG.error("unknown token: " + token);
    return true;
  }

  /**
   * @return true if expression can be the left part of assignment operator
   */
  public static boolean isLValue(PsiExpression element) {
    if (element instanceof PsiReferenceExpression) {
      final PsiReferenceExpression expression = (PsiReferenceExpression)element;
      final PsiElement resolved = expression.resolve();
      return resolved instanceof PsiVariable;
    }
    if (element instanceof PsiParenthesizedExpression) {
      return isLValue(((PsiParenthesizedExpression)element).getExpression());
    }
    if (element instanceof PsiArrayAccessExpression) {
      final PsiArrayAccessExpression arrayAccessExpression = (PsiArrayAccessExpression)element;
      final PsiExpression arrayExpression = arrayAccessExpression.getArrayExpression();
      final PsiType type = arrayExpression.getType();
      if (type == null || !(type instanceof PsiArrayType)) return false;
      final PsiExpression indexExpression = arrayAccessExpression.getIndexExpression();
      if (indexExpression == null) return false;
      final PsiType indexType = indexExpression.getType();
      if (indexType == null) return false;
      if (getTypeRank(indexType) <= INT_RANK) return true;
    }
    return false;
  }


  /**
   * JLS 5.2
   */
  public static boolean areTypesAssignmentCompatible(PsiType lType, PsiExpression rExpr) {
    if (lType == null || rExpr == null) return true;
    PsiType rType = rExpr.getType();
    if (rType == null) return false;
    if (isAssignable(lType, rType)) return true;
    if (lType instanceof PsiClassType) {
        lType = PsiPrimitiveType.getUnboxedType(lType);
        if (lType == null) return false;
    }

    final int rTypeRank = getTypeRank(rType);
    if (lType instanceof PsiPrimitiveType
        && rType instanceof PsiPrimitiveType
        && rTypeRank >= BYTE_RANK && rTypeRank <= INT_RANK) {
      final Object rValue = JavaPsiFacade.getInstance(rExpr.getProject()).getConstantEvaluationHelper().computeConstantExpression(rExpr);
      final long value;
      if (rValue instanceof Number) {
        value = ((Number)rValue).longValue();
      }
      else if (rValue instanceof Character) {
        value = (Character)rValue;
      }
      else {
        return false;
      }

      if (PsiType.BYTE.equals(lType)) {
        return -128 <= value && value <= 127;
      }
      else if (PsiType.SHORT.equals(lType)) {
        return -32768 <= value && value <= 32767;
      }
      else if (PsiType.CHAR.equals(lType)) {
        return 0 <= value && value <= 0xFFFF;
      }
    }
    return false;
  }

  /**
   * Checks whether values of one type can be assigned to another
   *
   * @param left  type to assign to
   * @param right type of value
   * @return true if value of type {@code right} can be assigned to an l-value of
   *         type {@code left}
   */
  public static boolean isAssignable(@NotNull PsiType left, @NotNull PsiType right) {
    return isAssignable(left, right, true);
  }

  public static boolean isAssignable(@NotNull PsiType left, @NotNull PsiType right, boolean allowUncheckedConversion) {
    return isAssignable(left, right, allowUncheckedConversion, true);
  }

  private static boolean isAssignable(@NotNull PsiType left,
                                      @NotNull PsiType right,
                                      boolean allowUncheckedConversion,
                                      boolean capture) {
    if (left == right || left.equals(right)) return true;

    if (isNullType(right)) {
      return !(left instanceof PsiPrimitiveType) || isNullType(left);
    }

    if (right instanceof PsiMethodReferenceType) {
      final PsiMethodReferenceExpression methodReferenceExpression = ((PsiMethodReferenceType)right).getExpression();
      if (left instanceof PsiLambdaExpressionType) {
        final PsiType rType = methodReferenceExpression.getFunctionalInterfaceType();
        final PsiType lType = ((PsiLambdaExpressionType)left).getExpression().getFunctionalInterfaceType();
        return Comparing.equal(rType, lType);
      } else if (left instanceof PsiMethodReferenceType) {
        final PsiType rType = methodReferenceExpression.getFunctionalInterfaceType();
        final PsiType lType = ((PsiMethodReferenceType)left).getExpression().getFunctionalInterfaceType();
        return Comparing.equal(rType, lType);
      }
      return !(left instanceof PsiArrayType) && methodReferenceExpression.isAcceptable(left);
    }
    if (right instanceof PsiLambdaExpressionType) {
      final PsiLambdaExpression rLambdaExpression = ((PsiLambdaExpressionType)right).getExpression();
      if (left instanceof PsiLambdaExpressionType) {
        final PsiLambdaExpression lLambdaExpression = ((PsiLambdaExpressionType)left).getExpression();
        final PsiType rType = rLambdaExpression.getFunctionalInterfaceType();
        final PsiType lType = lLambdaExpression.getFunctionalInterfaceType();
        return Comparing.equal(rType, lType);
      }
      return !(left instanceof PsiArrayType) && rLambdaExpression.isAcceptable(left);
    }

    if (left instanceof PsiIntersectionType) {
      PsiType[] conjuncts = ((PsiIntersectionType)left).getConjuncts();
      for (PsiType conjunct : conjuncts) {
        if (!isAssignable(conjunct, right, allowUncheckedConversion, capture)) return false;
      }
      return true;
    }
    if (right instanceof PsiIntersectionType) {
      PsiType[] conjuncts = ((PsiIntersectionType)right).getConjuncts();
      for (PsiType conjunct : conjuncts) {
        if (isAssignable(left, conjunct, allowUncheckedConversion, capture)) return true;
      }
      return false;
    }

    if (right instanceof PsiCapturedWildcardType) {
      return isAssignable(left, ((PsiCapturedWildcardType)right).getUpperBound(capture), allowUncheckedConversion, capture);
    }

    if (left instanceof PsiCapturedWildcardType) {
      return left.equals(right) || isAssignable(((PsiCapturedWildcardType)left).getLowerBound(), right, allowUncheckedConversion, capture);
    }

    if (left instanceof PsiWildcardType) {
      return isAssignableToWildcard((PsiWildcardType)left, right);
    }
    if (right instanceof PsiWildcardType) {
      return isAssignableFromWildcard(left, (PsiWildcardType)right);
    }
    if (right instanceof PsiArrayType) {
      if (!(left instanceof PsiArrayType)) {
        if (left instanceof PsiPrimitiveType || PsiUtil.resolveClassInType(left) == null) return false;
        PsiClass lClass = PsiUtil.resolveClassInType(left);
        if (lClass == null) return false;
        if (lClass.isInterface()) {
          final String qualifiedName = lClass.getQualifiedName();
          return "java.io.Serializable".equals(qualifiedName) || "java.lang.Cloneable".equals(qualifiedName);
        }
        else {
          return left.equalsToText(CommonClassNames.JAVA_LANG_OBJECT);
        }
      }
      PsiType lCompType = ((PsiArrayType)left).getComponentType();
      PsiType rCompType = ((PsiArrayType)right).getComponentType();
      if (lCompType instanceof PsiPrimitiveType) {
        return lCompType.equals(rCompType);
      }
      return !(rCompType instanceof PsiPrimitiveType) && isAssignable(lCompType, rCompType, allowUncheckedConversion, capture);
    }

    if (left instanceof PsiDisjunctionType) {
      for (PsiType type : ((PsiDisjunctionType)left).getDisjunctions()) {
        if (isAssignable(type, right, allowUncheckedConversion, capture)) return true;
      }
      return false;
    }
    if (right instanceof PsiDisjunctionType) {
      return isAssignable(left, ((PsiDisjunctionType)right).getLeastUpperBound(), allowUncheckedConversion, capture);
    }

    if (left instanceof PsiArrayType) return false;
    if (right instanceof PsiPrimitiveType) {
      if (isVoidType(right)) return false;
      if (!(left instanceof PsiPrimitiveType)) {
        return left instanceof PsiClassType && isBoxable((PsiClassType)left, (PsiPrimitiveType)right);
      }
      int leftTypeIndex = TYPE_TO_RANK_MAP.get(left) - 1;
      int rightTypeIndex = TYPE_TO_RANK_MAP.get(right) - 1;
      return leftTypeIndex >= 0 &&
             rightTypeIndex >= 0 &&
             rightTypeIndex < IS_ASSIGNABLE_BIT_SET.length &&
             leftTypeIndex < IS_ASSIGNABLE_BIT_SET.length &&
             IS_ASSIGNABLE_BIT_SET[rightTypeIndex][leftTypeIndex];
    }
    if (!(right instanceof PsiClassType)) {
      return false; // must be TypeCook's PsiTypeVariable
    }
    if (left instanceof PsiPrimitiveType) {
      return isUnboxable((PsiPrimitiveType)left, (PsiClassType)right, new HashSet<>());
    }
    final PsiClassType.ClassResolveResult leftResult = PsiUtil.resolveGenericsClassInType(left);
    final PsiClassType.ClassResolveResult rightResult = PsiUtil.resolveGenericsClassInType(right);
    if (leftResult.getElement() == null || rightResult.getElement() == null) {
      if (leftResult.getElement() != rightResult.getElement()) return false;
      // let's suppose 2 unknown classes, which could be the same to be the same
      String lText = left.getPresentableText();
      String rText = right.getPresentableText();
      if (lText.equals(rText)) return true;
      if (lText.length() > rText.length() && lText.endsWith(rText) &&
          lText.charAt(lText.length() - rText.length() - 1) == '.') {
        return true;
      }
      return rText.length() > lText.length()
             && rText.endsWith(lText)
             && rText.charAt(rText.length() - lText.length() - 1) == '.';
    }
    return isClassAssignable(leftResult, rightResult, allowUncheckedConversion, left.getResolveScope(), capture);
  }

  private static boolean isAssignableFromWildcard(@NotNull PsiType left, @NotNull PsiWildcardType rightWildcardType) {
    if (rightWildcardType.isSuper()) {
      final PsiClass aClass = PsiUtil.resolveClassInType(rightWildcardType.getSuperBound());
      if (aClass instanceof PsiTypeParameter) {
        final PsiClassType[] types = aClass.getExtendsListTypes();
        for (PsiClassType type : types) {
          if (isAssignable(left, type)) return true;
        }
      }
    }
    return isAssignable(left, rightWildcardType.getExtendsBound());
  }

  private static boolean isAssignableToWildcard(@NotNull PsiWildcardType wildcardType, @NotNull PsiType right) {
    if (wildcardType.isSuper()) {
      return isAssignable(wildcardType.getSuperBound(), right);
    }
    return isAssignable(wildcardType.getExtendsBound(), right);
  }

  private static boolean isUnboxable(@NotNull PsiPrimitiveType left, @NotNull PsiClassType right, @NotNull Set<PsiClassType> types) {
    if (!right.getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_5)) return false;
    final PsiClass psiClass = right.resolve();
    if (psiClass == null) return false;

    if (psiClass instanceof PsiTypeParameter) {
      for (PsiClassType bound : psiClass.getExtendsListTypes()) {
        if (types.add(bound) && isUnboxable(left, bound, types)) {
          return true;
        }
      }
      return false;
    }

    final PsiPrimitiveType rightUnboxedType = PsiPrimitiveType.getUnboxedType(right);
    return rightUnboxedType != null && isAssignable(left, rightUnboxedType);
  }

  public static boolean boxingConversionApplicable(final PsiType left, final PsiType right) {
    if (left instanceof PsiPrimitiveType && !PsiType.NULL.equals(left)) {
      return right instanceof PsiClassType && isAssignable(left, right);
    }

    if (left instanceof PsiIntersectionType) {
      for (PsiType lConjunct : ((PsiIntersectionType)left).getConjuncts()) {
        if (!boxingConversionApplicable(lConjunct, right)) return false;
      }
      return true;
    }

    return left instanceof PsiClassType
              && right instanceof PsiPrimitiveType
              && !PsiType.NULL.equals(right)
              && isAssignable(left, right);
  }

  private static final Key<CachedValue<Set<String>>> POSSIBLE_BOXED_HOLDER_TYPES = Key.create("Types that may be possibly assigned from primitive ones");

  private static boolean isBoxable(@NotNull PsiClassType left, @NotNull PsiPrimitiveType right) {
    if (!left.getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_5)) return false;
    final PsiClass psiClass = left.resolve();
    if (psiClass == null) return false;

    final String qname = psiClass.getQualifiedName();
    if (qname == null || !(psiClass instanceof PsiTypeParameter || getAllBoxedTypeSupers(psiClass).contains(qname))) {
      return false;
    }

    final PsiClassType rightBoxed = right.getBoxedType(psiClass.getManager(), left.getResolveScope());
    return rightBoxed != null && isAssignable(left, rightBoxed);
  }

  @NotNull
  private static Set<String> getAllBoxedTypeSupers(@NotNull PsiClass psiClass) {
    PsiManager manager = psiClass.getManager();
    final Project project = psiClass.getProject();
    CachedValue<Set<String>> boxedHolderTypes = project.getUserData(POSSIBLE_BOXED_HOLDER_TYPES);
    if (boxedHolderTypes == null) {
      project.putUserData(POSSIBLE_BOXED_HOLDER_TYPES, boxedHolderTypes = CachedValuesManager.getManager(manager.getProject()).createCachedValue(
        () -> {
          final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
          final Set<String> set = new THashSet<>();
          for (final String qname : PsiPrimitiveType.getAllBoxedTypeNames()) {
            final PsiClass boxedClass = facade.findClass(qname, GlobalSearchScope.allScope(project));
            InheritanceUtil.processSupers(boxedClass, true, psiClass1 -> {
              ContainerUtil.addIfNotNull(set, psiClass1.getQualifiedName());
              return true;
            });
          }
          return CachedValueProvider.Result.create(set, ProjectRootModificationTracker.getInstance(project));
        }, false));
    }

    return boxedHolderTypes.getValue();
  }

  private static boolean isClassAssignable(@NotNull PsiClassType.ClassResolveResult leftResult,
                                           @NotNull PsiClassType.ClassResolveResult rightResult,
                                           boolean allowUncheckedConversion, 
                                           GlobalSearchScope resolveScope, 
                                           boolean capture) {
    final PsiClass leftClass = leftResult.getElement();
    final PsiClass rightClass = rightResult.getElement();
    if (leftClass == null || rightClass == null) return false;

    PsiSubstitutor superSubstitutor = JavaClassSupers.getInstance().getSuperClassSubstitutor(leftClass, rightClass, resolveScope,
                                                                                             rightResult.getSubstitutor());
    return superSubstitutor != null && typeParametersAgree(leftResult, rightResult, allowUncheckedConversion, superSubstitutor, capture);
  }

  private static boolean typeParametersAgree(@NotNull PsiClassType.ClassResolveResult leftResult,
                                             @NotNull PsiClassType.ClassResolveResult rightResult,
                                             boolean allowUncheckedConversion, PsiSubstitutor superSubstitutor,
                                             boolean capture) {
    PsiSubstitutor rightSubstitutor = rightResult.getSubstitutor();
    PsiClass leftClass = leftResult.getElement();
    PsiClass rightClass = rightResult.getElement();

    Iterator<PsiTypeParameter> li = PsiUtil.typeParametersIterator(leftClass);

    if (!li.hasNext()) return true;
    PsiSubstitutor leftSubstitutor = leftResult.getSubstitutor();

    if (!leftClass.getManager().areElementsEquivalent(leftClass, rightClass)) {
      rightSubstitutor = superSubstitutor;
      rightClass = leftClass;
    }
    else if (!PsiUtil.typeParametersIterator(rightClass).hasNext()) return true;

    Iterator<PsiTypeParameter> ri = PsiUtil.typeParametersIterator(rightClass);
    while (li.hasNext()) {
      if (!ri.hasNext()) return false;
      PsiTypeParameter lp = li.next();
      PsiTypeParameter rp = ri.next();
      final PsiType typeLeft = leftSubstitutor.substitute(lp);
      if (typeLeft == null) continue;
      final PsiType typeRight = PsiCapturedWildcardType.isCapture() && capture
                                ? rightSubstitutor.substituteWithBoundsPromotion(rp)
                                : rightSubstitutor.substitute(rp);
      if (typeRight == null) {
        // compatibility feature: allow to assign raw types to generic ones
        return allowUncheckedConversion;
      }
      if (!typesAgree(typeLeft, typeRight, allowUncheckedConversion)) {
        return false;
      }
    }
    return true;
  }

  private static final RecursionGuard ourGuard = RecursionManager.createGuard("isAssignable");

  public static boolean typesAgree(final @NotNull PsiType typeLeft,
                                   final @NotNull PsiType typeRight,
                                   final boolean allowUncheckedConversion) {
    if (typeLeft instanceof PsiWildcardType) {
      final PsiWildcardType leftWildcard = (PsiWildcardType)typeLeft;
      final PsiType leftBound = leftWildcard.getBound();
      if (leftBound == null) return true;
      if (leftBound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
        if (!leftWildcard.isSuper()) return true;
        if (typeRight.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return true;
      }

      if (typeRight instanceof PsiWildcardType) {
        final PsiWildcardType rightWildcard = (PsiWildcardType)typeRight;
        if (leftWildcard.isExtends()) {
          return rightWildcard.isExtends() && isAssignable(leftBound, rightWildcard.getBound(), allowUncheckedConversion, false);
        }
        else { //isSuper
          if (rightWildcard.isSuper()) {
            final Boolean assignable = ourGuard.doPreventingRecursion(rightWildcard, true,
                                                                      (NotNullComputable<Boolean>)() -> isAssignable(rightWildcard.getBound(), leftBound, allowUncheckedConversion, false));
            if (assignable != null && assignable) {
              return true;
            }
          }
          return false;
        }
      }
      else {
        if (leftWildcard.isExtends()) {
          return isAssignable(leftBound, typeRight, false, false);
        }
        else { // isSuper
          final Boolean assignable = ourGuard.doPreventingRecursion(leftWildcard, true,
                                                                    (NotNullComputable<Boolean>)() -> isAssignable(typeRight, leftBound, false, false));
          return assignable == null || assignable.booleanValue(); 
        }
      }
    }
    else {
      return typeLeft.equals(typeRight);
    }
  }

  @Nullable
  public static PsiSubstitutor getClassSubstitutor(@NotNull PsiClass superClassCandidate,
                                                   @NotNull PsiClass derivedClassCandidate,
                                                   @NotNull PsiSubstitutor derivedSubstitutor) {
    if (superClassCandidate.getManager().areElementsEquivalent(superClassCandidate, derivedClassCandidate)) {
      PsiTypeParameter[] baseParams = superClassCandidate.getTypeParameters();
      PsiTypeParameter[] derivedParams = derivedClassCandidate.getTypeParameters();
      if (baseParams.length > 0 && derivedParams.length == 0) {
        return JavaPsiFacade.getInstance(superClassCandidate.getProject()).getElementFactory().createRawSubstitutor(superClassCandidate);
      }
      return derivedSubstitutor;
    }
    return getMaybeSuperClassSubstitutor(superClassCandidate, derivedClassCandidate, derivedSubstitutor, null);
  }

  /**
   * Calculates substitutor that binds type parameters in {@code superClass} with
   * values that they have in {@code derivedClass}, given that type parameters in
   * {@code derivedClass} are bound by {@code derivedSubstitutor}.
   * {@code superClass} must be a super class/interface of {@code derivedClass} (as in
   * {@code InheritanceUtil.isInheritorOrSelf(derivedClass, superClass, true)}
   *
   * @return substitutor (never returns {@code null})
   * @see PsiClass#isInheritor(PsiClass, boolean)
   * @see InheritanceUtil#isInheritorOrSelf(PsiClass, PsiClass, boolean)
   */
  @NotNull
  public static PsiSubstitutor getSuperClassSubstitutor(@NotNull PsiClass superClass,
                                                        @NotNull PsiClass derivedClass,
                                                        @NotNull PsiSubstitutor derivedSubstitutor) {
    if (!superClass.hasTypeParameters() && superClass.getContainingClass() == null) return PsiSubstitutor.EMPTY; //optimization and protection against EJB queer hierarchy

    Set<PsiClass> visited = new THashSet<>();
    PsiSubstitutor substitutor = getMaybeSuperClassSubstitutor(superClass, derivedClass, derivedSubstitutor, visited);

    if (substitutor == null) {
      JavaClassSupers.getInstance().reportHierarchyInconsistency(superClass, derivedClass);
      return PsiSubstitutor.EMPTY;
    }
    return substitutor;
  }

  // the same as getSuperClassSubstitutor() but can return null, which means that classes were not inheritors
  @Nullable
  public static PsiSubstitutor getMaybeSuperClassSubstitutor(@NotNull PsiClass superClass,
                                                             @NotNull PsiClass derivedClass,
                                                             @NotNull PsiSubstitutor derivedSubstitutor,
                                                             @Nullable Set<PsiClass> visited) {
    return JavaClassSupers.getInstance().getSuperClassSubstitutor(superClass, derivedClass, derivedClass.getResolveScope(), derivedSubstitutor);
  }

  @NotNull
  public static PsiSubstitutor getSuperClassSubstitutor(@NotNull PsiClass superClass, @NotNull PsiClassType classType) {
      final PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
      return getSuperClassSubstitutor(superClass, classResolveResult.getElement(), classResolveResult.getSubstitutor());
  }

  /**
   * see JLS 5.6.2
   */
  @NotNull
  public static PsiType binaryNumericPromotion(PsiType type1, PsiType type2) {
    if (isDoubleType(type1)) return unbox(type1);
    if (isDoubleType(type2)) return unbox(type2);
    if (isFloatType(type1)) return unbox(type1);
    if (isFloatType(type2)) return unbox(type2);
    if (isLongType(type1)) return unbox(type1);
    if (isLongType(type2)) return unbox(type2);

    return PsiType.INT;
  }

  @NotNull
  private static PsiType unbox(@NotNull PsiType type) {
    if (type instanceof PsiPrimitiveType) return type;
    if (type instanceof PsiClassType) {
      type = PsiPrimitiveType.getUnboxedType(type);
      LOG.assertTrue(type != null);
      return type;
    }
    LOG.error("Invalid type for unboxing "+type);
    return type;
  }

  private static final Set<String> INTEGER_NUMBER_TYPES = new THashSet<>(5);

  static {
    INTEGER_NUMBER_TYPES.add(PsiType.BYTE.getCanonicalText());
    INTEGER_NUMBER_TYPES.add(PsiType.CHAR.getCanonicalText());
    INTEGER_NUMBER_TYPES.add(PsiType.LONG.getCanonicalText());
    INTEGER_NUMBER_TYPES.add(PsiType.INT.getCanonicalText());
    INTEGER_NUMBER_TYPES.add(PsiType.SHORT.getCanonicalText());
  }

  private static final Set<String> PRIMITIVE_TYPES = new THashSet<>(9);

  static {
    PRIMITIVE_TYPES.add(PsiType.VOID.getCanonicalText());
    PRIMITIVE_TYPES.add(PsiType.BYTE.getCanonicalText());
    PRIMITIVE_TYPES.add(PsiType.CHAR.getCanonicalText());
    PRIMITIVE_TYPES.add(PsiType.DOUBLE.getCanonicalText());
    PRIMITIVE_TYPES.add(PsiType.FLOAT.getCanonicalText());
    PRIMITIVE_TYPES.add(PsiType.LONG.getCanonicalText());
    PRIMITIVE_TYPES.add(PsiType.INT.getCanonicalText());
    PRIMITIVE_TYPES.add(PsiType.SHORT.getCanonicalText());
    PRIMITIVE_TYPES.add(PsiType.BOOLEAN.getCanonicalText());
  }

  private static final Set<String> PRIMITIVE_WRAPPER_TYPES = new THashSet<>(8);

  static {
    PRIMITIVE_WRAPPER_TYPES.add("java.lang.Byte");
    PRIMITIVE_WRAPPER_TYPES.add("java.lang.Character");
    PRIMITIVE_WRAPPER_TYPES.add("java.lang.Double");
    PRIMITIVE_WRAPPER_TYPES.add("java.lang.Float");
    PRIMITIVE_WRAPPER_TYPES.add("java.lang.Long");
    PRIMITIVE_WRAPPER_TYPES.add("java.lang.Integer");
    PRIMITIVE_WRAPPER_TYPES.add("java.lang.Short");
    PRIMITIVE_WRAPPER_TYPES.add("java.lang.Boolean");
  }

  public static boolean isIntegerNumber(String typeName) {
    return INTEGER_NUMBER_TYPES.contains(typeName);
  }

  public static boolean isPrimitive(String typeName) {
    return PRIMITIVE_TYPES.contains(typeName);
  }

  public static boolean isPrimitiveWrapper(String typeName) {
    return PRIMITIVE_WRAPPER_TYPES.contains(typeName);
  }
  @Contract("null -> false")
  public static boolean isAssignableFromPrimitiveWrapper(final PsiType type) {
    if (type == null) return false;
    return isPrimitiveWrapper(type) ||
           type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ||
           type.equalsToText(CommonClassNames.JAVA_LANG_NUMBER);
  }

  @Contract("null -> false")
  public static boolean isPrimitiveWrapper(final PsiType type) {
    return type instanceof PsiClassType && isPrimitiveWrapper(type.getCanonicalText());
  }

  @Contract("null -> false")
  public static boolean isComposite(final PsiType type) {
    return type instanceof PsiDisjunctionType || type instanceof PsiIntersectionType;
  }

  public static PsiType typeParameterErasure(@NotNull PsiTypeParameter typeParameter) {
    return typeParameterErasure(typeParameter, PsiSubstitutor.EMPTY);
  }

  private static PsiType typeParameterErasure(@NotNull PsiTypeParameter typeParameter, @NotNull PsiSubstitutor beforeSubstitutor) {
    final PsiClassType[] extendsList = typeParameter.getExtendsList().getReferencedTypes();
    if (extendsList.length > 0) {
      final PsiClass psiClass = extendsList[0].resolve();
      if (psiClass instanceof PsiTypeParameter) {
        Set<PsiClass> visited = new THashSet<>();
        visited.add(psiClass);
        final PsiTypeParameter boundTypeParameter = (PsiTypeParameter)psiClass;
        if (beforeSubstitutor.getSubstitutionMap().containsKey(boundTypeParameter)) {
          return erasure(beforeSubstitutor.substitute(boundTypeParameter));
        }
        return typeParameterErasureInner(boundTypeParameter, visited, beforeSubstitutor);
      }
      else if (psiClass != null) {
        return JavaPsiFacade.getInstance(typeParameter.getProject()).getElementFactory().createType(psiClass);
      }
    }
    return PsiType.getJavaLangObject(typeParameter.getManager(), typeParameter.getResolveScope());
  }

  private static PsiClassType typeParameterErasureInner(PsiTypeParameter typeParameter,
                                                        Set<PsiClass> visited,
                                                        PsiSubstitutor beforeSubstitutor) {
    final PsiClassType[] extendsList = typeParameter.getExtendsList().getReferencedTypes();
    if (extendsList.length > 0) {
      final PsiClass psiClass = extendsList[0].resolve();
      if (psiClass instanceof PsiTypeParameter) {
        if (!visited.contains(psiClass)) {
          visited.add(psiClass);
          if (beforeSubstitutor.getSubstitutionMap().containsKey(psiClass)) {
            return (PsiClassType)erasure(beforeSubstitutor.substitute((PsiTypeParameter)psiClass));
          }
          return typeParameterErasureInner((PsiTypeParameter)psiClass, visited, beforeSubstitutor);
        }
      }
      else if (psiClass != null) {
        return JavaPsiFacade.getInstance(typeParameter.getProject()).getElementFactory().createType(psiClass);
      }
    }
    return PsiType.getJavaLangObject(typeParameter.getManager(), typeParameter.getResolveScope());
  }

  @Contract("null -> null")
  public static PsiType erasure(@Nullable PsiType type) {
    return erasure(type, PsiSubstitutor.EMPTY);
  }

  @Contract("null, _ -> null")
  public static PsiType erasure(@Nullable final PsiType type, @NotNull final PsiSubstitutor beforeSubstitutor) {
    if (type == null) return null;
    return type.accept(new PsiTypeVisitor<PsiType>() {
      @Nullable
      @Override
      public PsiType visitType(PsiType type) {
        return type;
      }

      @Override
      public PsiType visitClassType(PsiClassType classType) {
        final PsiClass aClass = classType.resolve();
        if (aClass instanceof PsiTypeParameter && !isFreshVariable((PsiTypeParameter)aClass)) {
          return typeParameterErasure((PsiTypeParameter)aClass, beforeSubstitutor);
        }
        return classType.rawType();
      }

      @Override
      public PsiType visitWildcardType(PsiWildcardType wildcardType) {
        return wildcardType;
      }

      @Nullable
      @Override
      public PsiType visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
        return capturedWildcardType.getUpperBound().accept(this);
      }

      @Override
      public PsiType visitPrimitiveType(PsiPrimitiveType primitiveType) {
        return primitiveType;
      }

      @Override
      public PsiType visitEllipsisType(PsiEllipsisType ellipsisType) {
        final PsiType componentType = ellipsisType.getComponentType();
        final PsiType newComponentType = componentType.accept(this);
        if (newComponentType == componentType) return ellipsisType;
        return newComponentType != null ? newComponentType.createArrayType() : null;
      }

      @Override
      public PsiType visitArrayType(PsiArrayType arrayType) {
        final PsiType componentType = arrayType.getComponentType();
        final PsiType newComponentType = componentType.accept(this);
        if (newComponentType == componentType) return arrayType;
        return newComponentType != null ? newComponentType.createArrayType() : null;
      }

      @Override
      public PsiType visitDisjunctionType(PsiDisjunctionType disjunctionType) {
        final PsiClassType lub = PsiTypesUtil.getLowestUpperBoundClassType(disjunctionType);
        return lub != null ? erasure(lub, beforeSubstitutor) : disjunctionType;
      }
    });
  }

  public static Object computeCastTo(final Object operand, final PsiType castType) {
    if (operand == null || castType == null) return null;
    Object value;
    if (operand instanceof String && castType.equalsToText(JAVA_LANG_STRING) ||
        operand instanceof Boolean && PsiType.BOOLEAN.equals(castType)) {
      value = operand;
    }
    else {
      final PsiType primitiveType = wrapperToPrimitive(operand);
      if (primitiveType == null) return null;
      // identity cast, including (boolean)boolValue
      if (castType.equals(primitiveType)) return operand;
      final int rankFrom = getTypeRank(primitiveType);
      if (rankFrom > caster.length) return null;
      final int rankTo = getTypeRank(castType);
      if (rankTo > caster.length) return null;

      value = caster[rankFrom - 1][rankTo - 1].cast(operand);
    }
    return value;
  }

  @NotNull
  public static PsiType unboxAndBalanceTypes(PsiType type1, PsiType type2) {
    if (type1 instanceof PsiClassType) type1 = PsiPrimitiveType.getUnboxedType(type1);
    if (type2 instanceof PsiClassType) type2 = PsiPrimitiveType.getUnboxedType(type2);

    if (PsiType.DOUBLE.equals(type1) || PsiType.DOUBLE.equals(type2)) return PsiType.DOUBLE;
    if (PsiType.FLOAT.equals(type1) || PsiType.FLOAT.equals(type2)) return PsiType.FLOAT;
    if (PsiType.LONG.equals(type1) || PsiType.LONG.equals(type2)) return PsiType.LONG;
    return PsiType.INT;
  }

  public static IElementType convertEQtoOperation(IElementType eqOpSign) {
    IElementType opSign = null;
    if (eqOpSign == JavaTokenType.ANDEQ) {
      opSign = JavaTokenType.AND;
    }
    else if (eqOpSign == JavaTokenType.ASTERISKEQ) {
      opSign = JavaTokenType.ASTERISK;
    }
    else if (eqOpSign == JavaTokenType.DIVEQ) {
      opSign = JavaTokenType.DIV;
    }
    else if (eqOpSign == JavaTokenType.GTGTEQ) {
      opSign = JavaTokenType.GTGT;
    }
    else if (eqOpSign == JavaTokenType.GTGTGTEQ) {
      opSign = JavaTokenType.GTGTGT;
    }
    else if (eqOpSign == JavaTokenType.LTLTEQ) {
      opSign = JavaTokenType.LTLT;
    }
    else if (eqOpSign == JavaTokenType.MINUSEQ) {
      opSign = JavaTokenType.MINUS;
    }
    else if (eqOpSign == JavaTokenType.OREQ) {
      opSign = JavaTokenType.OR;
    }
    else if (eqOpSign == JavaTokenType.PERCEQ) {
      opSign = JavaTokenType.PERC;
    }
    else if (eqOpSign == JavaTokenType.PLUSEQ) {
      opSign = JavaTokenType.PLUS;
    }
    else if (eqOpSign == JavaTokenType.XOREQ) {
      opSign = JavaTokenType.XOR;
    }
    return opSign;
  }

  @Nullable
  public static PsiType calcTypeForBinaryExpression(PsiType lType, PsiType rType, @NotNull IElementType sign, boolean accessLType) {
    if (sign == JavaTokenType.PLUS) {
      // evaluate right argument first, since '+-/*%' is left associative and left operand tends to be bigger
      if (rType == null) return null;
      if (rType.equalsToText(JAVA_LANG_STRING)) {
        return rType;
      }
      if (!accessLType) return NULL_TYPE;
      if (lType == null) return null;
      if (lType.equalsToText(JAVA_LANG_STRING)) {
        return lType;
      }
      return unboxAndBalanceTypes(lType, rType);
    }
    if (sign == JavaTokenType.MINUS || sign == JavaTokenType.ASTERISK || sign == JavaTokenType.DIV || sign == JavaTokenType.PERC) {
      if (rType == null) return null;
      if (!accessLType) return NULL_TYPE;
      if (lType == null) return null;
      return unboxAndBalanceTypes(lType, rType);
    }
    if (sign == JavaTokenType.LTLT || sign == JavaTokenType.GTGT || sign == JavaTokenType.GTGTGT) {
      if (!accessLType) return NULL_TYPE;
      if (PsiType.BYTE.equals(lType) || PsiType.CHAR.equals(lType) || PsiType.SHORT.equals(lType)) {
        return PsiType.INT;
      }
      if (lType instanceof PsiClassType) lType = PsiPrimitiveType.getUnboxedType(lType);
      return lType;
    }
    if (PsiBinaryExpression.BOOLEAN_OPERATION_TOKENS.contains(sign)) {
      return PsiType.BOOLEAN;
    }
    if (sign == JavaTokenType.OR || sign == JavaTokenType.XOR || sign == JavaTokenType.AND) {
      if (rType instanceof PsiClassType) rType = PsiPrimitiveType.getUnboxedType(rType);

      if (lType instanceof PsiClassType) lType = PsiPrimitiveType.getUnboxedType(lType);

      if (rType == null) return null;
      if (PsiType.BOOLEAN.equals(rType)) return PsiType.BOOLEAN;
      if (!accessLType) return NULL_TYPE;
      if (lType == null) return null;
      if (PsiType.BOOLEAN.equals(lType)) return PsiType.BOOLEAN;
      if (PsiType.LONG.equals(lType) || PsiType.LONG.equals(rType)) return PsiType.LONG;
      return PsiType.INT;
    }
    LOG.error("Unknown token: "+sign);
    return null;
  }

  /**
   * See JLS 3.10.2. Floating-Point Literals
   * @return true  if floating point literal consists of zeros only
   */
  public static boolean isFPZero(@NotNull final String text) {
    for (int i = 0; i < text.length(); i++) {
      final char c = text.charAt(i);
      if (Character.isDigit(c) && c != '0') return false;
      final char d = Character.toUpperCase(c);
      if (d == 'E' || d == 'P') break;
    }
    return true;
  }

  public static boolean areSameFreshVariables(PsiTypeParameter p1, PsiTypeParameter p2) {
    final PsiElement originalContext = p1.getUserData(ORIGINAL_CONTEXT);
    return originalContext != null && originalContext == p2.getUserData(ORIGINAL_CONTEXT);
  }

  public static boolean isFreshVariable(PsiTypeParameter typeParameter) {
    return typeParameter.getUserData(ORIGINAL_CONTEXT) != null;
  }
  
  public static void markAsFreshVariable(PsiTypeParameter parameter, PsiElement context) {
    parameter.putUserData(ORIGINAL_CONTEXT, context);
  }

  private interface Caster {
    @NotNull
    Object cast(@NotNull Object operand);
  }

  private static final Caster[][] caster = {
    {
      new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return operand;
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (short)((Number)operand).intValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (char)((Number)operand).intValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return ((Number)operand).intValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (long)((Number)operand).intValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (float)((Number)operand).intValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (double)((Number)operand).intValue();
        }
      }
    }
    ,
    {
      new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (byte)((Short)operand).shortValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return operand;
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (char)((Short)operand).shortValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (int)(Short)operand;
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (long)(Short)operand;
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (float)(Short)operand;
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (double)(Short)operand;
        }
      }
    }
    ,
    {
      new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (byte)((Character)operand).charValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (short)((Character)operand).charValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return operand;
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (int)(Character)operand;
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (long)(Character)operand;
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (float)(Character)operand;
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (double)(Character)operand;
        }
      }
    }
    ,
    {
      new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (byte)((Integer)operand).intValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (short)((Integer)operand).intValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (char)((Integer)operand).intValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return operand;
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (long)(Integer)operand;
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (float)(Integer)operand;
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (double)(Integer)operand;
        }
      }
    }
    ,
    {
      new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (byte)((Long)operand).longValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (short)((Long)operand).longValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (char)((Long)operand).longValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (int)((Long)operand).longValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return operand;
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (float)(Long)operand;
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (double)(Long)operand;
        }
      }
    }
    ,
    {
      new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (byte)((Float)operand).floatValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (short)((Float)operand).floatValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (char)((Float)operand).floatValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (int)((Float)operand).floatValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (long)((Float)operand).floatValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return operand;
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (double)(Float)operand;
        }
      }
    }
    ,
    {
      new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (byte)((Double)operand).doubleValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (short)((Double)operand).doubleValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (char)((Double)operand).doubleValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (int)((Double)operand).doubleValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return (long)((Double)operand).doubleValue();
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return new Float((Double)operand);
        }
      }
      , new Caster() {
        @NotNull
        @Override
        public Object cast(@NotNull Object operand) {
          return operand;
        }
      }
    }
  };

  private static final Map<Class, PsiType> WRAPPER_TO_PRIMITIVE = new THashMap<>(8);
  static {
    WRAPPER_TO_PRIMITIVE.put(Boolean.class, PsiType.BOOLEAN);
    WRAPPER_TO_PRIMITIVE.put(Byte.class, PsiType.BYTE);
    WRAPPER_TO_PRIMITIVE.put(Character.class, PsiType.CHAR);
    WRAPPER_TO_PRIMITIVE.put(Short.class, PsiType.SHORT);
    WRAPPER_TO_PRIMITIVE.put(Integer.class, PsiType.INT);
    WRAPPER_TO_PRIMITIVE.put(Long.class, PsiType.LONG);
    WRAPPER_TO_PRIMITIVE.put(Float.class, PsiType.FLOAT);
    WRAPPER_TO_PRIMITIVE.put(Double.class, PsiType.DOUBLE);
  }

  private static PsiType wrapperToPrimitive(@NotNull Object o) {
    return WRAPPER_TO_PRIMITIVE.get(o.getClass());
  }
}
