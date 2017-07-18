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

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils.InitializerUsageStatus;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Tagir Valeev
 */
abstract class BaseStreamApiMigration {
  private final boolean myShouldWarn;
  private final String myReplacement;

  protected BaseStreamApiMigration(boolean shouldWarn, String replacement) {
    myShouldWarn = shouldWarn;
    myReplacement = replacement;
  }

  public String getReplacement() {
    return myReplacement;
  }

  abstract PsiElement migrate(@NotNull Project project, @NotNull PsiStatement body, @NotNull TerminalBlock tb);

  public boolean isShouldWarn() {
    return myShouldWarn;
  }

  static PsiElement replaceWithNumericAddition(PsiLoopStatement loopStatement,
                                               PsiVariable var,
                                               String streamText,
                                               PsiType expressionType) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(loopStatement.getProject());
    restoreComments(loopStatement, loopStatement.getBody());
    InitializerUsageStatus status = ControlFlowUtils.getInitializerUsageStatus(var, loopStatement);
    if (status != ControlFlowUtils.InitializerUsageStatus.UNKNOWN) {
      PsiExpression initializer = var.getInitializer();
      if (ExpressionUtils.isZero(initializer)) {
        PsiType type = var.getType();
        String replacement = (type.equals(expressionType) ? "" : "(" + type.getCanonicalText() + ") ") + streamText;
        return replaceInitializer(loopStatement, var, initializer, replacement, status);
      }
    }
    return loopStatement.replace(elementFactory.createStatementFromText(var.getName() + "+=" + streamText + ";", loopStatement));
  }

  static PsiElement replaceWithNumericMultiplication(PsiLoopStatement loopStatement,
                                                     PsiVariable var,
                                                     String streamText,
                                                     PsiType expressionType) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(loopStatement.getProject());
    restoreComments(loopStatement, loopStatement.getBody());
    InitializerUsageStatus status = ControlFlowUtils.getInitializerUsageStatus(var, loopStatement);

    PsiExpression initializer = var.getInitializer();

    String finalExpressionText;
    boolean parenthesisRequired = false;
    if (initializer != null) {
      if (ExpressionUtils.isOne(initializer)) {
        finalExpressionText = streamText;
      }
      else {
        parenthesisRequired = true;
        finalExpressionText = ParenthesesUtils.getText(initializer, ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE) + " * " + streamText;
      }
    }
    else {
      finalExpressionText = streamText;
    }
    if (status != ControlFlowUtils.InitializerUsageStatus.UNKNOWN) {
      PsiType type = var.getType();
      String replacement;
      if (type.equals(expressionType)) {
        replacement = finalExpressionText;
      }
      else {
        if(parenthesisRequired) {
          finalExpressionText = "(" + finalExpressionText + ")";
        }
        replacement = "(" + type.getCanonicalText() + ") " + finalExpressionText;
      }
      return replaceInitializer(loopStatement, var, initializer, replacement, status);
    }
    return loopStatement.replace(elementFactory.createStatementFromText(var.getName() + "*=" + finalExpressionText + ";", loopStatement));
  }

  static PsiElement replaceInitializer(PsiLoopStatement loopStatement,
                                       PsiVariable var,
                                       PsiExpression initializer,
                                       String replacement,
                                       InitializerUsageStatus status) {
    Project project = loopStatement.getProject();
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    if(status == ControlFlowUtils.InitializerUsageStatus.DECLARED_JUST_BEFORE) {
      initializer.replace(elementFactory.createExpressionFromText(replacement, loopStatement));
      removeLoop(loopStatement);
      return var;
    } else {
      if(status == ControlFlowUtils.InitializerUsageStatus.AT_WANTED_PLACE_ONLY) {
        initializer.delete();
      }
      return
        loopStatement.replace(elementFactory.createStatementFromText(var.getName() + " = " + replacement + ";", loopStatement));
    }
  }

  static void restoreComments(PsiLoopStatement loopStatement, PsiStatement body) {
    final PsiElement parent = loopStatement.getParent();
    for (PsiElement comment : PsiTreeUtil.findChildrenOfType(body, PsiComment.class)) {
      parent.addBefore(comment, loopStatement);
    }
  }

  static void removeLoop(@NotNull PsiLoopStatement statement) {
    PsiElement parent = statement.getParent();
    if (parent instanceof PsiLabeledStatement) {
      parent.delete();
    }
    else {
      statement.delete();
    }
  }

  static boolean isReachable(PsiReturnStatement target) {
    ControlFlow flow;
    try {
      flow = ControlFlowFactory.getInstance(target.getProject())
        .getControlFlow(target.getParent(), LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
    }
    catch (AnalysisCanceledException e) {
      return true;
    }
    return ControlFlowUtil.isInstructionReachable(flow, flow.getStartOffset(target), 0);
  }
}
