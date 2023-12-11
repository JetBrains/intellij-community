/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import static com.intellij.codeInspection.options.OptPane.number;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class OverlyLargePrimitiveArrayInitializerInspection
  extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public int m_limit = 64;

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Integer numElements = (Integer)infos[0];
    return InspectionGadgetsBundle.message(
      "large.initializer.primitive.type.array.problem.descriptor",
      numElements);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      number("m_limit", InspectionGadgetsBundle.message(
        "large.initializer.primitive.type.array.maximum.number.of.elements.option"), 1, Integer.MAX_VALUE));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OverlyLargePrimitiveArrayInitializerVisitor();
  }

  private class OverlyLargePrimitiveArrayInitializerVisitor
    extends BaseInspectionVisitor {


    @Override
    public void visitArrayInitializerExpression(
      @NotNull PsiArrayInitializerExpression expression) {
      super.visitArrayInitializerExpression(expression);
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      final PsiType componentType = type.getDeepComponentType();
      if (!(componentType instanceof PsiPrimitiveType)) {
        return;
      }
      final int numElements = calculateNumElements(expression);
      if (numElements <= m_limit) {
        return;
      }
      registerError(expression, Integer.valueOf(numElements));
    }

    private int calculateNumElements(PsiExpression expression) {
      if (expression instanceof PsiArrayInitializerExpression arrayExpression) {
        final PsiExpression[] initializers =
          arrayExpression.getInitializers();
        return Arrays.stream(initializers).mapToInt(this::calculateNumElements).sum();
      }
      return 1;
    }
  }
}