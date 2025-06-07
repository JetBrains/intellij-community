// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.numeric;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.JavaBundle;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.text.LiteralFormatUtil;
import org.jetbrains.annotations.NotNull;

/**
 * This inspection adds a {@link ConvertNumericLiteralQuickFix} quickfix to insert underscores to numeric literals
 * if they don't have any. This leads to better readability.
 *
 * @see RemoveLiteralUnderscoresInspection
 */
public final class InsertLiteralUnderscoresInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isAvailable(JavaFeature.UNDERSCORES, holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitLiteralExpression(final @NotNull PsiLiteralExpression literalExpression) {
        final PsiType type = literalExpression.getType();
        if (!PsiTypes.intType().equals(type) && !PsiTypes.longType().equals(type) &&
            !PsiTypes.floatType().equals(type) && !PsiTypes.doubleType().equals(type)) return;

        final String text = literalExpression.getText();
        if (text == null || text.contains("_")) return;

        final String converted = LiteralFormatUtil.format(text, type);
        if (converted.length() == text.length()) return;

        final String displayMessage = JavaBundle.message("inspection.insert.literal.underscores.display.name");
        final String actionText = CommonQuickFixBundle.message("fix.replace.x.with.y", text, converted);
        final String familyName = JavaBundle.message("inspection.insert.literal.underscores.family.name");

        final ConvertNumericLiteralQuickFix quickFix = new ConvertNumericLiteralQuickFix(converted, actionText, familyName);

        holder.registerProblem(literalExpression, displayMessage, quickFix);
      }
    };
  }
}
