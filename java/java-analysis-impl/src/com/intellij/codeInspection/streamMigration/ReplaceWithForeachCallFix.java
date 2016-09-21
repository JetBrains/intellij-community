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
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Tagir Valeev
 */
class ReplaceWithForeachCallFix extends MigrateToStreamFix {
  private static final Logger LOG = Logger.getInstance("#" + ReplaceWithForeachCallFix.class.getName());

  private final String myForEachMethodName;

  protected ReplaceWithForeachCallFix(String forEachMethodName) {
    myForEachMethodName = forEachMethodName;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace with " + myForEachMethodName;
  }

  @Override
  void migrate(@NotNull Project project,
               @NotNull ProblemDescriptor descriptor,
               @NotNull PsiForeachStatement foreachStatement,
               @NotNull PsiExpression iteratedValue,
               @NotNull PsiStatement body,
               @NotNull StreamApiMigrationInspection.TerminalBlock tb,
               @NotNull List<String> intermediateOps) {
    restoreComments(foreachStatement, body);

    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

    StringBuilder buffer = generateStream(iteratedValue, intermediateOps);
    PsiElement block = tb.convertToElement(elementFactory);

    buffer.append(".").append(myForEachMethodName).append("(");

    final String functionalExpressionText = tb.getVariable().getName() + " -> " + wrapInBlock(block);
    PsiExpressionStatement callStatement = (PsiExpressionStatement)elementFactory
      .createStatementFromText(buffer.toString() + functionalExpressionText + ");", foreachStatement);
    callStatement = (PsiExpressionStatement)foreachStatement.replace(callStatement);

    final PsiExpressionList argumentList = ((PsiCallExpression)callStatement.getExpression()).getArgumentList();
    LOG.assertTrue(argumentList != null, callStatement.getText());
    final PsiExpression[] expressions = argumentList.getExpressions();
    LOG.assertTrue(expressions.length == 1);

    if (expressions[0] instanceof PsiFunctionalExpression &&
        ((PsiFunctionalExpression)expressions[0]).getFunctionalInterfaceType() == null) {
      callStatement =
        (PsiExpressionStatement)callStatement.replace(elementFactory.createStatementFromText(
          buffer.toString() + "(" + tb.getVariable().getText() + ") -> " + wrapInBlock(block) + ");", callStatement));
    }

    simplifyAndFormat(project, callStatement);
  }

  private static String wrapInBlock(PsiElement block) {
    if (block instanceof PsiExpressionStatement) {
      return ((PsiExpressionStatement)block).getExpression().getText();
    }
    if (block instanceof PsiCodeBlock) {
      return block.getText();
    }
    return "{" + block.getText() + "}";
  }
}
