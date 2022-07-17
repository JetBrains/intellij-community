// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.numeric;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.JavaBundle;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
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
  public @NotNull PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel7OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitLiteralExpression(@NotNull final PsiLiteralExpression literalExpression) {
        final PsiType type = literalExpression.getType();
        if (!PsiType.INT.equals(type) && !PsiType.LONG.equals(type) &&
            !PsiType.FLOAT.equals(type) && !PsiType.DOUBLE.equals(type)) return;

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
