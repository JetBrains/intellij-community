// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.codeInspection.util.ChronoUtil;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.StringJoiner;

import static com.intellij.psi.CommonClassNames.*;

public class RedundantJavaTimeOperationsInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  private static final CallMatcher FROM_MATCHER = CallMatcher.anyOf(
    CallMatcher.staticCall(JAVA_TIME_LOCAL_TIME, "from").parameterCount(1),
    CallMatcher.staticCall(JAVA_TIME_LOCAL_DATE, "from").parameterCount(1),
    CallMatcher.staticCall(JAVA_TIME_LOCAL_DATE_TIME, "from")
      .parameterCount(1),
    CallMatcher.staticCall(JAVA_TIME_OFFSET_DATE_TIME, "from")
      .parameterCount(1),
    CallMatcher.staticCall(JAVA_TIME_OFFSET_TIME, "from").parameterCount(1),
    CallMatcher.staticCall(JAVA_TIME_ZONED_DATE_TIME, "from")
      .parameterCount(1));

  private static final CallMatcher LOCAL_DATE_OF_MATCHER =
    CallMatcher.anyOf(CallMatcher.staticCall(JAVA_TIME_LOCAL_DATE, "of").parameterTypes("int", "int", "int"),
                      CallMatcher.staticCall(JAVA_TIME_LOCAL_DATE, "of").parameterTypes("int", "java.time.Month", "int"));

  private static final CallMatcher GET_YEAR_MATCHER =
    CallMatcher.anyOf(
      CallMatcher.instanceCall(JAVA_TIME_LOCAL_DATE, "getYear").parameterCount(0),
      CallMatcher.instanceCall(JAVA_TIME_LOCAL_DATE_TIME, "getYear").parameterCount(0),
      CallMatcher.instanceCall(JAVA_TIME_OFFSET_DATE_TIME, "getYear").parameterCount(0),
      CallMatcher.instanceCall(JAVA_TIME_ZONED_DATE_TIME, "getYear").parameterCount(0));

  private static final CallMatcher GET_MONTH_MATCHER =
    CallMatcher.anyOf(
      CallMatcher.instanceCall(JAVA_TIME_LOCAL_DATE, "getMonth").parameterCount(0),
      CallMatcher.instanceCall(JAVA_TIME_LOCAL_DATE_TIME, "getMonth").parameterCount(0),
      CallMatcher.instanceCall(JAVA_TIME_OFFSET_DATE_TIME, "getMonth").parameterCount(0),
      CallMatcher.instanceCall(JAVA_TIME_ZONED_DATE_TIME, "getMonth").parameterCount(0));

  private static final CallMatcher GET_MONTH_VALUE_MATCHER =
    CallMatcher.anyOf(
      CallMatcher.instanceCall(JAVA_TIME_LOCAL_DATE, "getMonthValue").parameterCount(0),
      CallMatcher.instanceCall(JAVA_TIME_LOCAL_DATE_TIME, "getMonthValue").parameterCount(0),
      CallMatcher.instanceCall(JAVA_TIME_OFFSET_DATE_TIME, "getMonthValue").parameterCount(0),
      CallMatcher.instanceCall(JAVA_TIME_ZONED_DATE_TIME, "getMonthValue").parameterCount(0));

  private static final CallMatcher GET_DAY_OF_MONTH_MATCHER =
    CallMatcher.anyOf(
      CallMatcher.instanceCall(JAVA_TIME_LOCAL_DATE, "getDayOfMonth").parameterCount(0),
      CallMatcher.instanceCall(JAVA_TIME_LOCAL_DATE_TIME, "getDayOfMonth").parameterCount(0),
      CallMatcher.instanceCall(JAVA_TIME_OFFSET_DATE_TIME, "getDayOfMonth").parameterCount(0),
      CallMatcher.instanceCall(JAVA_TIME_ZONED_DATE_TIME, "getDayOfMonth").parameterCount(0));

  private static final CallMatcher LOCAL_TIME_OF_MATCHER =
    CallMatcher.staticCall(JAVA_TIME_LOCAL_TIME, "of").parameterTypes("int", "int", "int", "int");

  private static final CallMatcher GET_HOUR_MATCHER =
    CallMatcher.anyOf(CallMatcher.instanceCall(JAVA_TIME_LOCAL_TIME, "getHour").parameterCount(0),
                      CallMatcher.instanceCall(JAVA_TIME_LOCAL_DATE_TIME, "getHour").parameterCount(0),
                      CallMatcher.instanceCall(JAVA_TIME_OFFSET_DATE_TIME, "getHour").parameterCount(0),
                      CallMatcher.instanceCall(JAVA_TIME_ZONED_DATE_TIME, "getHour").parameterCount(0));

  private static final CallMatcher GET_MINUTE_MATCHER =
    CallMatcher.anyOf(CallMatcher.instanceCall(JAVA_TIME_LOCAL_TIME, "getMinute").parameterCount(0),
                      CallMatcher.instanceCall(JAVA_TIME_LOCAL_DATE_TIME, "getMinute").parameterCount(0),
                      CallMatcher.instanceCall(JAVA_TIME_OFFSET_DATE_TIME, "getMinute").parameterCount(0),
                      CallMatcher.instanceCall(JAVA_TIME_ZONED_DATE_TIME, "getMinute").parameterCount(0));

  private static final CallMatcher GET_SECOND_MATCHER =
    CallMatcher.anyOf(CallMatcher.instanceCall(JAVA_TIME_LOCAL_TIME, "getSecond").parameterCount(0),
                      CallMatcher.instanceCall(JAVA_TIME_LOCAL_DATE_TIME, "getSecond").parameterCount(0),
                      CallMatcher.instanceCall(JAVA_TIME_OFFSET_DATE_TIME, "getSecond").parameterCount(0),
                      CallMatcher.instanceCall(JAVA_TIME_ZONED_DATE_TIME, "getSecond").parameterCount(0));

  private static final CallMatcher GET_NANO_MATCHER =
    CallMatcher.anyOf(CallMatcher.instanceCall(JAVA_TIME_LOCAL_TIME, "getNano").parameterCount(0),
                      CallMatcher.instanceCall(JAVA_TIME_LOCAL_DATE_TIME, "getNano").parameterCount(0),
                      CallMatcher.instanceCall(JAVA_TIME_OFFSET_DATE_TIME, "getNano").parameterCount(0),
                      CallMatcher.instanceCall(JAVA_TIME_ZONED_DATE_TIME, "getNano").parameterCount(0));
  private static final String TO_LOCAL_TIME = "toLocalTime";
  private static final String TO_LOCAL_DATE = "toLocalDate";

  private static final CallMatcher CAN_BE_SIMPLIFIED_MATCHERS = CallMatcher.anyOf(
    ChronoUtil.CHRONO_GET_MATCHERS,
    ChronoUtil.CHRONO_WITH_MATCHERS,
    ChronoUtil.CHRONO_PLUS_MINUS_MATCHERS
  );

  private static final CallMatcher COMPARE_TO_METHODS = CallMatcher.anyOf(
    CallMatcher.instanceCall(JAVA_TIME_LOCAL_DATE, "compareTo").parameterTypes("java.time.chrono.ChronoLocalDate"),
    CallMatcher.instanceCall(JAVA_TIME_LOCAL_TIME, "compareTo").parameterTypes(JAVA_TIME_LOCAL_TIME),
    CallMatcher.instanceCall(JAVA_TIME_LOCAL_DATE_TIME, "compareTo")
      .parameterTypes("java.time.chrono.ChronoLocalDateTime"),
    CallMatcher.instanceCall(JAVA_TIME_OFFSET_TIME, "compareTo").parameterTypes(JAVA_TIME_OFFSET_TIME),
    CallMatcher.instanceCall(JAVA_TIME_OFFSET_DATE_TIME, "compareTo")
      .parameterTypes(JAVA_TIME_OFFSET_DATE_TIME)
  );

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @SuppressWarnings("UnnecessaryReturnStatement")
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        if (fixRedundantFrom(call)) return;
        if (fixRedundantOfLocalDate(call)) return;
        if (fixRedundantOfLocalTime(call)) return;
        if (fixRedundantExplicitChronoField(call)) return;
        if (fixRedundantComparison(call)) return;
      }

      private boolean fixRedundantComparison(@NotNull PsiMethodCallExpression call) {
        if (!COMPARE_TO_METHODS.test(call)) return false;
        PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
        if (nameElement == null) return false;

        final PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
        if (qualifierExpression == null) {
          return false;
        }
        PsiType[] types = call.getArgumentList().getExpressionTypes();
        if (types.length != 1) {
          return false;
        }
        final PsiType argumentType = types[0];
        if (argumentType == null || !argumentType.equals(qualifierExpression.getType())) {
          return false;
        }

        PsiBinaryExpression binOp = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(call.getParent()), PsiBinaryExpression.class);
        if (binOp == null) return false;
        RelationType relationType = DfaPsiUtil.getRelationByToken(binOp.getOperationTokenType());
        if (relationType == RelationType.IS || relationType == RelationType.IS_NOT) return false;
        if (relationType == null) return false;
        if (ExpressionUtils.isZero(binOp.getLOperand())) {
          relationType = relationType.getFlipped();
          if (relationType == null) return false;
        }
        else if (!ExpressionUtils.isZero(binOp.getROperand())) {
          return false;
        }
        holder.registerProblem(nameElement,
                               InspectionGadgetsBundle.message(
                                 "inspection.redundant.java.time.operation.compare.java.time.problem.descriptor"),
                               new InlineCompareToTimeCallFix(relationType, argumentType.getCanonicalText()));
        return true;
      }

      private boolean fixRedundantExplicitChronoField(@NotNull PsiMethodCallExpression call) {
        String methodName = call.getMethodExpression().getReferenceName();
        if (!"get".equals(methodName) && !"with".equals(methodName) &&
            !"plus".equals(methodName) && !"minus".equals(methodName)) {
          return false;
        }

        if (!CAN_BE_SIMPLIFIED_MATCHERS.matches(call)) return false;
        int fieldArgumentIndex = 1;
        if ("get".equals(methodName) || "with".equals(methodName)) {
          fieldArgumentIndex = 0;
        }
        PsiExpression[] expressions = call.getArgumentList().getExpressions();
        if (expressions.length < fieldArgumentIndex + 1) {
          return false;
        }
        @Nullable PsiExpression fieldExpression = expressions[fieldArgumentIndex];
        String chronoEnumName = getNameOfChronoEnum(fieldExpression, methodName);
        if (chronoEnumName == null) return false;
        String newMethodName = getNewMethodName(chronoEnumName, call);
        if (newMethodName == null) return false;
        PsiElement identifier = getIdentifier(call.getMethodExpression());
        if (identifier == null) return false;
        holder.registerProblem(identifier,
                               InspectionGadgetsBundle.message("inspection.redundant.java.time.operation.explicit.chrono.field.problem.descriptor"),
                               new InlineChronoEnumCallFix(newMethodName, fieldArgumentIndex));
        return true;
      }

      private static @Nullable String getNewMethodName(@NotNull String chronoEnumName, @NotNull PsiMethodCallExpression call) {
        PsiMethod method = call.resolveMethod();
        if (method == null) {
          return null;
        }
        if (!isAvailableCall(method, chronoEnumName)) {
          return null;
        }
        //'with(ChronoField, long)' can be converted only to 'with...(int)'
        if ("with".equals(method.getName())) {
          PsiType[] types = call.getArgumentList().getExpressionTypes();
          if (types.length != 2 || types[1] == null || TypeConversionUtil.getTypeRank(types[1]) > TypeConversionUtil.INT_RANK) {
            return null;
          }
        }
        String methodName = method.getName();
        return findEquivalentMethod(chronoEnumName, methodName);
      }

      private static boolean isAvailableCall(@NotNull PsiMethod method, @NotNull String chronoEnumName) {
        return switch (method.getName()) {
          case "get" -> ChronoUtil.isAnyGetSupported(method, ChronoUtil.getChronoField(chronoEnumName));
          case "with" -> ChronoUtil.isWithSupported(method, ChronoUtil.getChronoField(chronoEnumName));
          case "plus", "minus" -> ChronoUtil.isPlusMinusSupported(method, ChronoUtil.getChronoUnit(chronoEnumName));
          default -> false;
        };
      }

      private static @Nullable String findEquivalentMethod(@NotNull String chronoEnumName, @NotNull String methodName) {
        return switch (methodName) {
          case "plus" -> switch (chronoEnumName) {
            case "NANOS" -> "plusNanos";
            case "SECONDS" -> "plusSeconds";
            case "MINUTES" -> "plusMinutes";
            case "HOURS" -> "plusHours";
            case "DAYS" -> "plusDays";
            case "WEEKS" -> "plusWeeks";
            case "MONTHS" -> "plusMonths";
            case "YEARS" -> "plusYears";
            default -> null;
          };
          case "minus" -> switch (chronoEnumName) {
            case "NANOS" -> "minusNanos";
            case "SECONDS" -> "minusSeconds";
            case "MINUTES" -> "minusMinutes";
            case "HOURS" -> "minusHours";
            case "DAYS" -> "minusDays";
            case "WEEKS" -> "minusWeeks";
            case "MONTHS" -> "minusMonths";
            case "YEARS" -> "minusYears";
            default -> null;
          };
          case "get" -> switch (chronoEnumName) {
            case "NANO_OF_SECOND" -> "getNano";
            case "SECOND_OF_MINUTE" -> "getSecond";
            case "MINUTE_OF_HOUR" -> "getMinute";
            case "HOUR_OF_DAY" -> "getHour";
            case "DAY_OF_MONTH" -> "getDayOfMonth";
            case "DAY_OF_YEAR" -> "getDayOfYear";
            case "MONTH_OF_YEAR" -> "getMonth";
            case "YEAR" -> "getYear";
            default -> null;
          };
          case "with" -> switch (chronoEnumName) {
            case "NANO_OF_SECOND" -> "withNano";
            case "SECOND_OF_MINUTE" -> "withSecond";
            case "MINUTE_OF_HOUR" -> "withMinute";
            case "HOUR_OF_DAY" -> "withHour";
            case "DAY_OF_MONTH" -> "withDayOfMonth";
            case "DAY_OF_YEAR" -> "withDayOfYear";
            case "MONTH_OF_YEAR" -> "withMonth";
            case "YEAR" -> "withYear";
            default -> null;
          };
          default -> null;
        };
      }

      private static @Nullable PsiElement getIdentifier(@Nullable PsiReferenceExpression expression) {
        if (expression == null) return null;
        PsiIdentifier[] identifiers = PsiTreeUtil.getChildrenOfType(expression, PsiIdentifier.class);
        if (identifiers == null || identifiers.length != 1) {
          return null;
        }
        return identifiers[0];
      }

      private static @Nullable String getNameOfChronoEnum(@Nullable PsiExpression expression, @Nullable String methodName) {
        if (expression == null || methodName == null) return null;
        if (!(expression instanceof PsiReferenceExpression referenceExpression)) {
          return null;
        }
        PsiElement resolvedElement = referenceExpression.resolve();
        if (!(resolvedElement instanceof PsiEnumConstant enumConstant)) {
          return null;
        }
        PsiClass containingClass = enumConstant.getContainingClass();
        if (containingClass == null || !containingClass.isEnum()) {
          return null;
        }
        String classQualifiedName = containingClass.getQualifiedName();
        if (!(ChronoUtil.CHRONO_FIELD.equals(classQualifiedName) && (methodName.equals("get") || methodName.equals("with"))) &&
            !(ChronoUtil.CHRONO_UNIT.equals(classQualifiedName)) && (methodName.equals("plus") || methodName.equals("minus"))) {
          return null;
        }
        return enumConstant.getName();
      }

      private boolean fixRedundantOfLocalTime(@NotNull PsiMethodCallExpression call) {
        if (!LOCAL_TIME_OF_MATCHER.test(call)) return false;
        PsiExpression[] arguments = call.getArgumentList().getExpressions();
        if (arguments.length != 4) return false;
        if (!(arguments[0] instanceof PsiMethodCallExpression firstArgumentCall)) return false;
        if (!GET_HOUR_MATCHER.test(firstArgumentCall)) return false;
        if (!(arguments[1] instanceof PsiMethodCallExpression secondArgumentCall)) return false;
        if (!GET_MINUTE_MATCHER.test(secondArgumentCall)) return false;
        if (!(arguments[2] instanceof PsiMethodCallExpression thirdArgumentCall)) return false;
        if (!GET_SECOND_MATCHER.test(thirdArgumentCall)) return false;
        if (!(arguments[3] instanceof PsiMethodCallExpression fourthArgumentCall)) return false;
        if (!GET_NANO_MATCHER.test(fourthArgumentCall)) return false;
        PsiExpression expression1 = firstArgumentCall.getMethodExpression().getQualifierExpression();
        if (!(PsiUtil.skipParenthesizedExprDown(expression1) instanceof PsiReferenceExpression referenceExpression1 &&
              referenceExpression1.resolve() instanceof PsiVariable variable1)) {
          return false;
        }
        if (!(areSameVariableReferences(holder.getProject(),
                                        expression1,
                                        secondArgumentCall.getMethodExpression().getQualifierExpression(),
                                        thirdArgumentCall.getMethodExpression().getQualifierExpression(),
                                        fourthArgumentCall.getMethodExpression().getQualifierExpression()))) {
          return false;
        }

        PsiClass variableClass = PsiUtil.resolveClassInClassTypeOnly(variable1.getType());
        if (variableClass == null) return false;
        PsiElement referenceNameElement = call.getMethodExpression().getReferenceNameElement();
        if (referenceNameElement == null) return false;

        PsiIdentifier identifier = variable1.getNameIdentifier();
        if (identifier == null) return false;
        if (JAVA_TIME_LOCAL_TIME.equals(variableClass.getQualifiedName())) {
          holder.registerProblem(referenceNameElement,
                                 InspectionGadgetsBundle.message("inspection.redundant.java.time.operation.creation.java.time.error.message", "LocalTime"),
                                 getHighlightTypeWithUnused(call),
                                 RedundantCreationFix.create(identifier.getText(), RedundantCreationFix.FixType.REMOVE,
                                                             InspectionGadgetsBundle.message(
                                                               "inspection.redundant.java.time.operation.creation.java.time.error.remove.fix.message",
                                                               "LocalTime.of()")));
          return true;
        }
        PsiMethod[] localTimes = variableClass.findMethodsByName(TO_LOCAL_TIME, false);
        if (localTimes.length != 1) return false;

        String newText = identifier.getText() + "." + TO_LOCAL_TIME + "()";

        holder.registerProblem(referenceNameElement,
                               InspectionGadgetsBundle.message("inspection.redundant.java.time.operation.creation.java.time.error.message", "LocalTime"),
                               RedundantCreationFix.create(newText, RedundantCreationFix.FixType.SIMPLIFY,
                                                           InspectionGadgetsBundle.message(
                                                             "inspection.redundant.java.time.operation.creation.java.time.error.replace.fix.message",
                                                             TO_LOCAL_TIME + "()")));
        return true;
      }

      private boolean fixRedundantOfLocalDate(@NotNull PsiMethodCallExpression call) {
        if (!LOCAL_DATE_OF_MATCHER.test(call)) return false;
        PsiExpression[] arguments = call.getArgumentList().getExpressions();
        if (arguments.length != 3) return false;
        if (!(arguments[0] instanceof PsiMethodCallExpression firstArgumentCall)) return false;
        if (!GET_YEAR_MATCHER.test(firstArgumentCall)) return false;
        if (!(arguments[1] instanceof PsiMethodCallExpression secondArgumentCall)) return false;
        if (!GET_MONTH_VALUE_MATCHER.test(secondArgumentCall) && !GET_MONTH_MATCHER.test(secondArgumentCall)) return false;
        if (!(arguments[2] instanceof PsiMethodCallExpression thirdArgumentCall)) return false;
        if (!GET_DAY_OF_MONTH_MATCHER.test(thirdArgumentCall)) return false;

        PsiExpression firstArgument = PsiUtil.skipParenthesizedExprDown(firstArgumentCall.getMethodExpression().getQualifierExpression());
        if (!(firstArgument instanceof PsiReferenceExpression referenceExpression &&
              referenceExpression.resolve() instanceof PsiVariable firstVariable)) {
          return false;
        }
        if (!areSameVariableReferences(holder.getProject(),
                                       firstArgument,
                                       secondArgumentCall.getMethodExpression().getQualifierExpression(),
                                       thirdArgumentCall.getMethodExpression().getQualifierExpression())) {
          return false;
        }

        PsiClass variableClass = PsiUtil.resolveClassInClassTypeOnly(firstArgument.getType());
        if (variableClass == null) return false;
        PsiElement referenceNameElement = call.getMethodExpression().getReferenceNameElement();
        if (referenceNameElement == null) return false;
        PsiIdentifier identifier = firstVariable.getNameIdentifier();
        if (identifier == null) return false;

        if (JAVA_TIME_LOCAL_DATE.equals(variableClass.getQualifiedName())) {
          holder.registerProblem(referenceNameElement,
                                 InspectionGadgetsBundle.message("inspection.redundant.java.time.operation.creation.java.time.error.message", "LocalDate"),
                                 getHighlightTypeWithUnused(call),
                                 RedundantCreationFix.create(identifier.getText(),
                                                             RedundantCreationFix.FixType.REMOVE,
                                                             InspectionGadgetsBundle.message(
                                                               "inspection.redundant.java.time.operation.creation.java.time.error.remove.fix.message",
                                                               "LocalDate.of()")));
          return true;
        }

        PsiMethod[] localDates = variableClass.findMethodsByName(TO_LOCAL_DATE, false);
        if (localDates.length != 1) return false;
        String newText = identifier.getText() + "." + TO_LOCAL_DATE + "()";

        holder.registerProblem(referenceNameElement,
                               InspectionGadgetsBundle.message("inspection.redundant.java.time.operation.creation.java.time.error.message", "LocalDate"),
                               RedundantCreationFix.create(newText, RedundantCreationFix.FixType.SIMPLIFY,
                                                           InspectionGadgetsBundle.message(
                                                             "inspection.redundant.java.time.operation.creation.java.time.error.replace.fix.message",
                                                             TO_LOCAL_DATE + "()")));
        return true;
      }

      private static boolean areSameVariableReferences(@NotNull Project project, PsiExpression... expressions) {
        if (expressions.length == 0) return false;
        PsiManager psiManager = PsiManager.getInstance(project);
        PsiVariable firstVariable = resolveVariable(expressions[0]);
        if (firstVariable == null) return false;
        for (PsiExpression expression : expressions) {
          PsiVariable variable = resolveVariable(expression);
          if (variable == null || !psiManager.areElementsEquivalent(firstVariable, variable)) {
            return false;
          }
        }
        return true;
      }

      @Nullable
      private static PsiVariable resolveVariable(@Nullable PsiExpression expression) {
        if (expression == null) return null;
        PsiExpression unwrappedExpression = PsiUtil.skipParenthesizedExprDown(expression);
        if (!(unwrappedExpression instanceof PsiReferenceExpression referenceExpression)) return null;
        PsiElement resolvedElement = referenceExpression.resolve();
        return (resolvedElement instanceof PsiVariable variable) ? variable : null;
      }

      private boolean fixRedundantFrom(@NotNull PsiMethodCallExpression call) {
        if (!FROM_MATCHER.test(call)) return false;
        PsiExpression[] arguments = call.getArgumentList().getExpressions();
        if (arguments.length != 1) return false;
        PsiExpression expression = arguments[0];
        PsiClass classOfArgument = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
        PsiMethod method = call.resolveMethod();
        if (method == null) return false;
        PsiClass classOfMethod = method.getContainingClass();
        if (classOfMethod == null) return false;
        PsiManager manager = classOfMethod.getManager();
        if (!manager.areElementsEquivalent(classOfArgument, classOfMethod)) return false;
        String newText = expression.getText();
        if (newText == null) return false;
        PsiElement identifier = call.getMethodExpression().getReferenceNameElement();
        if (identifier == null) return false;
        String className = classOfMethod.getName();
        if (className == null) return false;
        holder.registerProblem(identifier,
                               InspectionGadgetsBundle.message("inspection.redundant.java.time.operation.creation.java.time.redundant.call.message",
                                                               className + ".from()"),
                               getHighlightTypeWithUnused(call),
                               RedundantCreationFix.create(newText, RedundantCreationFix.FixType.REMOVE,
                                                           InspectionGadgetsBundle.message(
                                                             "inspection.redundant.java.time.operation.creation.java.time.error.remove.fix.message",
                                                             className + ".from()")));
        return true;
      }

    };
  }

  private @NotNull ProblemHighlightType getHighlightTypeWithUnused(@NotNull PsiElement psiElement) {
    boolean result = false;
    HighlightDisplayKey key = HighlightDisplayKey.find(getShortName());
    if (key != null) {
      HighlightDisplayLevel errorLevel =
        InspectionProjectProfileManager.getInstance(psiElement.getProject()).getCurrentProfile().getErrorLevel(key, psiElement);
      result = HighlightDisplayLevel.WARNING.equals(errorLevel);
    }
    return result ? ProblemHighlightType.LIKE_UNUSED_SYMBOL : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
  }

  private static class InlineChronoEnumCallFix extends PsiUpdateModCommandQuickFix {
    private final @NotNull String myNewMethodName;
    private final int myDeletedArgumentIndex;

    InlineChronoEnumCallFix(@NotNull @NlsSafe String newMethodName, int deletedArgumentIndex) {
      myNewMethodName = newMethodName;
      myDeletedArgumentIndex = deletedArgumentIndex;
    }

    @Override
    public @NotNull String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x.call", myNewMethodName + "()");
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.redundant.java.time.operation.explicit.chrono.field.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
      if (qualifierExpression == null) {
        return;
      }
      CommentTracker ct = new CommentTracker();
      String text = ct.text(qualifierExpression) + "." + myNewMethodName;
      PsiExpression[] expressions = call.getArgumentList().getExpressions();
      StringJoiner joiner = new StringJoiner(",", "(", ")");
      for (int i = 0; i < expressions.length; i++) {
        if (i == myDeletedArgumentIndex) {
          continue;
        }
        joiner.add(ct.text(expressions[i]));
      }
      text += joiner.toString();
      ct.replaceAndRestoreComments(call, text);
    }
  }

  private static class RedundantCreationFix extends PsiUpdateModCommandQuickFix {
    private enum FixType {
      REMOVE, SIMPLIFY
    }

    @NotNull private final String myNewText;
    @NotNull private final RedundantCreationFix.FixType myFixType;
    @IntentionName @Nullable private final String myMessageError;

    private RedundantCreationFix(@NotNull String text, @NotNull RedundantCreationFix.FixType type,
                                 @IntentionName @Nullable String messageError) {
      myNewText = text;
      myFixType = type;
      myMessageError = messageError;
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, false);
      if (callExpression == null) return;
      new CommentTracker().replaceAndRestoreComments(callExpression, myNewText);
    }

    @Override
    public @NotNull String getName() {
      if (myMessageError != null) return myMessageError;
      return super.getName();
    }

    @Override
    public @NotNull String getFamilyName() {
      return switch (myFixType) {
        case SIMPLIFY -> InspectionGadgetsBundle.message("inspection.redundant.java.time.operation.creation.java.time.family.name");
        case REMOVE -> InspectionGadgetsBundle.message("inspection.redundant.java.time.operation.creation.java.time.remove.family.name");
      };
    }

    private static @NotNull ModCommandQuickFix create(@NotNull String newText, @NotNull RedundantCreationFix.FixType type,
                                                      @IntentionName @Nullable String messageError) {
      return new RedundantCreationFix(newText, type, messageError);
    }
  }

  private static class InlineCompareToTimeCallFix extends PsiUpdateModCommandQuickFix {
    private final @NotNull RelationType myRelationType;
    private final @NotNull String myArgumentType;

    InlineCompareToTimeCallFix(@NotNull RelationType relationType, @NotNull @NlsSafe String argumentType) {
      myRelationType = relationType;
      myArgumentType = argumentType;
    }

    @Override
    public @NotNull String getName() {
      String method = getMethodName();
      return CommonQuickFixBundle.message("fix.replace.with.x.call", method);
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.redundant.java.time.operation.compare.java.time.family.name");
    }

    private @NotNull String getMethodName() {
      return switch (myRelationType) {
        case EQ, NE -> myArgumentType.equals(JAVA_TIME_LOCAL_TIME) ? "equals" : "isEqual";
        case GT, LE -> "isAfter";
        case LT, GE -> "isBefore";
        default -> throw new UnsupportedOperationException(myRelationType.toString());
      };
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression first = call.getMethodExpression().getQualifierExpression();
      if (first == null) {
        return;
      }
      PsiExpression second = call.getArgumentList().getExpressions()[0];
      CommentTracker ct = new CommentTracker();
      String text = ct.text(first) + "." + getMethodName() + "(" + ct.text(second) + ")";

      if (myRelationType == RelationType.NE || myRelationType == RelationType.LE || myRelationType == RelationType.GE) {
        text = "!" + text;
      }
      PsiBinaryExpression parent = PsiTreeUtil.getParentOfType(call, PsiBinaryExpression.class);
      if (parent == null) return;
      ct.replaceAndRestoreComments(parent, text);
    }
  }
}
