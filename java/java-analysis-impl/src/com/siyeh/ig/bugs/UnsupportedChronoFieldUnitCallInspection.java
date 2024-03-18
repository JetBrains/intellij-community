// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.util.ChronoUtil;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;

public final class UnsupportedChronoFieldUnitCallInspection extends AbstractBaseJavaLocalInspectionTool {

  private final CallMatcher myMatcher = anyOf(
    ChronoUtil.CHRONO_ALL_GET_MATCHERS,
    ChronoUtil.CHRONO_PLUS_MINUS_MATCHERS,
    ChronoUtil.CHRONO_WITH_MATCHERS
  );

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        String methodName = call.getMethodExpression().getReferenceName();
        if (!"get".equals(methodName) && !"getLong".equals(methodName) && !"with".equals(methodName) &&
            !"plus".equals(methodName) && !"minus".equals(methodName)) {
          return;
        }

        if (!myMatcher.test(call)) {
          return;
        }

        PsiMethod method = call.resolveMethod();
        if (method == null) return;

        int fieldArgumentIndex = 1;
        if ("get".equals(methodName) || "getLong".equals(methodName) || "with".equals(methodName)) {
          fieldArgumentIndex = 0;
        }
        PsiExpression[] expressions = call.getArgumentList().getExpressions();
        if (expressions.length < fieldArgumentIndex + 1) {
          return;
        }
        @Nullable PsiExpression fieldExpression = expressions[fieldArgumentIndex];
        if (fieldExpression == null) return;
        CommonDataflow.DataflowResult dataflowResult = CommonDataflow.getDataflowResult(fieldExpression);
        if (dataflowResult == null) return;
        Set<Object> values = dataflowResult.getExpressionValues(fieldExpression);
        Set<PsiEnumConstant> enumConstants = new HashSet<>();
        for (Object value : values) {
          if (value instanceof PsiEnumConstant enumConstant) {
            enumConstants.add(enumConstant);
          }
          else {
            return;
          }
        }
        List<PsiEnumConstant> unsupportedEnums = getUnsupportedEnums(enumConstants, method);
        if (unsupportedEnums == null) {
          return;
        }
        if (!unsupportedEnums.isEmpty()) {
          registerProblems(unsupportedEnums, fieldExpression, holder);
        }
      }

      private static void registerProblems(@NotNull List<PsiEnumConstant> enums,
                                           @NotNull PsiExpression expression,
                                           @NotNull ProblemsHolder holder) {
        StringJoiner joiner = new StringJoiner(", ");
        enums.forEach(enumName -> joiner.add("'" + enumName.getName() + "'"));
        String description = enums.size() == 1 ?
                             InspectionGadgetsBundle.message("inspection.unsupported.chrono.value.message", joiner.toString()) :
                             InspectionGadgetsBundle.message("inspection.unsupported.chrono.values.message", joiner.toString());
        holder.registerProblem(expression, description);
      }

      @Nullable
      private static List<PsiEnumConstant> getUnsupportedEnums(Set<PsiEnumConstant> constants, PsiMethod method) {
        String methodName = method.getName();
        ArrayList<PsiEnumConstant> result = new ArrayList<>();
        for (PsiEnumConstant enumConstant : constants) {
          PsiClass containingClass = enumConstant.getContainingClass();
          if (containingClass == null || !containingClass.isEnum()) {
            return null;
          }
          String classQualifiedName = containingClass.getQualifiedName();
          if (!(ChronoUtil.CHRONO_FIELD.equals(classQualifiedName) &&
                (methodName.equals("get") || methodName.equals("getLong") || methodName.equals("with"))) &&
              !(ChronoUtil.CHRONO_UNIT.equals(classQualifiedName)) && (methodName.equals("plus") || methodName.equals("minus"))) {
            return null;
          }
          String enumConstantName = enumConstant.getName();
          switch (methodName) {
            case "get", "getLong" -> {
              if (!ChronoUtil.isAnyGetSupported(method, ChronoUtil.getChronoField(enumConstantName))) {
                result.add(enumConstant);
              }
            }
            case "with" -> {
              if (!ChronoUtil.isWithSupported(method, ChronoUtil.getChronoField(enumConstantName))) {
                result.add(enumConstant);
              }
            }
            case "plus", "minus" -> {
              if (!ChronoUtil.isPlusMinusSupported(method, ChronoUtil.getChronoUnit(enumConstantName))) {
                result.add(enumConstant);
              }
            }
          }
        }
        return result;
      }
    };
  }
}
