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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.*;
import com.siyeh.ig.psiutils.ControlFlowUtils.InitializerUsageStatus;
import org.jetbrains.annotations.NotNull;

class MatchMigration extends BaseStreamApiMigration {
  private static final Logger LOG = Logger.getInstance(MatchMigration.class);

  MatchMigration(boolean shouldWarn, String methodName) {
    super(shouldWarn, methodName+"()");
  }

  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiElement body, @NotNull TerminalBlock tb) {
    PsiStatement sourceStatement = tb.getStreamSourceStatement();
    CommentTracker ct = new CommentTracker();
    if(tb.getSingleStatement() instanceof PsiReturnStatement) {
      PsiReturnStatement returnStatement = (PsiReturnStatement)tb.getSingleStatement();
      PsiExpression value = returnStatement.getReturnValue();
      if (ExpressionUtils.isLiteral(value, Boolean.TRUE) || ExpressionUtils.isLiteral(value, Boolean.FALSE)) {
        boolean foundResult = (boolean)((PsiLiteralExpression)value).getValue();
        PsiReturnStatement nextReturnStatement = ControlFlowUtils.getNextReturnStatement(sourceStatement);
        if (nextReturnStatement != null) {
          PsiExpression returnValue = nextReturnStatement.getReturnValue();
          if(returnValue == null) return null;
          String methodName = foundResult ? "anyMatch" : "noneMatch";
          String streamText = addTerminalOperation(ct, methodName, sourceStatement, tb);
          if (nextReturnStatement.getParent() == sourceStatement.getParent()) {
            if(!ExpressionUtils.isLiteral(returnValue, !foundResult)) {
              streamText += (foundResult ? "||" : "&&") + ct.text(returnValue, ParenthesesUtils.BINARY_OR_PRECEDENCE);
            }
            removeLoop(ct, sourceStatement);
            return new CommentTracker().replaceAndRestoreComments(returnValue, streamText);
          }
          PsiElement result = ct.replaceAndRestoreComments(sourceStatement, "return " + streamText + ";");
          if(!ControlFlowUtils.isReachable(nextReturnStatement)) {
            new CommentTracker().deleteAndRestoreComments(nextReturnStatement);
          }
          return result;
        }
      }
    }
    PsiStatement[] statements = tb.getStatements();
    if (!(statements.length == 1 ||
          (sourceStatement instanceof PsiLoopStatement && statements.length == 2 &&
           ControlFlowUtils.statementBreaksLoop(statements[1], (PsiLoopStatement)sourceStatement)))) {
      return null;
    }
    String streamText = addTerminalOperation(ct, "anyMatch", sourceStatement, tb);
    PsiStatement statement = statements[0];
    PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(statement);
    if(assignment != null) {
      PsiExpression lValue = assignment.getLExpression();
      PsiExpression rValue = assignment.getRExpression();
      if ((lValue instanceof PsiReferenceExpression) && rValue != null) {
        PsiElement maybeVar = ((PsiReferenceExpression)lValue).resolve();
        if (maybeVar instanceof PsiVariable) {
          // Simplify single assignments like this:
          // boolean flag = false;
          // for(....) if(...) {flag = true; break;}
          PsiVariable var = (PsiVariable)maybeVar;
          PsiExpression initializer = var.getInitializer();
          InitializerUsageStatus status = ControlFlowUtils.getInitializerUsageStatus(var, sourceStatement);
          if (initializer != null && status != ControlFlowUtils.InitializerUsageStatus.UNKNOWN) {
            String replacement;
            if (ExpressionUtils.isLiteral(initializer, Boolean.FALSE) &&
                ExpressionUtils.isLiteral(rValue, Boolean.TRUE)) {
              replacement = streamText;
            }
            else if (ExpressionUtils.isLiteral(initializer, Boolean.TRUE) &&
                     ExpressionUtils.isLiteral(rValue, Boolean.FALSE)) {
              replacement = "!" + streamText;
            }
            else {
              replacement = streamText + "?" + ct.text(rValue) + ":" + ct.text(initializer);
            }
            return replaceInitializer(sourceStatement, var, initializer, replacement, status, ct);
          }
        }
      }
    }
    String replacement = "if(" + streamText + "){" + ct.text(statement) + "}";
    return ct.replaceAndRestoreComments(sourceStatement, replacement);
  }

  private static String addTerminalOperation(CommentTracker ct,
                                             String methodName,
                                             @NotNull PsiElement contextElement,
                                             @NotNull TerminalBlock tb) {
    String origStream = tb.generate(ct);
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(contextElement.getProject());
    PsiExpression stream = elementFactory.createExpressionFromText(origStream, contextElement);
    LOG.assertTrue(stream instanceof PsiMethodCallExpression);
    PsiElement nameElement = ((PsiMethodCallExpression)stream).getMethodExpression().getReferenceNameElement();
    if (nameElement != null && nameElement.getText().equals("filter")) {
      if (methodName.equals("noneMatch")) {
        // Try to reduce noneMatch(x -> !(condition)) to allMatch(x -> condition)
        PsiExpression[] expressions = ((PsiMethodCallExpression)stream).getArgumentList().getExpressions();
        if (expressions.length == 1 && expressions[0] instanceof PsiLambdaExpression) {
          PsiLambdaExpression lambda = (PsiLambdaExpression)expressions[0];
          PsiElement lambdaBody = lambda.getBody();
          if (lambdaBody instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)lambdaBody)) {
            PsiExpression negated = BoolUtils.getNegated((PsiExpression)lambdaBody);
            LOG.assertTrue(negated != null, lambdaBody.getText());
            lambdaBody.replace(negated);
            methodName = "allMatch";
          }
        }
      }
      nameElement.replace(elementFactory.createIdentifier(methodName));
      return stream.getText();
    }
    return origStream + "." + methodName + "(" + tb.getVariable().getName() + " -> true)";
  }
}
