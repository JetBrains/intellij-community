/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.portability;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public final class HardcodedLineSeparatorsInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "HardcodedLineSeparator";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("hardcoded.line.separator.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new HardcodedLineSeparatorsVisitor();
  }

  private static class HardcodedLineSeparatorsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (type == null || !TypeUtils.isJavaLangString(type) && !type.equals(PsiTypes.charType())) {
        return;
      }
      final String text = expression.getText();
      final int[] offsets = new int[text.length() + 1];
      final CharSequence result = CodeInsightUtilCore.parseStringCharacters(text, offsets);
      if (result != null) {
        for (int i = 0, max = result.length(); i < max; i++) {
          final char c = result.charAt(i);
          if (c == '\n' || c == '\r') {
            final int offset = offsets[i];
            final int length = offsets[i + 1] - offset;
            if (length > 1) {
              registerErrorAtOffset(expression, offset, length);
            }
          }
        }
      }
    }
  }
}
