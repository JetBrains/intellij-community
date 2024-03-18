/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfLongType;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class CastThatLosesPrecisionInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreIntegerCharCasts = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreOverflowingByteCasts = false;

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "NumericCastThatLosesPrecision";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiType operandType = (PsiType)infos[0];
    boolean negativeOnly = (boolean)infos[1];
    return InspectionGadgetsBundle.message(negativeOnly ?
                                           "cast.that.loses.precision.negative.problem.descriptor" :
                                           "cast.that.loses.precision.problem.descriptor",
                                           operandType.getPresentableText());
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreIntegerCharCasts", InspectionGadgetsBundle.message("cast.that.loses.precision.option")),
      checkbox("ignoreOverflowingByteCasts", InspectionGadgetsBundle.message("ignore.overflowing.byte.casts.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CastThatLosesPrecisionVisitor();
  }

  private class CastThatLosesPrecisionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
      final PsiType castType = expression.getType();
      if (!ClassUtils.isPrimitiveNumericType(castType)) {
        return;
      }
      final PsiExpression operand = expression.getOperand();
      if (operand == null) {
        return;
      }
      final PsiType operandType = operand.getType();
      if (!ClassUtils.isPrimitiveNumericType(operandType) || !TypeUtils.isNarrowingConversion(operandType, castType)) {
        return;
      }
      if (ignoreIntegerCharCasts && PsiTypes.intType().equals(operandType) && PsiTypes.charType().equals(castType)) {
        return;
      }
      if (PsiTypes.longType().equals(operandType) && PsiTypes.intType().equals(castType)) {
        final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
        if (MethodUtils.isHashCode(method)) {
          return;
        }
      }
      Object result = ExpressionUtils.computeConstantExpression(operand);
      if (result instanceof Character) {
        result = Integer.valueOf(((Character)result).charValue());
      }
      if (result instanceof Number number) {
        if (ignoreOverflowingByteCasts && PsiTypes.intType().equals(operandType) && PsiTypes.byteType().equals(castType)) {
          final int i = number.intValue();
          if (i > Byte.MIN_VALUE && i <= 255) {
            return;
          }
        }
        if (valueIsContainableInType(number, castType)) {
          return;
        }
      }
      final PsiTypeElement castTypeElement = expression.getCastType();
      if (castTypeElement == null) {
        return;
      }
      LongRangeSet targetRange = JvmPsiRangeSetUtil.typeRange(castType);
      LongRangeSet lostRange = LongRangeSet.all();
      if (targetRange != null && JvmPsiRangeSetUtil.typeRange(operandType) != null) {
        LongRangeSet valueRange = DfLongType.extractRange(CommonDataflow.getDfType(operand));
        lostRange = valueRange.subtract(targetRange);
        if (lostRange.isEmpty()) return;
      }
      registerError(castTypeElement, operandType, lostRange.max() < 0);
    }

    private static boolean valueIsContainableInType(Number value, PsiType type) {
      final long longValue = value.longValue();
      final double doubleValue = value.doubleValue();
      if (PsiTypes.byteType().equals(type)) {
        return longValue >= (long)Byte.MIN_VALUE &&
               longValue <= (long)Byte.MAX_VALUE &&
               doubleValue >= (double)Byte.MIN_VALUE &&
               doubleValue <= (double)Byte.MAX_VALUE;
      }
      else if (PsiTypes.charType().equals(type)) {
        return longValue >= (long)Character.MIN_VALUE &&
               longValue <= (long)Character.MAX_VALUE &&
               doubleValue >= (double)Character.MIN_VALUE &&
               doubleValue <= (double)Character.MAX_VALUE;
      }
      else if (PsiTypes.shortType().equals(type)) {
        return longValue >= (long)Short.MIN_VALUE &&
               longValue <= (long)Short.MAX_VALUE &&
               doubleValue >= (double)Short.MIN_VALUE &&
               doubleValue <= (double)Short.MAX_VALUE;
      }
      else if (PsiTypes.intType().equals(type)) {
        return longValue >= (long)Integer.MIN_VALUE &&
               longValue <= (long)Integer.MAX_VALUE &&
               doubleValue >= (double)Integer.MIN_VALUE &&
               doubleValue <= (double)Integer.MAX_VALUE;
      }
      else if (PsiTypes.longType().equals(type)) {
        return doubleValue >= (double)Long.MIN_VALUE && doubleValue <= (double)Long.MAX_VALUE;
      }
      else if (PsiTypes.floatType().equals(type)) {
        return doubleValue == value.floatValue();
      }
      else if (PsiTypes.doubleType().equals(type)) {
        return true;
      }
      return false;
    }
  }
}