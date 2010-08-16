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
package com.intellij.psi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.infos.ClassCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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

  private static final TObjectIntHashMap<PsiType> TYPE_TO_RANK_MAP = new TObjectIntHashMap<PsiType>();

  public static final int BYTE_RANK = 1;
  public static final int SHORT_RANK = 2;
  public static final int CHAR_RANK = 3;
  public static final int INT_RANK = 4;
  private static final int LONG_RANK = 5;
  private static final int FLOAT_RANK = 6;
  private static final int DOUBLE_RANK = 7;
  private static final int BOOL_RANK = 10;
  private static final int STRING_RANK = 100;
  private static final int MAX_NUMERIC_RANK = DOUBLE_RANK;

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


  /**
   * @return true iff fromType can be casted to toType
   */
  public static boolean areTypesConvertible(@NotNull PsiType fromType, @NotNull PsiType toType) {
    if (fromType == toType) return true;
    final boolean fromIsPrimitive = isPrimitiveAndNotNull(fromType);
    final boolean toIsPrimitive = isPrimitiveAndNotNull(toType);
    if (fromIsPrimitive || toIsPrimitive) {
      if (isVoidType(fromType) || isVoidType(toType)) return false;
      final int fromTypeRank = getTypeRank(fromType);
      final int toTypeRank = getTypeRank(toType);
      if (!toIsPrimitive) {
        if (fromTypeRank == toTypeRank) return true;
        // JLS 5.5: A value of a primitive type can be cast to a reference type by boxing conversion(see 5.1.7)
        if (!(toType instanceof PsiClassType)) return false;
        PsiClass toClass = ((PsiClassType)toType).resolve();
        if (toClass == null) return false;
        PsiClassType boxedType = ((PsiPrimitiveType)fromType).getBoxedType(toClass.getManager(), toType.getResolveScope());
        return boxedType != null && areTypesConvertible(boxedType, toType);
      }
      if (!fromIsPrimitive) {
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
   * see JLS 5.1.5, JLS3 5.5
   */
  private static boolean isNarrowingReferenceConversionAllowed(PsiType fromType, PsiType toType) {
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
      return isAssignable(fromType, toType);
    }
    if (fromType instanceof PsiArrayType) {
      if (toType instanceof PsiClassType) {
        final PsiClass resolved = ((PsiClassType)toType).resolve();
        if (resolved instanceof PsiTypeParameter) {
          for (final PsiClassType boundType : resolved.getExtendsListTypes()) {
            if (!isNarrowingReferenceConversionAllowed(fromType, boundType)) return false;
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
    } else if (toType instanceof PsiIntersectionType) return false;

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
      return isNarrowingReferenceConversionAllowed(fromType, ((PsiCapturedWildcardType)toType).getWildcard());
    }
    if (fromType instanceof PsiCapturedWildcardType) {
      return isNarrowingReferenceConversionAllowed(((PsiCapturedWildcardType)fromType).getWildcard(), toType);
    }

    if (isAssignable(fromType, toType)) return true;

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
        return !fromClass.hasModifierProperty(PsiModifier.FINAL) &&
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
    else {
      if (!toClass.isInterface()) {
        if (!toClass.hasModifierProperty(PsiModifier.FINAL)) {
          return checkSuperTypesWithDifferentTypeArguments(fromResult, toClass, manager, toResult.getSubstitutor(), null, languageLevel);
        }
        else {
          if (!toClass.isInheritor(fromClass, true)) return false;
          PsiSubstitutor toSubstitutor = getSuperClassSubstitutor(fromClass, toClass, toResult.getSubstitutor());
          return areSameArgumentTypes(fromClass, fromResult.getSubstitutor(), toSubstitutor);
        }
      }
      else {
        if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) {
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
    }
  }

  private static PsiClassType obtainSafeSuperType(final PsiTypeParameter typeParameter) {
    final PsiClassType superType = typeParameter.getSuperTypes()[0];
    final PsiClassType.ClassResolveResult result = superType.resolveGenerics();
    final PsiClass superClass = result.getElement();
    if (superClass != null) {
      final PsiSubstitutor substitutor = result.getSubstitutor().put(typeParameter, null);
      return JavaPsiFacade.getInstance(typeParameter.getProject()).getElementFactory().createType(superClass, substitutor);
    }
    return superType;
  }

  private static boolean checkSuperTypesWithDifferentTypeArguments(PsiClassType.ClassResolveResult baseResult,
                                                                   PsiClass derived,
                                                                   PsiManager manager,
                                                                   PsiSubstitutor derivedSubstitutor,
                                                                   Set<PsiClass> visited,
                                                                   final LanguageLevel languageLevel) {
    if (visited != null && visited.contains(derived)) return true;

    if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) return true;
    PsiClass base = baseResult.getElement();
    PsiClass[] supers = derived.getSupers();
    if (manager.areElementsEquivalent(base, derived)) {
      derivedSubstitutor = getSuperClassSubstitutor(derived, derived, derivedSubstitutor);
      return areSameArgumentTypes(derived, baseResult.getSubstitutor(), derivedSubstitutor);
    }
    else if (base.isInheritor(derived, true)) {
      derivedSubstitutor = getSuperClassSubstitutor(derived, derived, derivedSubstitutor);
      PsiSubstitutor baseSubstitutor = getSuperClassSubstitutor(derived, base, baseResult.getSubstitutor());
      if (!areSameArgumentTypes(derived, baseSubstitutor, derivedSubstitutor)) return false;
    }

    if (visited == null) visited = new THashSet<PsiClass>();
    visited.add(derived);
    for (PsiClass aSuper : supers) {
      PsiSubstitutor s = getSuperClassSubstitutor(aSuper, derived, derivedSubstitutor);
      if (!checkSuperTypesWithDifferentTypeArguments(baseResult, aSuper, manager, s, visited, languageLevel)) return false;
    }

    return true;
  }

  private static boolean areSameParameterTypes(PsiClassType type1, PsiClassType type2) {
    PsiClassType.ClassResolveResult resolveResult1 = type1.resolveGenerics();
    PsiClassType.ClassResolveResult resolveResult2 = type2.resolveGenerics();
    final PsiClass aClass = resolveResult1.getElement();
    final PsiClass bClass = resolveResult2.getElement();
    return aClass != null &&
           bClass != null &&
           aClass.getManager().areElementsEquivalent(aClass, bClass) &&
           areSameArgumentTypes(aClass, resolveResult1.getSubstitutor(), resolveResult2.getSubstitutor());
  }

  private static boolean areSameArgumentTypes(PsiClass aClass, PsiSubstitutor substitutor1, PsiSubstitutor substitutor2) {
    for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(aClass)) {
      PsiType typeArg1 = substitutor1.substitute(typeParameter);
      PsiType typeArg2 = substitutor2.substitute(typeParameter);
      if (typeArg1 == null || typeArg2 == null) return true;
      if (TypesDistinctProver.provablyDistinct(typeArg1, typeArg2)) return false;
    }

    return true;
  }

  public static boolean isPrimitiveAndNotNull(PsiType type) {
    return type instanceof PsiPrimitiveType && !isNullType(type);
  }

  public static boolean isEnumType(PsiType type) {
    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      return psiClass != null && psiClass.isEnum();
    }
    return false;
  }

  public static boolean isNullType(PsiType type) {
    return PsiType.NULL.equals(type);
  }

  public static boolean isDoubleType(PsiType type) {
    return PsiType.DOUBLE.equals(type) || PsiType.DOUBLE.equals(PsiPrimitiveType.getUnboxedType(type));
  }

  public static boolean isFloatType(PsiType type) {
    return PsiType.FLOAT.equals(type) || PsiType.FLOAT.equals(PsiPrimitiveType.getUnboxedType(type));
  }

  public static boolean isLongType(PsiType type) {
    return PsiType.LONG.equals(type) || PsiType.LONG.equals(PsiPrimitiveType.getUnboxedType(type));
  }

  public static boolean isVoidType(PsiType type) {
    return PsiType.VOID.equals(type);
  }

  public static boolean isBooleanType(PsiType type) {
    return PsiType.BOOLEAN.equals(type) || PsiType.BOOLEAN.equals(PsiPrimitiveType.getUnboxedType(type));
  }

  public static boolean isNumericType(int typeRank) {
    return typeRank <= MAX_NUMERIC_RANK;
  }
  public static boolean isNumericType(PsiType type) {
    return type != null && isNumericType(getTypeRank(type));
  }

  /**
   * @return 1..MAX_NUMERIC_TYPE if type is primitive numeric type,
   *         BOOL_TYPE for boolean,
   *         STRING_TYPE for String,
   *         Integer.MAX_VALUE for others
   */
  public static int getTypeRank(PsiType type) {
    PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(type);
    if (unboxedType != null) {
      type = unboxedType;
    }

    int rank = TYPE_TO_RANK_MAP.get(type);
    if (rank != 0) return rank;
    if (type.equalsToText("java.lang.String")) return STRING_RANK;
    return Integer.MAX_VALUE;
  }

  /**
   * @param tokenType JavaTokenType enumeration
   * @param lOperand
   * @param rOperand
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
        if (isPrimitiveAndNotNull(ltype) || isPrimitiveAndNotNull(rtype)) return false;
        isApplicable = areTypesConvertible(ltype, rtype) || areTypesConvertible(rtype, ltype);
      }
    }
    else if (tokenType == JavaTokenType.PLUS) {
      if (ltype.equalsToText("java.lang.String")) {
        isApplicable = !isVoidType(rtype);
        resultTypeRank = STRING_RANK;
        break Label;
      }
      else if (rtype.equalsToText("java.lang.String")) {
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
        isApplicable = ltypeRank == resultTypeRank || ltype.equalsToText("java.lang.Object");
      }
      else {
        isApplicable = ltypeRank <= MAX_NUMERIC_RANK;
      }
    }
    return isApplicable;
  }

  public static boolean isPrimitiveAndNotNullOrWrapper(PsiType type) {
    if (type instanceof PsiClassType) {
      return PsiPrimitiveType.getUnboxedType(type) != null;
    }

    return isPrimitiveAndNotNull(type);
  }

  public static boolean isUnaryOperatorApplicable(PsiJavaToken token, PsiExpression operand) {
    if (operand == null) return false;
    PsiType type = operand.getType();
    return type != null && isUnaryOperatorApplicable(token, type);
  }

  public static boolean isUnaryOperatorApplicable(final PsiJavaToken token, final PsiType type) {
    IElementType i = token.getTokenType();
    int typeRank = getTypeRank(type);
    if (i == JavaTokenType.MINUSMINUS || i == JavaTokenType.PLUSPLUS) {
      return typeRank <= MAX_NUMERIC_RANK;
    }
    else if (i == JavaTokenType.MINUS || i == JavaTokenType.PLUS) {
      return typeRank <= MAX_NUMERIC_RANK;
    }
    else if (i == JavaTokenType.TILDE) {
      return typeRank <= LONG_RANK;
    }
    else if (i == JavaTokenType.EXCL) {
      return typeRank == BOOL_RANK;
    }
    else {
      LOG.error("unknown token: " + token);
    }
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
        value = ((Character)rValue).charValue();
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
   * @return true if value of type <code>right</code> can be assigned to an l-value of
   *         type <code>left</code>
   */
  public static boolean isAssignable(@NotNull PsiType left, @NotNull PsiType right) {
    return isAssignable(left, right, true);
  }

  public static boolean isAssignable(@NotNull PsiType left, @NotNull PsiType right, boolean allowUncheckedConversion) {
    if (left == right || left.equals(right)) return true;
    if (isNullType(right)) {
      return !(left instanceof PsiPrimitiveType) || isNullType(left);
    }

    if (left instanceof PsiIntersectionType) {
      PsiType[] conjuncts = ((PsiIntersectionType)left).getConjuncts();
      for (PsiType conjunct : conjuncts) {
        if (!isAssignable(conjunct, right, allowUncheckedConversion)) return false;
      }
      return true;
    }
    if (right instanceof PsiIntersectionType) {
      PsiType[] conjuncts = ((PsiIntersectionType)right).getConjuncts();
      for (PsiType conjunct : conjuncts) {
        if (isAssignable(left, conjunct, allowUncheckedConversion)) return true;
      }
      return false;
    }

    if (left instanceof PsiCapturedWildcardType) {
      return left.equals(right) || isAssignable(((PsiCapturedWildcardType)left).getLowerBound(), right, allowUncheckedConversion);
    }
    if (right instanceof PsiCapturedWildcardType) {
      return isAssignable(left, ((PsiCapturedWildcardType)right).getUpperBound(), allowUncheckedConversion);
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
          return left.equalsToText("java.lang.Object");
        }
      }
      PsiType lCompType = ((PsiArrayType)left).getComponentType();
      PsiType rCompType = ((PsiArrayType)right).getComponentType();
      if (lCompType instanceof PsiPrimitiveType) {
        return lCompType.equals(rCompType);
      }
      else {
        return !(rCompType instanceof PsiPrimitiveType) && isAssignable(lCompType, rCompType, allowUncheckedConversion);
      }
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
      LOG.error(right);
    }
    if (left instanceof PsiPrimitiveType) {
      return isUnboxable((PsiPrimitiveType)left, (PsiClassType)right);
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
    return isClassAssignable(leftResult, rightResult, allowUncheckedConversion);
  }

  private static boolean isAssignableFromWildcard(PsiType left, PsiWildcardType rightWildcardType) {
    return isAssignable(left, rightWildcardType.getExtendsBound());
  }

  private static boolean isAssignableToWildcard(PsiWildcardType wildcardType, PsiType right) {
    if (wildcardType.isSuper()) {
      return isAssignable(right, wildcardType.getSuperBound());
    }
    return isAssignable(wildcardType.getExtendsBound(), right);
  }

  private static boolean isUnboxable(final PsiPrimitiveType left, final PsiClassType right) {
    final PsiPrimitiveType rightUnboxedType = PsiPrimitiveType.getUnboxedType(right);
    return rightUnboxedType != null && isAssignable(left, rightUnboxedType);
  }

  public static boolean boxingConversionApplicable(final PsiType left, final PsiType right) {
    if (left instanceof PsiPrimitiveType && !PsiType.NULL.equals(left)) {
      return right instanceof PsiClassType && isAssignable(left, right);
    }
    else {
      return left instanceof PsiClassType
                && right instanceof PsiPrimitiveType
                && !PsiType.NULL.equals(right)
                && isAssignable(left, right);
    }
  }

  private static final Key<CachedValue<Set<String>>> POSSIBLE_BOXED_HOLDER_TYPES = Key.create("Types that may be possibly assigned from primitive ones");

  private static boolean isBoxable(final PsiClassType left, final PsiPrimitiveType right) {
    if (!left.getLanguageLevel().hasEnumKeywordAndAutoboxing()) return false;
    final PsiClass psiClass = left.resolve();
    if (psiClass == null) return false;

    final String qname = psiClass.getQualifiedName();
    if (qname == null || !getAllBoxedTypeSupers(psiClass).contains(qname)) {
      return false;
    }

    final PsiClassType rightBoxed = right.getBoxedType(psiClass.getManager(), left.getResolveScope());
    return rightBoxed != null && isAssignable(left, rightBoxed);
  }

  private static Set<String> getAllBoxedTypeSupers(PsiClass psiClass) {
    PsiManager manager = psiClass.getManager();
    final Project project = psiClass.getProject();
    CachedValue<Set<String>> boxedHolderTypes = project.getUserData(POSSIBLE_BOXED_HOLDER_TYPES);
    if (boxedHolderTypes == null) {
      project.putUserData(POSSIBLE_BOXED_HOLDER_TYPES, boxedHolderTypes = CachedValuesManager.getManager(manager.getProject()).createCachedValue(new CachedValueProvider<Set<String>>() {
        public Result<Set<String>> compute() {
          final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
          final Set<String> set = new THashSet<String>();
          for (final String qname : PsiPrimitiveType.getAllBoxedTypeNames()) {
            final PsiClass boxedClass = facade.findClass(qname, GlobalSearchScope.allScope(project));
            InheritanceUtil.processSupers(boxedClass, true, new Processor<PsiClass>() {
              public boolean process(PsiClass psiClass) {
                ContainerUtil.addIfNotNull(psiClass.getQualifiedName(), set);
                return true;
              }
            });
          }
          return Result.create(set, ProjectRootManager.getInstance(project));
        }
      }, false));
    }

    final Set<String> boxedHolders = boxedHolderTypes.getValue();
    return boxedHolders;
  }

  private static boolean isClassAssignable(PsiClassType.ClassResolveResult leftResult,
                                           PsiClassType.ClassResolveResult rightResult,
                                           boolean allowUncheckedConversion) {
    final PsiClass leftClass = leftResult.getElement();
    final PsiClass rightClass = rightResult.getElement();
    return leftClass != null
           && rightClass != null
           && InheritanceUtil.isInheritorOrSelf(rightClass, leftClass, true)
           && typeParametersAgree(leftResult, rightResult, allowUncheckedConversion);
  }

  private static boolean typeParametersAgree(PsiClassType.ClassResolveResult leftResult,
                                             PsiClassType.ClassResolveResult rightResult,
                                             boolean allowUncheckedConversion) {
    PsiSubstitutor rightSubstitutor = rightResult.getSubstitutor();
    PsiClass leftClass = leftResult.getElement();
    PsiClass rightClass = rightResult.getElement();

    if (!leftClass.hasTypeParameters()) return true;
    PsiSubstitutor leftSubstitutor = leftResult.getSubstitutor();

    if (!leftClass.getManager().areElementsEquivalent(leftClass, rightClass)) {
      rightSubstitutor = getSuperClassSubstitutor(leftClass, rightClass, rightSubstitutor);
      rightClass = leftClass;
    }
    else if (!rightClass.hasTypeParameters()) return true;

    Iterator<PsiTypeParameter> li = PsiUtil.typeParametersIterator(leftClass);
    Iterator<PsiTypeParameter> ri = PsiUtil.typeParametersIterator(rightClass);
    while (li.hasNext()) {
      if (!ri.hasNext()) return false;
      PsiTypeParameter lp = li.next();
      PsiTypeParameter rp = ri.next();
      final PsiType typeLeft = leftSubstitutor.substitute(lp);
      if (typeLeft == null) continue;
      final PsiType typeRight = rightSubstitutor.substituteWithBoundsPromotion(rp);
      if (typeRight == null) {
        // compatibility feature: allow to assign raw types to generic ones
        return allowUncheckedConversion;
      }
      if (!typesAgree(typeLeft, typeRight)) return false;
    }
    return true;
  }

  private static boolean typesAgree(PsiType typeLeft, PsiType typeRight) {
    if (typeLeft instanceof PsiWildcardType) {
      final PsiWildcardType leftWildcard = (PsiWildcardType)typeLeft;
      final PsiType leftBound = leftWildcard.getBound();
      if (leftBound == null) return true;
      if (leftBound.equalsToText("java.lang.Object")) {
        if (!leftWildcard.isSuper()) return true;
        if (typeRight.equalsToText("java.lang.Object")) return true;
      }

      if (typeRight instanceof PsiWildcardType) {
        final PsiWildcardType rightWildcard = (PsiWildcardType)typeRight;
        if (leftWildcard.isExtends()) {
          return rightWildcard.isExtends() && isAssignable(leftBound, rightWildcard.getBound(), false);
        }
        else { //isSuper
          return rightWildcard.isSuper() && isAssignable(rightWildcard.getBound(), leftBound, false);
        }
      }
      else {
        if (leftWildcard.isExtends()) {
          return isAssignable(leftBound, typeRight, false);
        }
        else { // isSuper
          return isAssignable(typeRight, leftBound, false);
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
                                                   PsiSubstitutor derivedSubstitutor) {
    if (superClassCandidate.getManager().areElementsEquivalent(superClassCandidate, derivedClassCandidate)) {
      PsiTypeParameter[] baseParams = superClassCandidate.getTypeParameters();
      PsiTypeParameter[] derivedParams = derivedClassCandidate.getTypeParameters();
      if (baseParams.length > 0 && derivedParams.length == 0) {
        return JavaPsiFacade.getInstance(superClassCandidate.getProject()).getElementFactory().createRawSubstitutor(superClassCandidate);
      }
      return derivedSubstitutor;
    }
    if (!derivedClassCandidate.isInheritor(superClassCandidate, true)) return null;
    return getSuperClassSubstitutor(superClassCandidate, derivedClassCandidate, derivedSubstitutor);
  }

  /**
   * Calculates substitutor that binds type parameters in <code>superClass</code> with
   * values that they have in <code>derivedClass</code>, given that type parameters in
   * <code>derivedClass</code> are bound by <code>derivedSubstitutor</code>.
   * <code>superClass</code> must be a super class/interface of <code>derivedClass</code> (as in
   * <code>InheritanceUtil.isInheritor(derivedClass, superClass, true)</code>
   *
   * @param superClass
   * @param derivedClass
   * @param derivedSubstitutor
   * @return substitutor (never returns <code>null</code>)
   * @see InheritanceUtil#isInheritor(PsiClass, PsiClass, boolean)
   */
  @NotNull
  public static PsiSubstitutor getSuperClassSubstitutor(@NotNull PsiClass superClass,
                                                        PsiClass derivedClass,
                                                        PsiSubstitutor derivedSubstitutor) {
    // [dsl] assertion commented out since we no longer cache isInheritor
    //LOG.assertTrue(derivedClass.isInheritor(superClass, true), "Not inheritor: " + derivedClass + " super: " + superClass);

    if (!superClass.hasTypeParameters() && superClass.getContainingClass() == null) return PsiSubstitutor.EMPTY; //optimization

    final PsiManager manager = superClass.getManager();
    if (PsiUtil.isRawSubstitutor(derivedClass, derivedSubstitutor)) {
      return JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createRawSubstitutor(superClass);
    }

    final PsiClass objectClass = JavaPsiFacade.getInstance(manager.getProject()).findClass("java.lang.Object", superClass.getResolveScope());
    if (manager.areElementsEquivalent(superClass, objectClass)) {
      return PsiSubstitutor.EMPTY;
    }

    PsiSubstitutor substitutor;
    final Set<PsiClass> visited = new THashSet<PsiClass>();
    if (derivedClass instanceof PsiAnonymousClass) {
      final PsiClassType baseType = ((PsiAnonymousClass)derivedClass).getBaseClassType();
      final JavaResolveResult result = baseType.resolveGenerics();
      if (result.getElement() == null) return PsiSubstitutor.UNKNOWN;
      substitutor = getSuperClassSubstitutorInner(superClass, (PsiClass)result.getElement(),
                                                  derivedSubstitutor.putAll(result.getSubstitutor()), visited, manager);
    }
    else {
      substitutor = getSuperClassSubstitutorInner(superClass, derivedClass, derivedSubstitutor, visited, manager);
    }
    if (substitutor == null) {
      LOG.error(
        "Not inheritor: " + derivedClass + "(" + derivedClass.getClass().getName() + "; " + PsiUtil.getVirtualFile(derivedClass) + ");" +
        "\n super: " + superClass + "(" + superClass.getClass().getName() + "; " + PsiUtil.getVirtualFile(superClass) + ")");
    }
    return substitutor;
  }

  @NotNull
  public static PsiSubstitutor getSuperClassSubstitutor(@NotNull PsiClass superClass, @NotNull PsiClassType classType) {
      final PsiClassType.ClassResolveResult classResolveResult = classType.resolveGenerics();
      return getSuperClassSubstitutor(superClass, classResolveResult.getElement(), classResolveResult.getSubstitutor());
  }

  private static PsiSubstitutor getSuperClassSubstitutorInner(PsiClass base,
                                                              PsiClass candidate,
                                                              PsiSubstitutor candidateSubstitutor,
                                                              Set<PsiClass> visited,
                                                              PsiManager manager) {
    if (!visited.add(candidate)) return null;

    if (base == candidate) return candidateSubstitutor;
    if (manager.areElementsEquivalent(base, candidate)) {
      PsiTypeParameter[] baseParams = base.getTypeParameters();
      PsiTypeParameter[] candidateParams = candidate.getTypeParameters();
      PsiElementFactory factory = JavaPsiFacade.getInstance(base.getProject()).getElementFactory();
      if (baseParams.length > 0 && candidateParams.length == 0) {
        return factory.createRawSubstitutor(base);
      }
      else {
        Map<PsiTypeParameter, PsiType> m = new HashMap<PsiTypeParameter, PsiType>();
        for (int i = 0; i < candidateParams.length && i < baseParams.length; i++) {
          m.put(baseParams[i], candidateSubstitutor.substitute(candidateParams[i]));
        }
        return factory.createSubstitutor(m);
      }
    }

    PsiSubstitutor substitutor = checkReferenceList(candidate.getExtendsListTypes(), candidateSubstitutor, base, visited,
                                                    manager);
    if (substitutor == null) {
      substitutor = checkReferenceList(candidate.getImplementsListTypes(), candidateSubstitutor, base, visited, manager);
    }
    return substitutor;
  }

  private static PsiSubstitutor checkReferenceList(final PsiClassType[] types, PsiSubstitutor candidateSubstitutor,
                                                   PsiClass base,
                                                   Set<PsiClass> set,
                                                   PsiManager manager) {
    for (final PsiClassType type : types) {
      final PsiType substitutedType = candidateSubstitutor.substitute(type);
      //if (!(substitutedType instanceof PsiClassType)) return null;
      LOG.assertTrue(substitutedType instanceof PsiClassType);

      final JavaResolveResult result = ((PsiClassType)substitutedType).resolveGenerics();
      final PsiElement newCandidate = result.getElement();
      if (newCandidate != null) {
        final PsiSubstitutor substitutor = result.getSubstitutor();
        final PsiSubstitutor newSubstitutor = getSuperClassSubstitutorInner(base, (PsiClass)newCandidate,
                                                                            substitutor, set, manager);
        if (newSubstitutor != null) {
          return type.isRaw() ? JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createRawSubstitutor(base) : newSubstitutor;
        }
      }
    }
    return null;
  }

  /**
   * see JLS 5.6.2
   */
  public static PsiType binaryNumericPromotion(PsiType type1, PsiType type2) {
    if (isDoubleType(type1)) return unbox(type1);
    if (isDoubleType(type2)) return unbox(type2);
    if (isFloatType(type1)) return unbox(type1);
    if (isFloatType(type2)) return unbox(type2);
    if (isLongType(type1)) return unbox(type1);
    if (isLongType(type2)) return unbox(type2);

    return PsiType.INT;
  }

  private static PsiType unbox(PsiType type) {
    if (type instanceof PsiPrimitiveType) return type;
    if (type instanceof PsiClassType) {
      type = PsiPrimitiveType.getUnboxedType(type);
      LOG.assertTrue(type != null);
      return type;
    }
    LOG.error("Invalid type for unboxing "+type);
    return type;
  }

  private static final Set<String> INTEGER_NUMBER_TYPES = new THashSet<String>(5);

  static {
    INTEGER_NUMBER_TYPES.add(PsiType.BYTE.getCanonicalText());
    INTEGER_NUMBER_TYPES.add(PsiType.CHAR.getCanonicalText());
    INTEGER_NUMBER_TYPES.add(PsiType.LONG.getCanonicalText());
    INTEGER_NUMBER_TYPES.add(PsiType.INT.getCanonicalText());
    INTEGER_NUMBER_TYPES.add(PsiType.SHORT.getCanonicalText());
  }

  private static final Set<String> PRIMITIVE_TYPES = new THashSet<String>(9);

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

  private static final Set<String> PRIMITIVE_WRAPPER_TYPES = new THashSet<String>(8);

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
  public static boolean isPrimitiveWrapper(final PsiType type) {
    return type != null && isPrimitiveWrapper(type.getCanonicalText());
  }

  public static PsiType typeParameterErasure(@NotNull PsiTypeParameter typeParameter) {
    return typeParameterErasure(typeParameter, PsiSubstitutor.EMPTY);
  }

  private static PsiType typeParameterErasure(@NotNull PsiTypeParameter typeParameter, final PsiSubstitutor beforeSubstitutor) {
    final PsiClassType[] extendsList = typeParameter.getExtendsList().getReferencedTypes();
    if (extendsList.length > 0) {
      final PsiClass psiClass = extendsList[0].resolve();
      if (psiClass instanceof PsiTypeParameter) {
        Set<PsiClass> visited = new THashSet<PsiClass>();
        visited.add(psiClass);
        final PsiTypeParameter boundTypeParameter = (PsiTypeParameter)psiClass;
        if (beforeSubstitutor.getSubstitutionMap().containsKey(boundTypeParameter)) {
          return erasure(beforeSubstitutor.substitute(boundTypeParameter));
        }
        return typeParameterErasureInner(boundTypeParameter, visited);
      }
      else if (psiClass != null) {
        return JavaPsiFacade.getInstance(typeParameter.getProject()).getElementFactory().createType(psiClass);
      }
    }
    return PsiType.getJavaLangObject(typeParameter.getManager(), typeParameter.getResolveScope());
  }

  private static PsiClassType typeParameterErasureInner(PsiTypeParameter typeParameter, Set<PsiClass> visited) {
    final PsiClassType[] extendsList = typeParameter.getExtendsList().getReferencedTypes();
    if (extendsList.length > 0) {
      final PsiClass psiClass = extendsList[0].resolve();
      if (psiClass instanceof PsiTypeParameter) {
        if (!visited.contains(psiClass)) {
          visited.add(psiClass);
          return typeParameterErasureInner((PsiTypeParameter)psiClass, visited);
        }
      }
      else if (psiClass != null) {
        return JavaPsiFacade.getInstance(typeParameter.getProject()).getElementFactory().createType(psiClass);
      }
    }
    return PsiType.getJavaLangObject(typeParameter.getManager(), typeParameter.getResolveScope());
  }

  public static PsiType erasure(PsiType type) {
    return erasure(type, PsiSubstitutor.EMPTY);
  }

  public static PsiType erasure(PsiType type, final PsiSubstitutor beforeSubstitutor) {
    if (type == null) return null;
    return type.accept(new PsiTypeVisitor<PsiType>() {
      public PsiType visitClassType(PsiClassType classType) {
        final PsiClass aClass = classType.resolve();
        if (aClass instanceof PsiTypeParameter) {
          return typeParameterErasure((PsiTypeParameter)aClass, beforeSubstitutor);
        }
        else {
          return classType.rawType();
        }
      }

      public PsiType visitWildcardType(PsiWildcardType wildcardType) {
        return wildcardType.getExtendsBound().accept(this);
      }

      public PsiType visitPrimitiveType(PsiPrimitiveType primitiveType) {
        return primitiveType;
      }

      public PsiType visitEllipsisType(PsiEllipsisType ellipsisType) {
        final PsiType componentType = ellipsisType.getComponentType();
        final PsiType newComponentType = componentType.accept(this);
        if (newComponentType == componentType) return ellipsisType;
        return new PsiArrayType(newComponentType);
      }

      public PsiType visitArrayType(PsiArrayType arrayType) {
        final PsiType componentType = arrayType.getComponentType();
        final PsiType newComponentType = componentType.accept(this);
        if (newComponentType == componentType) return arrayType;
        return newComponentType.createArrayType();
      }
    });
  }

  public static Object computeCastTo(final Object operand, final PsiType castType) {
    if (operand == null || castType == null) return null;
    Object value;
    if (operand instanceof String && castType.equalsToText("java.lang.String")) {
      value = operand;
    }
    else if (operand instanceof Boolean && PsiType.BOOLEAN.equals(castType)) {
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

  private interface Caster {
    Object cast(Object operand);
  }

  private static final Caster[][] caster = {
    {
      new Caster() {
        public Object cast(Object operand) {
          return operand;
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Short.valueOf((short)((Number)operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Character((char) ((Number) operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Integer.valueOf(((Number)operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Long.valueOf(((Number)operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Float(((Number) operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Double(((Number) operand).intValue());
        }
      }
    }
    ,
    {
      new Caster() {
        public Object cast(Object operand) {
          return Byte.valueOf((byte)((Short)operand).shortValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Short.valueOf(((Short)operand).shortValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Character((char) ((Short) operand).shortValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Integer.valueOf(((Short)operand).shortValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Long.valueOf(((Short)operand).shortValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Float(((Short) operand).shortValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Double(((Short) operand).shortValue());
        }
      }
    }
    ,
    {
      new Caster() {
        public Object cast(Object operand) {
          return Byte.valueOf((byte)((Character)operand).charValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Short.valueOf((short)((Character)operand).charValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Character(((Character) operand).charValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Integer.valueOf(((Character)operand).charValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Long.valueOf(((Character)operand).charValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Float(((Character) operand).charValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Double(((Character) operand).charValue());
        }
      }
    }
    ,
    {
      new Caster() {
        public Object cast(Object operand) {
          return Byte.valueOf((byte)((Integer)operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Short.valueOf((short)((Integer)operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Character((char) ((Integer) operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Integer.valueOf(((Integer)operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Long.valueOf(((Integer)operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Float(((Integer) operand).intValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Double(((Integer) operand).intValue());
        }
      }
    }
    ,
    {
      new Caster() {
        public Object cast(Object operand) {
          return Byte.valueOf((byte)((Long)operand).longValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Short.valueOf((short)((Long)operand).longValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Character((char) ((Long) operand).longValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Integer.valueOf((int)((Long)operand).longValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Long.valueOf(((Long)operand).longValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Float(((Long) operand).longValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Double(((Long) operand).longValue());
        }
      }
    }
    ,
    {
      new Caster() {
        public Object cast(Object operand) {
          return Byte.valueOf((byte)((Float)operand).floatValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Short.valueOf((short)((Float)operand).floatValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Character((char) ((Float) operand).floatValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Integer.valueOf((int)((Float)operand).floatValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Long.valueOf((long)((Float)operand).floatValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Float(((Float) operand).floatValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Double(((Float) operand).floatValue());
        }
      }
    }
    ,
    {
      new Caster() {
        public Object cast(Object operand) {
          return Byte.valueOf((byte)((Double)operand).doubleValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Short.valueOf((short)((Double)operand).doubleValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Character((char) ((Double) operand).doubleValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Integer.valueOf((int)((Double)operand).doubleValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return Long.valueOf((long)((Double)operand).doubleValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Float(((Double) operand).doubleValue());
        }
      }
      , new Caster() {
        public Object cast(Object operand) {
          return new Double(((Double) operand).doubleValue());
        }
      }
    }
  };

  private static final Map<Class, PsiType> WRAPPER_TO_PRIMITIVE = new THashMap<Class, PsiType>(8);
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

  private static PsiType wrapperToPrimitive(Object o) {
    return WRAPPER_TO_PRIMITIVE.get(o.getClass());
  }

  @Nullable
  public static ClassCandidateInfo splitType(@Nullable PsiType type, @NotNull PsiElement place) {
    if (type instanceof PsiArrayType) {
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(place);
      final PsiClass arrayClass = JavaPsiFacade.getInstance(place.getProject()).getElementFactory().getArrayClass(languageLevel);
      final PsiTypeParameter[] arrayTypeParameters = arrayClass.getTypeParameters();
      if (arrayTypeParameters.length > 0) {
        return new ClassCandidateInfo(arrayClass, PsiSubstitutor.EMPTY.put(arrayTypeParameters[0], ((PsiArrayType)type).getComponentType()));
      }

      return new ClassCandidateInfo(arrayClass, PsiSubstitutor.EMPTY);
    }

    final JavaResolveResult result = PsiUtil.resolveGenericsClassInType(type);
    final PsiClass clazz = (PsiClass)result.getElement();
    if (clazz == null) {
      return null;
    }

    return new ClassCandidateInfo(clazz, result.getSubstitutor());

  }
}
