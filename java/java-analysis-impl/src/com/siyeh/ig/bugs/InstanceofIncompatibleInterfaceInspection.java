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

import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ThreeState;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.InheritanceUtil;
import org.jetbrains.annotations.NotNull;

public final class InstanceofIncompatibleInterfaceInspection extends BaseInspection {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Boolean isInterface = (Boolean)infos[0];
    final String aClass = (String)infos[1];
    return InspectionGadgetsBundle.message("instanceof.with.incompatible.interface.problem.descriptor", isInterface ? 1 : 2, aClass);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InstanceofIncompatibleInterfaceVisitor();
  }

  private static class InstanceofIncompatibleInterfaceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
      super.visitInstanceOfExpression(expression);
      final PsiTypeElement checkTypeElement = expression.getCheckType();
      if (checkTypeElement == null) {
        return;
      }
      final PsiType checkType = checkTypeElement.getType();
      if (!(checkType instanceof PsiClassType checkClassType)) {
        return;
      }
      final PsiExpression operand = expression.getOperand();
      final PsiType operandType = operand.getType();
      if (!(operandType instanceof PsiClassType operandClassType)) {
        return;
      }
      if (DfaPsiUtil.isAssertionEffectively(expression, false)) {
        return;
      }
      if (!TypeConversionUtil.areTypesConvertible(operandClassType, checkType)) {
        return; // don't warn on uncompilable code
      }
      final PsiClass checkClass = checkClassType.resolve();
      if (checkClass == null) {
        return;
      }
      final PsiClass operandClass = operandClassType.resolve();
      if (operandClass == null) {
        return;
      }
      final String operandClassName = operandClass.getName();
      if (operandClassName == null) {
        return;
      }
      ThreeState hasMutualSubclass = InheritanceUtil.existsMutualSubclass(operandClass, checkClass, isOnTheFly());
      if (hasMutualSubclass == ThreeState.NO) {
        registerError(checkTypeElement, checkClass.isInterface(), operandClassName);
      }
      else if (hasMutualSubclass == ThreeState.UNSURE) {
        registerPossibleProblem(checkTypeElement);
      }
    }
  }
}