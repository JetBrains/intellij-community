// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeType;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Utility methods to use {@link LongRangeSet} in JVM.
 */
public class JvmPsiRangeSetUtil {
  private static final LongRangeSet BYTE_RANGE = LongRangeSet.range(Byte.MIN_VALUE, Byte.MAX_VALUE);
  private static final LongRangeSet CHAR_RANGE = LongRangeSet.range(Character.MIN_VALUE, Character.MAX_VALUE);
  private static final LongRangeSet SHORT_RANGE = LongRangeSet.range(Short.MIN_VALUE, Short.MAX_VALUE);
  private static final LongRangeSet INT_RANGE = LongRangeSet.range(Integer.MIN_VALUE, Integer.MAX_VALUE);
  private static final LongRangeSet INDEX_RANGE = LongRangeSet.range(0, Integer.MAX_VALUE);
  private static final String JETBRAINS_RANGE = "org.jetbrains.annotations.Range";
  private static final String CHECKER_RANGE = "org.checkerframework.common.value.qual.IntRange";
  private static final String CHECKER_GTE_NEGATIVE_ONE = "org.checkerframework.checker.index.qual.GTENegativeOne";
  private static final String CHECKER_NON_NEGATIVE = "org.checkerframework.checker.index.qual.NonNegative";
  private static final String CHECKER_POSITIVE = "org.checkerframework.checker.index.qual.Positive";
  private static final String JSR305_NONNEGATIVE = "javax.annotation.Nonnegative";
  private static final String VALIDATION_MIN = "javax.validation.constraints.Min";
  private static final String VALIDATION_MAX = "javax.validation.constraints.Max";
  private static final List<String> ANNOTATIONS = Arrays.asList(CHECKER_RANGE,
                                                                CHECKER_GTE_NEGATIVE_ONE,
                                                                CHECKER_NON_NEGATIVE,
                                                                CHECKER_POSITIVE,
                                                                JSR305_NONNEGATIVE,
                                                                VALIDATION_MIN,
                                                                VALIDATION_MAX);

  private JvmPsiRangeSetUtil() {}

  /**
   * @param owner possibly annotated element (PsiMethod or PsiVariable)
   * @return the set of possible values, according to the annotation; complete set (all possible long numbers) if not annotated via
   * range annotation.
   */
  @NotNull
  public static LongRangeSet fromPsiElement(@Nullable PsiModifierListOwner owner) {
    if (owner == null) return LongRangeSet.all();
    return StreamEx.of(AnnotationUtil.findAnnotation(owner, JETBRAINS_RANGE), owner.getAnnotation(JETBRAINS_RANGE))
                   .nonNull()
                   .append(AnnotationUtil.findAnnotations(owner, ANNOTATIONS))
                   .map(JvmPsiRangeSetUtil::fromAnnotation).foldLeft(LongRangeSet.all(), LongRangeSet::meet);
  }

  private static @NotNull LongRangeSet fromAnnotation(@NotNull PsiAnnotation annotation) {
    String qualifiedName = annotation.getQualifiedName();
    if (qualifiedName == null) {
      // unresolved annotation?
      return LongRangeSet.all();
    }
    switch (qualifiedName) {
      case JETBRAINS_RANGE, CHECKER_RANGE -> {
        Long from = AnnotationUtil.getLongAttributeValue(annotation, "from");
        Long to = AnnotationUtil.getLongAttributeValue(annotation, "to");
        if (from != null && to != null && to >= from) {
          return LongRangeSet.range(from, to);
        }
      }
      case VALIDATION_MIN -> {
        Long minValue = AnnotationUtil.getLongAttributeValue(annotation, "value");
        if (minValue != null && annotation.findDeclaredAttributeValue("groups") == null) {
          return LongRangeSet.range(minValue, Long.MAX_VALUE);
        }
      }
      case VALIDATION_MAX -> {
        Long maxValue = AnnotationUtil.getLongAttributeValue(annotation, "value");
        if (maxValue != null && annotation.findDeclaredAttributeValue("groups") == null) {
          return LongRangeSet.range(Long.MIN_VALUE, maxValue);
        }
      }
      case CHECKER_GTE_NEGATIVE_ONE -> {
        return LongRangeSet.range(-1, Long.MAX_VALUE);
      }
      case JSR305_NONNEGATIVE, CHECKER_NON_NEGATIVE -> {
        return LongRangeSet.range(0, Long.MAX_VALUE);
      }
      case CHECKER_POSITIVE -> {
        return LongRangeSet.range(1, Long.MAX_VALUE);
      }
    }
    return LongRangeSet.all();
  }

