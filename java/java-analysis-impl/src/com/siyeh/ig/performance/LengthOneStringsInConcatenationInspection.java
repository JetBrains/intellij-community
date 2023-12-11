/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers, Fabrice TIERCELIN
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiLiteralUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public final class LengthOneStringsInConcatenationInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  public String getID() {
    return "SingleCharacterStringConcatenation";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final String string = (String)infos[0];
    final String escapedString = StringUtil.escapeStringCharacters(string);
    return InspectionGadgetsBundle.message("expression.can.be.replaced.problem.descriptor", escapedString);
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new ReplaceStringsWithCharsFix();
  }

  private static class ReplaceStringsWithCharsFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiLiteralExpression expression = (PsiLiteralExpression)startElement;
      if (ExpressionUtils.isConversionToStringNecessary(expression, false)) {
        return;
      }
      final String charLiteral = PsiLiteralUtil.charLiteralString(expression);
      if (charLiteral == null) {
        return;
      }
      PsiReplacementUtil.replaceExpression(expression, charLiteral);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LengthOneStringsInConcatenationVisitor();
  }

  private static class LengthOneStringsInConcatenationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (!TypeUtils.isJavaLangString(type)) {
        return;
      }
      final String value = (String)expression.getValue();
      if (value == null || value.length() != 1) {
        return;
      }
      if (ExpressionUtils.isConversionToStringNecessary(expression, false)) {
        return;
      }
      registerError(expression, value);
    }
  }
}