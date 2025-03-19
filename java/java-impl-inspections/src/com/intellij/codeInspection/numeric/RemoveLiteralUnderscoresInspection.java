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
 * This inspection adds a {@link ConvertNumericLiteralQuickFix} quickfix to remove underscores from numeric literals
 * if they have some. This inspection allows to revert the changes made by {@link InsertLiteralUnderscoresInspection}
 *
 * @see InsertLiteralUnderscoresInspection
 */
public final class RemoveLiteralUnderscoresInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
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

        if (text == null || !text.contains("_")) return;

        final String converted = LiteralFormatUtil.removeUnderscores(text);
        if (converted.length() == text.length()) return;

        final String displayMessage = JavaBundle.message("inspection.remove.literal.underscores.display.name");
        final String familyName = JavaBundle.message("inspection.remove.literal.underscores.family.name");
        final String actionText = CommonQuickFixBundle.message("fix.replace.x.with.y", text, converted);

        final ConvertNumericLiteralQuickFix quickFix = new ConvertNumericLiteralQuickFix(converted, actionText, familyName);

        holder.registerProblem(literalExpression, displayMessage, quickFix);
      }
    };
  }
}
