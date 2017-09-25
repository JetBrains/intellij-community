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

import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.MapOp;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.streamMigration.OperationReductionMigration.SUM_OPERATION;

/**
 * @author Tagir Valeev
 */
class SumMigration extends BaseStreamApiMigration {

  SumMigration(boolean shouldWarn) {super(shouldWarn, "sum()");}

  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiElement body, @NotNull TerminalBlock tb) {
    PsiAssignmentExpression assignment = tb.getSingleExpression(PsiAssignmentExpression.class);
    if (assignment == null) return null;
    PsiVariable var = StreamApiMigrationInspection.extractSumAccumulator(assignment);
    if (var == null) return null;

    PsiExpression addend = StreamApiMigrationInspection.extractAddend(assignment);
    if (addend == null) return null;
    PsiType type = var.getType();
    if (!(type instanceof PsiPrimitiveType) || type.equals(PsiType.FLOAT)) return null;
    if (!type.equals(PsiType.DOUBLE) && !type.equals(PsiType.LONG)) {
      type = PsiType.INT;
    }
    PsiType addendType = addend.getType();
    if(addendType != null && !TypeConversionUtil.isAssignable(type, addendType)) {
      addend = JavaPsiFacade.getElementFactory(project).createExpressionFromText(
        "(" + type.getCanonicalText() + ")" + ParenthesesUtils.getText(addend, ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE), addend);
    }
    String stream = tb.add(new MapOp(addend, tb.getVariable(), type)).generate()+".sum()";
    return replaceWithOperation(tb.getStreamSourceStatement(), var, stream, type, SUM_OPERATION);
  }
}
