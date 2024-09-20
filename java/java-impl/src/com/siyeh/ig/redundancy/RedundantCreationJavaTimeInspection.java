// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.CommonClassNames.*;


public final class RedundantCreationJavaTimeInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
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

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @SuppressWarnings("UnnecessaryReturnStatement")
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        if (fixRedundantFrom(call)) return;
        if (fixRedundantOfLocalDate(call)) return;
        if (fixRedundantOfLocalTime(call)) return;
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
        if(JAVA_TIME_LOCAL_TIME.equals(variableClass.getQualifiedName())) {
          holder.registerProblem(referenceNameElement,
                                 InspectionGadgetsBundle.message("inspection.redundant.creation.java.time.error.message", "LocalTime"),
                                 RedundantCreationFix.create(identifier.getText()));
          return true;
        }
        PsiMethod[] localTimes = variableClass.findMethodsByName(TO_LOCAL_TIME, false);
        if (localTimes.length != 1) return false;

        String newText = identifier.getText() + "." + TO_LOCAL_TIME + "()";

        holder.registerProblem(referenceNameElement,
                               InspectionGadgetsBundle.message("inspection.redundant.creation.java.time.error.message", "LocalTime"),
                               RedundantCreationFix.create(newText));
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
              referenceExpression.resolve() instanceof PsiVariable firstVariable)) return false;
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
                                 InspectionGadgetsBundle.message("inspection.redundant.creation.java.time.error.message", "LocalDate"),
                                 RedundantCreationFix.create(identifier.getText()));
          return true;
        }

        PsiMethod[] localDates = variableClass.findMethodsByName(TO_LOCAL_DATE, false);
        if (localDates.length != 1) return false;
        String newText = identifier.getText() + "." + TO_LOCAL_DATE + "()";

        holder.registerProblem(referenceNameElement,
                               InspectionGadgetsBundle.message("inspection.redundant.creation.java.time.error.message", "LocalDate"),
                               RedundantCreationFix.create(newText));
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
                               InspectionGadgetsBundle.message("inspection.redundant.creation.java.time.error.message", className),
                               RedundantCreationFix.create(newText));
        return true;
      }
    };
  }

  private static class RedundantCreationFix extends PsiUpdateModCommandQuickFix {
    @NotNull private final String myNewText;

    private RedundantCreationFix(@NotNull String text) { myNewText = text; }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, false);
      if (callExpression == null) return;
      new CommentTracker().replaceAndRestoreComments(callExpression, myNewText);
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.redundant.creation.java.time.family.name");
    }

    private static @NotNull ModCommandQuickFix create(@NotNull String newText) {
      return new RedundantCreationFix(newText);
    }
  }
}