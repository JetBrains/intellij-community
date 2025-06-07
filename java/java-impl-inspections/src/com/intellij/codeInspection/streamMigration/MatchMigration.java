// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.streamMigration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.*;
import com.siyeh.ig.psiutils.ControlFlowUtils.InitializerUsageStatus;
import org.jetbrains.annotations.NotNull;

final class MatchMigration extends BaseStreamApiMigration {
  private static final Logger LOG = Logger.getInstance(MatchMigration.class);

  MatchMigration(boolean shouldWarn, String methodName) {
    super(shouldWarn, methodName);
  }

  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiElement body, @NotNull TerminalBlock tb) {
    PsiStatement sourceStatement = tb.getStreamSourceStatement();
    CommentTracker ct = new CommentTracker();
    if(tb.getSingleStatement() instanceof PsiReturnStatement returnStatement) {
      PsiLiteralExpression literal = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(returnStatement.getReturnValue()), 
                                                         PsiLiteralExpression.class);
      if (literal != null && literal.getValue() instanceof Boolean) {
        boolean foundResult = (Boolean)literal.getValue();
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
        if (maybeVar instanceof PsiVariable var) {
          // Simplify single assignments like this:
          // boolean flag = false;
          // for(....) if(...) {flag = true; break;}
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
    String replacement = "if(" + streamText + "){" + ct.text(statement) + "\n}";
    return ct.replaceAndRestoreComments(sourceStatement, replacement);
  }

  private static String addTerminalOperation(CommentTracker ct,
                                             String methodName,
                                             @NotNull PsiElement contextElement,
                                             @NotNull TerminalBlock tb) {
    String origStream = tb.generate(ct);
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(contextElement.getProject());
    PsiMethodCallExpression stream = (PsiMethodCallExpression)elementFactory.createExpressionFromText(origStream, contextElement);
    PsiElement nameElement = stream.getMethodExpression().getReferenceNameElement();
    if (nameElement != null && nameElement.getText().equals("filter")) {
      if (methodName.equals("noneMatch")) {
        // Try to reduce noneMatch(x -> !(condition)) to allMatch(x -> condition)
        PsiExpression[] expressions = stream.getArgumentList().getExpressions();
        if (expressions.length == 1 && expressions[0] instanceof PsiLambdaExpression lambda) {
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
