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
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Tagir Valeev
 */
class ReplaceWithFindFirstFix extends MigrateToStreamFix {
  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace with findFirst()";
  }

  @Override
  void migrate(@NotNull Project project,
               @NotNull ProblemDescriptor descriptor,
               @NotNull PsiForeachStatement foreachStatement,
               @NotNull PsiExpression iteratedValue,
               @NotNull PsiStatement body,
               @NotNull StreamApiMigrationInspection.TerminalBlock tb,
               @NotNull List<String> intermediateOps) {
    PsiStatement statement = tb.getSingleStatement();
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    intermediateOps.add("");
    if (statement instanceof PsiReturnStatement) {
      PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
      PsiExpression value = returnStatement.getReturnValue();
      if (value == null) return;
      PsiReturnStatement nextReturnStatement = StreamApiMigrationInspection.getNextReturnStatement(foreachStatement);
      if (nextReturnStatement == null) return;
      PsiExpression orElseExpression = nextReturnStatement.getReturnValue();
      if (!ExpressionUtils.isSimpleExpression(orElseExpression)) return;
      StringBuilder builder = generateStream(iteratedValue, intermediateOps).append(".findFirst()");
      if (!(value instanceof PsiReferenceExpression) || ((PsiReferenceExpression)value).resolve() != tb.getVariable()) {
        builder.append(".map(").append(tb.getVariable().getName()).append(" -> ").append(value.getText()).append(")");
      }
      builder.append(".orElse(").append(orElseExpression.getText()).append(")");
      restoreComments(foreachStatement, body);
      boolean siblings = nextReturnStatement.getParent() == foreachStatement.getParent();
      PsiElement result = foreachStatement.replace(elementFactory.createStatementFromText("return " + builder + ";", foreachStatement));
      if (siblings) {
        nextReturnStatement.delete();
      }
      simplifyAndFormat(project, result);
    }
    else {
      PsiStatement[] statements = tb.getStatements();
      if (statements.length != 2) return;
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(statements[0]);
      if (assignment == null) return;
      PsiExpression lValue = assignment.getLExpression();
      if (!(lValue instanceof PsiReferenceExpression)) return;
      PsiElement element = ((PsiReferenceExpression)lValue).resolve();
      if (!(element instanceof PsiVariable)) return;
      PsiVariable var = (PsiVariable)element;
      PsiExpression value = assignment.getRExpression();
      if (value == null) return;
      StringBuilder builder = generateStream(iteratedValue, intermediateOps).append(".findFirst()");
      if (!(value instanceof PsiReferenceExpression) || ((PsiReferenceExpression)value).resolve() != tb.getVariable()) {
        builder.append(".map(").append(tb.getVariable().getName()).append(" -> ").append(value.getText()).append(")");
      }
      restoreComments(foreachStatement, body);
      if (StreamApiMigrationInspection.isDeclarationJustBefore(var, foreachStatement)) {
        PsiExpression initializer = var.getInitializer();
        if (initializer != null) {
          PsiElement result =
            initializer.replace(elementFactory.createExpressionFromText(builder + ".orElse(" + initializer.getText() + ")", initializer));
          removeLoop(foreachStatement);
          simplifyAndFormat(project, result);
          return;
        }
      }
      PsiElement result = foreachStatement.replace(
        elementFactory.createStatementFromText(var.getName() + " = " + builder + ".orElse(" + var.getName() + ");", foreachStatement));
      simplifyAndFormat(project, result);
    }
  }
}