  /**
   * Cast the specified range to the given Java primitive numeric type
   * @param range range to cast
   * @param type target type
   * @return result of cast
   * @throws IllegalArgumentException if the specified type is not integral numeric type
   */
  public static @NotNull LongRangeSet castTo(@NotNull LongRangeSet range, @NotNull PsiPrimitiveType type) {
    if (!TypeConversionUtil.isIntegralNumberType(type)) {
      throw new IllegalArgumentException(type.toString());
    }
    if (type.equals(PsiType.LONG)) return range;
    if (range.isEmpty()) return range;
    Long value = range.getConstantValue();
    if (value != null) {
      long point = value;
      long newValue;
      if (PsiType.CHAR.equals(type)) {
        newValue = (char)point;
      }
      else if (PsiType.INT.equals(type)) {
        newValue = (int)point;
      }
      else if (PsiType.SHORT.equals(type)) {
        newValue = (short)point;
      }
      else if (PsiType.BYTE.equals(type)) {
        newValue = (byte)point;
      }
      else {
        throw new IllegalArgumentException(type.toString());
      }
      return newValue == point ? range : LongRangeSet.point(newValue);
    }
    LongRangeSet result = LongRangeSet.empty();
    for (LongRangeSet subRange : range.asRanges()) {
      result = result.join(castContinuousRange(subRange, type));
    }
    return result;
  }

  private static @NotNull LongRangeSet castContinuousRange(@NotNull LongRangeSet range, @NotNull PsiPrimitiveType type) {
    if (PsiType.BYTE.equals(type)) {
      LongRangeSet result = mask(range, Byte.SIZE, type);
      assert BYTE_RANGE.contains(result) : range;
      return result;
    }
    if (PsiType.SHORT.equals(type)) {
      LongRangeSet result = mask(range, Short.SIZE, type);
      assert SHORT_RANGE.contains(result) : range;
      return result;
    }
    if (PsiType.INT.equals(type)) {
      LongRangeSet result = mask(range, Integer.SIZE, type);
      assert INT_RANGE.contains(result) : range;
      return result;
    }
    if (PsiType.CHAR.equals(type)) {
      if (range.min() <= Character.MIN_VALUE && range.max() >= Character.MAX_VALUE) return CHAR_RANGE;
      if (range.min() >= Character.MIN_VALUE && range.max() <= Character.MAX_VALUE) return range;
      return range.bitwiseAnd(LongRangeSet.point(0xFFFF));
    }
    throw new IllegalArgumentException(type.toString());
  }

  private static @NotNull LongRangeSet mask(@NotNull LongRangeSet range, int size, @NotNull PsiPrimitiveType type) {
    long addend = 1L << (size - 1);
    if (range.min() <= -addend && range.max() >= addend - 1) return Objects.requireNonNull(typeRange(type));
    if (range.min() >= -addend && range.max() <= addend - 1) return range;
    long mask = (1L << size) - 1;
    return range.plus(LongRangeSet.point(addend), LongRangeType.INT64)
      .bitwiseAnd(LongRangeSet.point(mask)).plus(LongRangeSet.point(-addend), LongRangeType.INT64);
  }

  /**
   * Creates a range for given type (for primitives and boxed: values range)
   *
   * @param type type to create a range for
   * @return a range or null if type is not supported
   */
  public static @Nullable LongRangeSet typeRange(@Nullable PsiType type) {
    return typeRange(type, false);
  }

