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

import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.InitializerUsageStatus;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Tagir Valeev
 */
class FindFirstMigration extends BaseStreamApiMigration {
  FindFirstMigration() {super("findFirst()");}

  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiStatement body, @NotNull TerminalBlock tb) {
    PsiStatement statement = tb.getSingleStatement();
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    PsiLoopStatement loopStatement = tb.getMainLoop();
    if (statement instanceof PsiReturnStatement) {
      PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
      PsiExpression value = returnStatement.getReturnValue();
      if (value == null) return null;
      PsiReturnStatement nextReturnStatement = StreamApiMigrationInspection.getNextReturnStatement(loopStatement);
      if (nextReturnStatement == null) return null;
      PsiExpression orElseExpression = nextReturnStatement.getReturnValue();
      if (!ExpressionUtils.isSimpleExpression(orElseExpression)) return null;
      String stream = generateOptionalUnwrap(tb, value, orElseExpression, PsiTypesUtil.getMethodReturnType(returnStatement));
      restoreComments(loopStatement, body);
      boolean sibling = nextReturnStatement.getParent() == loopStatement.getParent();
      PsiElement replacement = loopStatement.replace(elementFactory.createStatementFromText("return " + stream + ";", loopStatement));
      if(sibling || !isReachable(nextReturnStatement)) {
        nextReturnStatement.delete();
      }
      return replacement;
    }
    else {
      PsiStatement[] statements = tb.getStatements();
      if (statements.length != 2) return null;
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(statements[0]);
      if (assignment == null) {
        if(!(statements[0] instanceof PsiExpressionStatement)) return null;
        PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
        restoreComments(loopStatement, body);
        return loopStatement.replace(elementFactory.createStatementFromText(
          tb.generate() + ".findFirst().ifPresent(" + LambdaUtil.createLambda(tb.getVariable(), expression) + ");", loopStatement));
      }
      PsiExpression lValue = assignment.getLExpression();
      if (!(lValue instanceof PsiReferenceExpression)) return null;
      PsiElement element = ((PsiReferenceExpression)lValue).resolve();
      if (!(element instanceof PsiVariable)) return null;
      PsiVariable var = (PsiVariable)element;
      PsiExpression value = assignment.getRExpression();
      if (value == null) return null;
      restoreComments(loopStatement, body);
      InitializerUsageStatus status = StreamApiMigrationInspection.getInitializerUsageStatus(var, loopStatement);
      if (status != InitializerUsageStatus.UNKNOWN) {
        PsiExpression initializer = var.getInitializer();
        if (initializer != null) {
          String replacementText = generateOptionalUnwrap(tb, value, initializer, var.getType());
          return replaceInitializer(loopStatement, var, initializer, replacementText, status);
        }
      }
      PsiElement maybeAssignment = PsiTreeUtil.skipSiblingsBackward(loopStatement, PsiWhiteSpace.class, PsiComment.class);
      PsiExpression prevRValue = ExpressionUtils.getAssignmentTo(maybeAssignment, var);
      if(prevRValue != null) {
        maybeAssignment.delete();
        return loopStatement.replace(elementFactory.createStatementFromText(
          var.getName() + " = " + generateOptionalUnwrap(tb, value, prevRValue, var.getType()) + ";", loopStatement));
      }
      return loopStatement.replace(elementFactory.createStatementFromText(
        var.getName() + " = " + generateOptionalUnwrap(tb, value, lValue, var.getType()) + ";", loopStatement));
    }
  }

  private static String generateOptionalUnwrap(TerminalBlock tb,
                                               PsiExpression trueExpression, PsiExpression falseExpression,
                                               PsiType targetType) {
    String qualifier = tb.generate() + ".findFirst()";
    return OptionalUtil.generateOptionalUnwrap(qualifier, tb.getVariable(), trueExpression, falseExpression, targetType, false);
  }
}
