/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public final class OctalAndDecimalIntegersMixedInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "OctalAndDecimalIntegersInSameArray";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("octal.and.decimal.integers.in.same.array.problem.descriptor");
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    return new LocalQuickFix[]{
      new ConvertOctalLiteralsToDecimalsFix(),
      new RemoveLeadingZeroesFix()
    };
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OctalAndDecimalIntegersMixedVisitor();
  }

  private static class OctalAndDecimalIntegersMixedVisitor extends BaseInspectionVisitor {

    @Override
    public void visitArrayInitializerExpression(@NotNull PsiArrayInitializerExpression expression) {
      super.visitArrayInitializerExpression(expression);
      final PsiExpression[] initializers = expression.getInitializers();
      boolean hasDecimalLiteral = false;
      boolean hasOctalLiteral = false;
      for (PsiExpression initializer : initializers) {
        initializer = PsiUtil.skipParenthesizedExprDown(initializer);
        if (initializer instanceof PsiLiteralExpression literal) {
          if (isDecimalLiteral(literal)) {
            hasDecimalLiteral = true;
          }
          if (ExpressionUtils.isOctalLiteral(literal)) {
            hasOctalLiteral = true;
          }
        }
      }
      if (hasOctalLiteral && hasDecimalLiteral) {
        registerError(expression);
      }
    }

    private static boolean isDecimalLiteral(PsiLiteralExpression literal) {
      final PsiType type = literal.getType();
      if (!PsiTypes.intType().equals(type) && !PsiTypes.longType().equals(type)) {
        return false;
      }
      final String text = literal.getText();
      return text.charAt(0) != '0';
    }
  }
}