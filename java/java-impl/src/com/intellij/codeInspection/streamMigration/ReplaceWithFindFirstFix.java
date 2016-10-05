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

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.InitializerUsageStatus;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.util.RefactoringUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

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
  PsiElement migrate(@NotNull Project project,
               @NotNull ProblemDescriptor descriptor,
               @NotNull PsiForeachStatement foreachStatement,
               @NotNull PsiExpression iteratedValue,
               @NotNull PsiStatement body,
               @NotNull StreamApiMigrationInspection.TerminalBlock tb) {
    PsiStatement statement = tb.getSingleStatement();
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    String stream = generateStream(iteratedValue, tb.getLastOperation()).append(".findFirst()").toString();
    if (statement instanceof PsiReturnStatement) {
      PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
      PsiExpression value = returnStatement.getReturnValue();
      if (value == null) return null;
      PsiReturnStatement nextReturnStatement = StreamApiMigrationInspection.getNextReturnStatement(foreachStatement);
      if (nextReturnStatement == null) return null;
      PsiExpression orElseExpression = nextReturnStatement.getReturnValue();
      if (!ExpressionUtils.isSimpleExpression(orElseExpression)) return null;
      stream = generateOptionalUnwrap(stream, tb, value, orElseExpression, null);
      restoreComments(foreachStatement, body);
      if (nextReturnStatement.getParent() == foreachStatement.getParent()) {
        nextReturnStatement.delete();
      }
      return foreachStatement.replace(elementFactory.createStatementFromText("return " + stream + ";", foreachStatement));
    }
    else {
      PsiStatement[] statements = tb.getStatements();
      if (statements.length != 2) return null;
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(statements[0]);
      if (assignment == null) {
        if(!(statements[0] instanceof PsiExpressionStatement)) return null;
        PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
        restoreComments(foreachStatement, body);
        return foreachStatement.replace(elementFactory.createStatementFromText(
          stream + ".ifPresent(" + LambdaUtil.createLambda(tb.getVariable(), expression) + ");", foreachStatement));
      }
      PsiExpression lValue = assignment.getLExpression();
      if (!(lValue instanceof PsiReferenceExpression)) return null;
      PsiElement element = ((PsiReferenceExpression)lValue).resolve();
      if (!(element instanceof PsiVariable)) return null;
      PsiVariable var = (PsiVariable)element;
      PsiExpression value = assignment.getRExpression();
      if (value == null) return null;
      restoreComments(foreachStatement, body);
      InitializerUsageStatus status = StreamApiMigrationInspection.getInitializerUsageStatus(var, foreachStatement);
      if (status != InitializerUsageStatus.UNKNOWN) {
        PsiExpression initializer = var.getInitializer();
        if (initializer != null) {
          String replacementText = generateOptionalUnwrap(stream, tb, value, initializer, var.getType());
          return replaceInitializer(foreachStatement, var, initializer, replacementText, status);
        }
      }
      return foreachStatement.replace(elementFactory.createStatementFromText(
        var.getName() + " = " + generateOptionalUnwrap(stream, tb, value, lValue, var.getType()) + ";", foreachStatement));
    }
  }

  private static String generateOptionalUnwrap(String stream, @NotNull StreamApiMigrationInspection.TerminalBlock tb,
                                               PsiExpression trueExpression, PsiExpression falseExpression, PsiType targetType) {
    PsiVariable var = tb.getVariable();
    if (!StreamApiMigrationInspection.isIdentityMapping(var, trueExpression)) {
      if(trueExpression instanceof PsiTypeCastExpression && ExpressionUtils.isNullLiteral(falseExpression)) {
        PsiTypeCastExpression castExpression = (PsiTypeCastExpression)trueExpression;
        PsiTypeElement castType = castExpression.getCastType();
        // pull cast outside to avoid the .map() step
        if(castType != null && StreamApiMigrationInspection.isIdentityMapping(var, castExpression.getOperand())) {
          return "(" + castType.getText() + ")" + stream + ".orElse(null)";
        }
      }
      if(ExpressionUtils.isLiteral(falseExpression, Boolean.FALSE) && PsiType.BOOLEAN.equals(trueExpression.getType())) {
        return stream + ".filter(" + LambdaUtil.createLambda(var, trueExpression) + ").isPresent()";
      }
      if(trueExpression instanceof PsiConditionalExpression) {
        PsiConditionalExpression condition = (PsiConditionalExpression)trueExpression;
        PsiExpression elseExpression = condition.getElseExpression();
        if(elseExpression != null && PsiEquivalenceUtil.areElementsEquivalent(falseExpression, elseExpression)) {
          return generateOptionalUnwrap(
            stream + ".filter(" + LambdaUtil.createLambda(var, condition.getCondition()) + ")", tb,
            condition.getThenExpression(), falseExpression, targetType);
        }
      }
      trueExpression =
        targetType == null ? trueExpression : RefactoringUtil.convertInitializerToNormalExpression(trueExpression, targetType);
      stream += ".map(" + LambdaUtil.createLambda(var, trueExpression) + ")";
    }
    stream += ".orElse(" + falseExpression.getText() + ")";
    return stream;
  }
}
