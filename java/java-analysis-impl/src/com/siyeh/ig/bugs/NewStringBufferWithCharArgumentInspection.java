// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NewStringBufferWithCharArgumentInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "new.string.buffer.with.char.argument.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiExpression argument = (PsiExpression)infos[0];
    if (!(argument instanceof PsiLiteralExpression)) {
      return null;
    }
    return new NewStringBufferWithCharArgumentFix();
  }

  private static class NewStringBufferWithCharArgumentFix
    extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "new.string.buffer.with.char.argument.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiNewExpression newExpression =
        (PsiNewExpression)element.getParent();
      final PsiExpressionList argumentList =
        newExpression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      final String text = argument.getText();
      final String newArgument = '"' + StringUtil.escapeStringCharacters(StringUtil.stripQuotesAroundValue(text)) + '"';
      PsiReplacementUtil.replaceExpression(argument, newArgument);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringBufferWithCharArgumentVisitor();
  }

  private static class StringBufferWithCharArgumentVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      final PsiType type = argument.getType();
      if (!PsiTypes.charType().equals(type)) {
        return;
      }
      final PsiMethod constructor = expression.resolveConstructor();
      if (constructor == null) {
        return;
      }
      final PsiClass aClass = constructor.getContainingClass();
      if (!InheritanceUtil.isInheritor(aClass,
                                       CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER)) {
        return;
      }
      registerNewExpressionError(expression, argument);
    }
  }
}