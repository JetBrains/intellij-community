// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.numeric;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class SuspiciousLiteralUnderscoreInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("suspicious.literal.underscore.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuspiciousLiteralUnderscoreVisitor();
  }

  private static class SuspiciousLiteralUnderscoreVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (!PsiTypes.shortType().equals(type) && !PsiTypes.intType().equals(type) && !PsiTypes.longType().equals(type) &&
          !PsiTypes.floatType().equals(type) && !PsiTypes.doubleType().equals(type)) {
        return;
      }
      final String text = expression.getText();
      if (text.startsWith("0") && !text.startsWith("0.")) {
        // don't check octal, hexadecimal or binary literals
        return;
      }
      if (!text.contains("_")) {
        return;
      }
      boolean underscore = false;
      boolean group = false;
      int dot = -1;
      int digit = 0;
      final int index = StringUtil.indexOfAny(text, "fledFLED"); // suffixes and floating point exponent
      final int length = index > 0 ? index : text.length();
      for (int i = 0; i < length; i++) {
        final char c = text.charAt(i);
        if (c == '_' || c == '.') {
          if (underscore) {
            return;
          }
          underscore = true;
          if (digit != 3 && group || digit > 3) {
            registerErrorAtOffset(expression, i - digit, digit);
          }
          group = true;
          digit = 0;
          if (c == '.') {
            dot = i;
          }
        }
        else if (Character.isDigit(c)) {
          underscore = false;
          digit++;
        }
        else {
          return;
        }
      }
      if (digit == 0) {
        // literal ends with underscore (which does not compile)
        return;
      }
      if (dot > 0 ? digit > 3 : digit != 3) {
        final int offset = length - digit;
        final boolean completeFractional = offset == dot + 1;
        if (!completeFractional) {
          registerErrorAtOffset(expression, offset, digit);
        }
      }
    }
  }
}
