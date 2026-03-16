// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.performance;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.PatternUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public final class ReplaceAllNonRegexInspection extends BaseInspection {
  private static final CallMatcher.Simple REPLACE_ALL = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "replaceAll")
    .parameterTypes(CommonClassNames.JAVA_LANG_STRING, CommonClassNames.JAVA_LANG_STRING);

  @Override
  public @NotNull String buildErrorString(Object @NotNull ... infos) {
    return JavaBundle.message("inspection.replace.all.non.regex.problem.descriptor");
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        if (PsiUtil.getLanguageLevel(expression).isLessThan(LanguageLevel.JDK_1_5)) return;
        if (!REPLACE_ALL.test(expression)) return;
        if (!isMigratableArgument(expression, 0, PatternUtil::containsMetaChar)) return;
        if (!isMigratableArgument(expression, 1, replacement -> StringUtil.containsAnyChar(replacement, "\\$"))) {
          return; // check for escapes and group references
        }
        registerMethodCallError(expression);
      }
    };
  }

  private static boolean isMigratableArgument(PsiMethodCallExpression call, int index, Condition<String> failCondition) {
    PsiExpression argument = call.getArgumentList().getExpressions()[index];
    Object constant = ExpressionUtils.computeConstantExpression(argument);
    if (!(constant instanceof String text)) return false;
    return !failCondition.value(text);
  }

  @Override
  protected LocalQuickFix buildFix(Object @NotNull ... infos) {
    return new PsiUpdateModCommandQuickFix() {
      @Override
      public @NotNull String getFamilyName() {
        return JavaBundle.message("inspection.replace.all.non.regex.quickfix");
      }

      @Override
      protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
        PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
        if (!REPLACE_ALL.test(call)) return;
        PsiElement namedElement = call.getMethodExpression().getReferenceNameElement();
        if (namedElement == null) return;
        namedElement.replace(JavaPsiFacade.getElementFactory(project).createIdentifier("replace"));
      }
    };
  }
}