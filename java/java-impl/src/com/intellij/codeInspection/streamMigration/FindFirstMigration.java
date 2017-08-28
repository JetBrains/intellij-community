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

import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils.InitializerUsageStatus;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Tagir Valeev
 */
class FindFirstMigration extends BaseStreamApiMigration {
  FindFirstMigration(boolean shouldWarn) {super(shouldWarn, "findFirst()");}

  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiElement body, @NotNull TerminalBlock tb) {
    PsiStatement statement = tb.getSingleStatement();
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    PsiStatement loopStatement = tb.getStreamSourceStatement();
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
      PsiReferenceExpression lValue = tryCast(assignment.getLExpression(), PsiReferenceExpression.class);
      if (lValue == null) return null;
      PsiVariable var = tryCast(lValue.resolve(), PsiVariable.class);
      if (var == null) return null;
      PsiExpression value = assignment.getRExpression();
      if (value == null) return null;
      restoreComments(loopStatement, body);
      InitializerUsageStatus status = ControlFlowUtils.getInitializerUsageStatus(var, loopStatement);
      PsiExpression initializer = var.getInitializer();
      PsiExpression falseExpression = lValue;
      if (status != ControlFlowUtils.InitializerUsageStatus.UNKNOWN &&
          (status != ControlFlowUtils.InitializerUsageStatus.AT_WANTED_PLACE || ExpressionUtils.isSimpleExpression(initializer))) {
        falseExpression = initializer;
      } else {
        PsiElement maybeAssignment = PsiTreeUtil.skipWhitespacesAndCommentsBackward(loopStatement);
        PsiExpression prevRValue = ExpressionUtils.getAssignmentTo(maybeAssignment, var);
        if (prevRValue != null) {
          maybeAssignment.delete();
          falseExpression = prevRValue;
        }
      }
      String replacementText = generateOptionalUnwrap(tb, value, falseExpression, var.getType());
      return replaceInitializer(loopStatement, var, initializer, replacementText, status);
    }
  }

  private static String generateOptionalUnwrap(TerminalBlock tb,
                                               PsiExpression trueExpression, PsiExpression falseExpression,
                                               PsiType targetType) {
    String qualifier = tb.generate() + ".findFirst()";
    return OptionalUtil.generateOptionalUnwrap(qualifier, tb.getVariable(), trueExpression, falseExpression, targetType, false);
  }
}
