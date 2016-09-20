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
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * @author Tagir Valeev
 */
class ReplaceWithSumFix extends MigrateToStreamFix {

  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace with sum()";
  }

  @Override
  void migrate(@NotNull Project project,
               @NotNull ProblemDescriptor descriptor,
               @NotNull PsiForeachStatement foreachStatement,
               @NotNull PsiExpression iteratedValue,
               @NotNull PsiStatement body,
               @NotNull StreamApiMigrationInspection.TerminalBlock tb,
               @NotNull List<String> intermediateOps) {
    PsiAssignmentExpression assignment = tb.getSingleExpression(PsiAssignmentExpression.class);
    if (assignment == null) return;
    PsiVariable var = StreamApiMigrationInspection.extractAccumulator(assignment);
    if (var == null) return;

    PsiExpression addend = StreamApiMigrationInspection.extractAddend(assignment);
    if (addend == null) return;
    PsiType type = var.getType();
    if (!(type instanceof PsiPrimitiveType)) return;
    PsiPrimitiveType primitiveType = (PsiPrimitiveType)type;
    if (primitiveType.equalsToText("float")) return;
    String typeName;
    if (primitiveType.equalsToText("double")) {
      typeName = "Double";
    }
    else if (primitiveType.equalsToText("long")) {
      typeName = "Long";
    }
    else {
      typeName = "Int";
    }
    intermediateOps.add(".mapTo" + typeName + "(" + StreamApiMigrationInspection.createLambda(tb.getVariable(), addend) + ")");
    final StringBuilder builder = generateStream(iteratedValue, intermediateOps);
    builder.append(".sum()");
    replaceWithNumericAddition(project, foreachStatement, var, builder, typeName.toLowerCase(Locale.ENGLISH));
  }
}