  /**
   * Creates a range for given type (for primitives and boxed: values range)
   *
   * @param type           type to create a range for
   * @param useAnnotations whether to check type annotations such as {@code @Range} to narrow down the search
   * @return a range or null if type is not supported
   */
  public static @Nullable LongRangeSet typeRange(@Nullable PsiType type, boolean useAnnotations) {
    if (!(type instanceof PsiPrimitiveType) && !TypeConversionUtil.isPrimitiveWrapper(type)) return null;
    type = PsiPrimitiveType.getOptionallyUnboxedType(type);
    if (type != null) {
      LongRangeSet result = LongRangeSet.all();
      if (useAnnotations) {
        for (PsiAnnotation annotation : type.getAnnotations()) {
          result = result.meet(fromAnnotation(annotation));
        }
      }
      if (type.equals(PsiType.BYTE)) {
        return BYTE_RANGE.meet(result);
      }
      if (type.equals(PsiType.CHAR)) {
        return CHAR_RANGE.meet(result);
      }
      if (type.equals(PsiType.SHORT)) {
        return SHORT_RANGE.meet(result);
      }
      if (type.equals(PsiType.INT)) {
        return INT_RANGE.meet(result);
      }
      if (type.equals(PsiType.LONG)) {
        return result;
      }
    }
    return null;
  }

  public static @NotNull LongRangeType getLongRangeType(@NotNull PsiType jvmType) {
    if (jvmType.equals(PsiType.LONG)) {
      return LongRangeType.INT64;
    }
    else if (jvmType.equals(PsiType.INT)) {
      return LongRangeType.INT32;
    }
    throw new UnsupportedOperationException();
  }

  /**
   * @return LongRangeSet describing possible array or string indices (from 0 to Integer.MAX_VALUE)
   */
  public static LongRangeSet indexRange() {
    return INDEX_RANGE;
  }

  /**
   * @param range range to format
   * @param type type of the value (e.g. PsiType.INT)
   * @return human-readable localized text that represents a given range
   */
  public static @NotNull @Nls String getPresentationText(@NotNull LongRangeSet range, PsiType type) {
    LongRangeSet fullTypeRange = typeRange(type);
    return range.getPresentationText(fullTypeRange == null ? LongRangeSet.all() : fullTypeRange);
  }

  /**
   * @param token Java token (like {@link JavaTokenType#PLUS})
   * @return a corresponding {@link LongRangeBinOp} constant; null if no constant corresponds for a given token
   */
  public static @Nullable LongRangeBinOp binOpFromToken(IElementType token) {
    if (token == null) return null;
    if (token.equals(JavaTokenType.PLUS)) {
      return LongRangeBinOp.PLUS;
    }
    if (token.equals(JavaTokenType.MINUS)) {
      return LongRangeBinOp.MINUS;
    }
    if (token.equals(JavaTokenType.AND)) {
      return LongRangeBinOp.AND;
    }
    if (token.equals(JavaTokenType.OR)) {
      return LongRangeBinOp.OR;
    }
    if (token.equals(JavaTokenType.XOR)) {
      return LongRangeBinOp.XOR;
    }
    if (token.equals(JavaTokenType.PERC)) {
      return LongRangeBinOp.MOD;
    }
    if (token.equals(JavaTokenType.DIV)) {
      return LongRangeBinOp.DIV;
    }
    if (token.equals(JavaTokenType.LTLT)) {
      return LongRangeBinOp.SHL;
    }
    if (token.equals(JavaTokenType.GTGT)) {
      return LongRangeBinOp.SHR;
    }
    if (token.equals(JavaTokenType.GTGTGT)) {
      return LongRangeBinOp.USHR;
    }
    if (token.equals(JavaTokenType.ASTERISK)) {
      return LongRangeBinOp.MUL;
    }
    return null;
  }
}
