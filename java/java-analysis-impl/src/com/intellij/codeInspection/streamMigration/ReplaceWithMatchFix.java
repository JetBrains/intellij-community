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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.BoolUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Tagir Valeev
 */
class ReplaceWithMatchFix extends MigrateToStreamFix {
  private static final Logger LOG = Logger.getInstance("#" + ReplaceWithMatchFix.class.getName());

  private final String myMethodName;

  public ReplaceWithMatchFix(String methodName) {
    myMethodName = methodName;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace with " + myMethodName + "()";
  }

  @Override
  void migrate(@NotNull Project project,
               @NotNull ProblemDescriptor descriptor,
               @NotNull PsiForeachStatement foreachStatement,
               @NotNull PsiExpression iteratedValue,
               @NotNull PsiStatement body,
               @NotNull StreamApiMigrationInspection.TerminalBlock tb,
               @NotNull List<String> intermediateOps) {
    PsiReturnStatement returnStatement = (PsiReturnStatement)tb.getSingleStatement();
    PsiExpression value = returnStatement.getReturnValue();
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    intermediateOps.add("");
    restoreComments(foreachStatement, body);
    if (StreamApiMigrationInspection.isLiteral(value, Boolean.TRUE) || StreamApiMigrationInspection.isLiteral(value, Boolean.FALSE)) {
      boolean foundResult = (boolean)((PsiLiteralExpression)value).getValue();
      PsiReturnStatement nextReturnStatement = StreamApiMigrationInspection.getNextReturnStatement(foreachStatement);
      if (nextReturnStatement != null && StreamApiMigrationInspection.isLiteral(nextReturnStatement.getReturnValue(), !foundResult)) {
        String methodName = foundResult ? "anyMatch" : "noneMatch";
        String streamText = generateStream(iteratedValue, intermediateOps).toString();
        streamText = addTerminalOperation(streamText, methodName, foreachStatement, tb);
        boolean siblings = nextReturnStatement.getParent() == foreachStatement.getParent();
        PsiElement result =
          foreachStatement.replace(elementFactory.createStatementFromText("return " + streamText + ";", foreachStatement));
        if (siblings) {
          nextReturnStatement.delete();
        }
        simplifyAndFormat(project, result);
        return;
      }
    }
    if (!StreamApiMigrationInspection.isVariableReferenced(tb.getVariable(), value)) {
      String streamText = generateStream(iteratedValue, intermediateOps).toString();
      streamText = addTerminalOperation(streamText, "anyMatch", foreachStatement, tb);
      String replacement = "if(" + streamText + "){" + returnStatement.getText() + "}";
      PsiElement result = foreachStatement.replace(elementFactory.createStatementFromText(replacement, foreachStatement));
      simplifyAndFormat(project, result);
    }
  }

  private static String addTerminalOperation(String origStream, String methodName, @NotNull PsiElement contextElement,
                                             @NotNull StreamApiMigrationInspection.TerminalBlock tb) {
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
