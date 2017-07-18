/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.streamMigration;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;


/**
 * Created by Roman Ivanov.
 */
public class MultiplyMigration extends BaseStreamApiMigration {
  //TODO maybe make base class for sum and multiply?
  protected MultiplyMigration(boolean shouldWarn) {
    super(shouldWarn, "reduce()");
  }

  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiStatement body, @NotNull TerminalBlock tb) {
    PsiAssignmentExpression assignment = tb.getSingleExpression(PsiAssignmentExpression.class);
    if (assignment == null) return null;
    PsiVariable var = StreamApiMigrationInspection.extractMultiplyAccumulator(assignment);
    if (var == null) return null;
    PsiExpression multiplier = StreamApiMigrationInspection.extractMultiplier(assignment);
    if (multiplier == null) return null;
    PsiType type = var.getType();
    if (!(type instanceof PsiPrimitiveType) || type.equals(PsiType.FLOAT)) return null;
    if (!type.equals(PsiType.DOUBLE) && !type.equals(PsiType.LONG)) {
      type = PsiType.INT;
    }
    PsiType multiplierType = multiplier.getType();
    if (multiplierType != null && !TypeConversionUtil.isAssignable(type, multiplierType)) {
      multiplier = JavaPsiFacade.getElementFactory(project).createExpressionFromText(
        "(" + type.getCanonicalText() + ")" + ParenthesesUtils.getText(multiplier, ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE), multiplier);
    }
    JavaCodeStyleManager javaStyle = JavaCodeStyleManager.getInstance(project);
    String leftOperand = javaStyle.suggestUniqueVariableName("a", body, true);
    String rightOperand = javaStyle.suggestUniqueVariableName("b", body, true);
    String stream = tb.add(new StreamApiMigrationInspection.MapOp(multiplier, tb.getVariable(), type)).generate() +
                    String.format(".reduce(1, (%s, %s) -> %s * %s)", leftOperand, rightOperand, leftOperand, rightOperand);
    return replaceWithNumericMultiplication(tb.getMainLoop(), var, stream, type);
  }
}
