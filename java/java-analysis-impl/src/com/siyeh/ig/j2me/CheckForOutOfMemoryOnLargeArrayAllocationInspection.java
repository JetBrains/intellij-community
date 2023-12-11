/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.j2me;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.number;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class CheckForOutOfMemoryOnLargeArrayAllocationInspection
  extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public int m_limit = 64;

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "large.array.allocation.no.outofmemoryerror.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      number("m_limit", InspectionGadgetsBundle.message(
        "large.array.allocation.no.outofmemoryerror.maximum.number.of.elements.option"), 1, Integer.MAX_VALUE - 1));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CheckForOutOfMemoryOnLargeArrayAllocationVisitor();
  }

  private class CheckForOutOfMemoryOnLargeArrayAllocationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiType type = expression.getType();
      if (!(type instanceof PsiArrayType)) {
        return;
      }
      int size = 1;
      final PsiExpression[] dimensions = expression.getArrayDimensions();
      for (final PsiExpression dimension : dimensions) {
        final Integer intValue =
          (Integer)ConstantExpressionUtil.computeCastTo(
            dimension, PsiTypes.intType());
        if (intValue != null) {
          size *= intValue.intValue();
        }
      }
      if (size <= m_limit) {
        return;
      }
      if (outOfMemoryExceptionCaught(expression)) {
        return;
      }
      registerNewExpressionError(expression);
    }

    private boolean outOfMemoryExceptionCaught(PsiElement element) {
      PsiElement currentElement = element;
      while (true) {
        final PsiTryStatement containingTryStatement =
          PsiTreeUtil.getParentOfType(currentElement,
                                      PsiTryStatement.class);
        if (containingTryStatement == null) {
          return false;
        }
        if (catchesOutOfMemoryException(containingTryStatement)) {
          return true;
        }
        currentElement = containingTryStatement;
      }
    }

    private static boolean catchesOutOfMemoryException(PsiTryStatement statement) {
      final PsiCatchSection[] sections = statement.getCatchSections();
      for (final PsiCatchSection section : sections) {
        final PsiType catchType = section.getCatchType();
        if (catchType != null) {
          final String typeText = catchType.getCanonicalText();
          if ("java.lang.OutOfMemoryError".equals(typeText)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}