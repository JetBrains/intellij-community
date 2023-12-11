/*
 * Copyright 2003-2023 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.psi.*;
import com.intellij.util.ThreeState;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.InheritanceUtil;
import com.siyeh.ig.psiutils.InstanceOfUtils;
import org.jetbrains.annotations.NotNull;

public final class CastToIncompatibleInterfaceInspection extends BaseInspection {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Boolean isInterface = (Boolean)infos[0];
    final String className = (String)infos[1];
    return InspectionGadgetsBundle.message("casting.to.incompatible.interface.problem.descriptor", isInterface ? 1 : 2, className);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CastToIncompatibleInterfaceVisitor();
  }

  private static class CastToIncompatibleInterfaceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      final PsiTypeElement castTypeElement = expression.getCastType();
      if (castTypeElement == null) {
        return;
      }
      final PsiType castType = castTypeElement.getType();
      if (!(castType instanceof PsiClassType castClassType)) {
        return;
      }

      final PsiExpression operand = expression.getOperand();
      if (operand == null) {
        return;
      }
      final PsiType operandType = operand.getType();
      if (!(operandType instanceof PsiClassType operandClassType)) {
        return;
      }
      if (!castClassType.isConvertibleFrom(operandClassType)) {
        // don't warn on red code
        return;
      }

      final PsiClass castClass = castClassType.resolve();
      if (castClass == null) {
        return;
      }
      final PsiClass operandClass = operandClassType.resolve();
      if (operandClass == null) {
        return;
      }
      String operandClassName = operandClass.getName();
      if (operandClassName == null) {
        return;
      }
      ThreeState hasMutualSubclass = InheritanceUtil.existsMutualSubclass(operandClass, castClass, isOnTheFly());
      if (hasMutualSubclass == ThreeState.YES) return;
      if (InstanceOfUtils.findCorrespondingInstanceOf(expression) != null) return;
      PsiType psiType = TypeConstraint.fromDfType(CommonDataflow.getDfType(operand)).getPsiType(operandClass.getProject());
      if (psiType != null && castClassType.isAssignableFrom(psiType)) return;
      if (hasMutualSubclass == ThreeState.UNSURE) {
        registerPossibleProblem(castTypeElement);
      } else {
        registerError(castTypeElement, castClass.isInterface(), operandClassName);
      }
    }
  }
}