// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeType;
import com.intellij.codeInspection.dataFlow.types.DfIntType;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public final class SuspiciousSystemArraycopyInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return (String)infos[0];
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuspiciousSystemArraycopyVisitor();
  }

  private static class SuspiciousSystemArraycopyVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiClassType objectType = TypeUtils.getObjectType(expression);
      if (!MethodCallUtils.isCallToMethod(expression, "java.lang.System", PsiTypes.voidType(), "arraycopy",
                                          objectType, PsiTypes.intType(), objectType, PsiTypes.intType(), PsiTypes.intType())) {
        return;
      }
      final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
      if (arguments.length != 5) {
        return;
      }
      final PsiExpression srcPos = arguments[1];
      final PsiExpression destPos = arguments[3];
      final PsiExpression length = arguments[4];
      final PsiExpression src = arguments[0];
      final PsiExpression dest = arguments[2];
      checkRanges(src, srcPos, dest, destPos, length, expression);
      final PsiType srcType = src.getType();
      if (srcType == null) {
        return;
      }
      boolean notArrayReported = false;
      if (!(srcType instanceof PsiArrayType)) {
        registerError(src, InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor4"));
        notArrayReported = true;
      }
      final PsiType destType = dest.getType();
      if (destType == null) {
        return;
      }
      if (!(destType instanceof PsiArrayType)) {
        registerError(dest, InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor5"));
        notArrayReported = true;
      }
      if (notArrayReported) {
        return;
      }
      final PsiArrayType srcArrayType = (PsiArrayType)srcType;
      final PsiArrayType destArrayType = (PsiArrayType)destType;
      final PsiType srcComponentType = srcArrayType.getComponentType();
      final PsiType destComponentType = destArrayType.getComponentType();
      if (!(srcComponentType instanceof PsiPrimitiveType)) {
        if (!destComponentType.isAssignableFrom(srcComponentType)) {
          registerError(dest, InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor6",
                                                              srcType.getCanonicalText(),
                                                              destType.getCanonicalText()));
        }
      }
      else if (!destComponentType.equals(srcComponentType)) {
        registerError(dest, InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor6",
                                                            srcType.getCanonicalText(),
                                                            destType.getCanonicalText()));
      }
    }

    private void checkRanges(@NotNull PsiExpression src,
                             @NotNull PsiExpression srcPos,
                             @NotNull PsiExpression dest,
                             @NotNull PsiExpression destPos,
                             @NotNull PsiExpression length,
                             @NotNull PsiMethodCallExpression call) {
      CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(src);
      if (result == null) return;

      LongRangeSet srcLengthSet = DfIntType.extractRange(SpecialField.ARRAY_LENGTH.getFromQualifier(result.getDfType(src)));
      LongRangeSet destLengthSet = DfIntType.extractRange(SpecialField.ARRAY_LENGTH.getFromQualifier(result.getDfType(dest)));
      LongRangeSet srcPosSet = DfIntType.extractRange(result.getDfType(srcPos));
      LongRangeSet destPosSet = DfIntType.extractRange(result.getDfType(destPos));
      LongRangeSet lengthSet = DfIntType.extractRange(result.getDfType(length));
      LongRangeSet srcPossibleLengthToCopy = srcLengthSet.minus(srcPosSet, LongRangeType.INT32);
      LongRangeSet destPossibleLengthToCopy = destLengthSet.minus(destPosSet, LongRangeType.INT32);
      long lengthMin = lengthSet.min();
      if (lengthMin > destPossibleLengthToCopy.max()) {
        registerError(length, InspectionGadgetsBundle
          .message("suspicious.system.arraycopy.problem.descriptor.length.bigger.dest", lengthSet.toString()));
        return;
      }
      if (lengthMin > srcPossibleLengthToCopy.max()) {
        registerError(length, InspectionGadgetsBundle
          .message("suspicious.system.arraycopy.problem.descriptor.length.bigger.src", lengthSet.toString()));
        return;
      }

      if (!PsiEquivalenceUtil.areElementsEquivalent(src, dest)) return;
      LongRangeSet srcRange = getDefiniteRange(srcPosSet, lengthSet);
      LongRangeSet destRange = getDefiniteRange(destPosSet, lengthSet);
      if (srcRange.intersects(destRange)) {
        PsiElement name = call.getMethodExpression().getReferenceNameElement();
        PsiElement elementToHighlight = name == null ? call : name;
        registerError(elementToHighlight,
                      InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor.ranges.intersect"));
      }
    }

    @NotNull
    private static LongRangeSet getDefiniteRange(@NotNull LongRangeSet startSet, @NotNull LongRangeSet lengthSet) {
      long maxLeftBorder = startSet.max();
      LongRangeSet lengthMinusOne = lengthSet.minus(LongRangeSet.point(1), LongRangeType.INT32);
      long minRightBorder = startSet.plus(lengthMinusOne, LongRangeType.INT32).min();
      if (maxLeftBorder > minRightBorder) return LongRangeSet.empty();
      return LongRangeSet.range(maxLeftBorder, minRightBorder);
    }
  }
}