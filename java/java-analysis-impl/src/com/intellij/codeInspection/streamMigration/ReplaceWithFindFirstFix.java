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
import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.InitializerUsageStatus;
import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.Operation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.EquivalenceChecker;
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
               @NotNull List<Operation> operations) {
    PsiStatement statement = tb.getSingleStatement();
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    String stream = generateStream(iteratedValue, operations).append(".findFirst()").toString();
    if (statement instanceof PsiReturnStatement) {
      PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
      PsiExpression value = returnStatement.getReturnValue();
      if (value == null) return;
      PsiReturnStatement nextReturnStatement = StreamApiMigrationInspection.getNextReturnStatement(foreachStatement);
      if (nextReturnStatement == null) return;
      PsiExpression orElseExpression = nextReturnStatement.getReturnValue();
      if (!ExpressionUtils.isSimpleExpression(orElseExpression)) return;
      stream = generateOptionalUnwrap(stream, tb, value, orElseExpression, null);
      restoreComments(foreachStatement, body);
      boolean siblings = nextReturnStatement.getParent() == foreachStatement.getParent();
      PsiElement result = foreachStatement.replace(elementFactory.createStatementFromText("return " + stream + ";", foreachStatement));
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
      restoreComments(foreachStatement, body);
      InitializerUsageStatus status = StreamApiMigrationInspection.getInitializerUsageStatus(var, foreachStatement);
      if (status != InitializerUsageStatus.UNKNOWN) {
        PsiExpression initializer = var.getInitializer();
        if (initializer != null) {
          String replacementText = generateOptionalUnwrap(stream, tb, value, initializer, var.getType());
          replaceInitializer(foreachStatement, var, initializer, replacementText, status);
          return;
        }
      }
      PsiElement result = foreachStatement.replace(elementFactory.createStatementFromText(
        var.getName() + " = " + generateOptionalUnwrap(stream, tb, value, lValue, var.getType()) + ";", foreachStatement));
      simplifyAndFormat(project, result);
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
        if(EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(falseExpression, condition.getElseExpression())) {
          return generateOptionalUnwrap(
            stream + ".filter(" + LambdaUtil.createLambda(var, condition.getCondition()) + ")", tb,
            condition.getThenExpression(), falseExpression, targetType);
        }
      }
      trueExpression =
        targetType == null ? trueExpression : ExpressionUtils.convertInitializerToNormalExpression(trueExpression, targetType);
      stream += ".map(" + LambdaUtil.createLambda(var, trueExpression) + ")";
    }
    stream += ".orElse(" + falseExpression.getText() + ")";
    return stream;
  }
}
