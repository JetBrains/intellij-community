// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

public final class OnlyOneElementUsedInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new RedundantElementAccessVisitor(holder);
  }

  private static class RedundantElementAccessVisitor extends JavaElementVisitor {
    private static final CallMatcher LIST_GET = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_LIST, "get")
      .parameterTypes(JavaKeywords.INT);
    private static final CallMatcher LIST_CONSTRUCTOR = CallMatcher.anyOf(
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_LIST, "of"),
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_ARRAYS, "asList")
    );
    private static final CallMatcher STRING_CHAR_AT = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "charAt")
      .parameterTypes(JavaKeywords.INT);
    private final @NotNull ProblemsHolder myHolder;

    private RedundantElementAccessVisitor(@NotNull ProblemsHolder holder) { myHolder = holder; }

    @Override
    public void visitArrayAccessExpression(@NotNull PsiArrayAccessExpression expression) {
      PsiNewExpression arrayExpression =
        tryCast(PsiUtil.skipParenthesizedExprDown(expression.getArrayExpression()), PsiNewExpression.class);
      if (arrayExpression == null || !arrayExpression.isArrayCreation()) return;
      PsiArrayInitializerExpression initializer = arrayExpression.getArrayInitializer();
      if (initializer == null) return;
      Integer value = getIndex(expression.getIndexExpression());
      if (value == null) return;
      PsiExpression[] initializers = initializer.getInitializers();
      if (value >= initializers.length) return;
      myHolder.registerProblem(expression, InspectionGadgetsBundle.message("inspection.only.one.element.used.array"),
                               new InlineSingleElementAccessFix(initializers[value].getText()));
    }

    private static @Nullable Integer getIndex(PsiExpression indexExpression) {
      PsiLiteralExpression literal =
        tryCast(PsiUtil.skipParenthesizedExprDown(indexExpression), PsiLiteralExpression.class);
      if (literal == null) return null;
      Integer value = tryCast(literal.getValue(), Integer.class);
      if (value == null || value < 0) return null;
      return value;
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      if (STRING_CHAR_AT.test(call)) {
        PsiLiteralExpression qualifier =
          tryCast(PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression()), PsiLiteralExpression.class);
        if (qualifier == null) return;
        Integer index = getIndex(call.getArgumentList().getExpressions()[0]);
        if (index == null) return;
        String value = tryCast(qualifier.getValue(), String.class);
        if (value == null || index >= value.length()) return;
        myHolder.registerProblem(call, InspectionGadgetsBundle.message("inspection.only.one.element.used.string"),
                                 new InlineSingleElementAccessFix("'" + StringUtil.escapeCharCharacters(String.valueOf(value.charAt(index)))+"'"));
      }
      else if (LIST_GET.test(call)) {
        PsiMethodCallExpression qualifier =
          tryCast(PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression()), PsiMethodCallExpression.class);
        if (!LIST_CONSTRUCTOR.test(qualifier)) return;
        PsiMethod method = qualifier.resolveMethod();
        if (method == null) return;
        if (method.isVarArgs() && !MethodCallUtils.isVarArgCall(qualifier)) return;
        Integer index = getIndex(call.getArgumentList().getExpressions()[0]);
        if (index == null) return;
        PsiExpression[] expressions = qualifier.getArgumentList().getExpressions();
        if (index >= expressions.length) return;
        myHolder.registerProblem(call, InspectionGadgetsBundle.message("inspection.only.one.element.used.list"),
                                 new InlineSingleElementAccessFix(expressions[index].getText()));
      }
    }
  }

  private static class InlineSingleElementAccessFix extends PsiUpdateModCommandQuickFix {
    final String myInitializer;

    private InlineSingleElementAccessFix(String initializer) {
      myInitializer = initializer;
    }

    @Override
    public @NotNull String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myInitializer);
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.only.one.element.used.fix.family");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      new CommentTracker().replaceAndRestoreComments(element, myInitializer);
    }
  }
}
