/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.Operation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Tagir Valeev
 */
class ReplaceWithCountFix extends MigrateToStreamFix {

  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace with count()";
  }

  @Override
  void migrate(@NotNull Project project,
               @NotNull ProblemDescriptor descriptor,
               @NotNull PsiForeachStatement foreachStatement,
               @NotNull PsiExpression iteratedValue,
               @NotNull PsiStatement body,
               @NotNull StreamApiMigrationInspection.TerminalBlock tb,
               @NotNull List<Operation> operations) {
    PsiExpression operand = StreamApiMigrationInspection.extractIncrementedLValue(tb.getSingleExpression(PsiExpression.class));
    if (!(operand instanceof PsiReferenceExpression)) return;
    PsiElement element = ((PsiReferenceExpression)operand).resolve();
    if (!(element instanceof PsiLocalVariable)) return;
    PsiLocalVariable var = (PsiLocalVariable)element;
    final StringBuilder builder = generateStream(iteratedValue, operations);
    builder.append(".count()");
    replaceWithNumericAddition(project, foreachStatement, var, builder, PsiType.LONG);
  }
}
