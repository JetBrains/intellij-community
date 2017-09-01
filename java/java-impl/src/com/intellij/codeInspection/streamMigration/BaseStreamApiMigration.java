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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Tagir Valeev
 */
abstract class BaseStreamApiMigration {
  private boolean myShouldWarn;
  private final String myReplacement;

  protected BaseStreamApiMigration(boolean shouldWarn, String replacement) {
    myShouldWarn = shouldWarn;
    myReplacement = replacement;
  }

  public String getReplacement() {
    return myReplacement;
  }

  abstract PsiElement migrate(@NotNull Project project, @NotNull PsiElement body, @NotNull TerminalBlock tb);

  public boolean isShouldWarn() {
    return myShouldWarn;
  }

  public void setShouldWarn(boolean shouldWarn) {
    myShouldWarn = shouldWarn;
  }

  static PsiElement replaceWithOperation(PsiStatement loopStatement,
                                         PsiVariable var,
                                         String streamText,
                                         PsiType expressionType,
                                         OperationReductionMigration.ReductionOperation reductionOperation) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(loopStatement.getProject());
    restoreComments(loopStatement, loopStatement instanceof PsiLoopStatement? ((PsiLoopStatement)loopStatement).getBody(): loopStatement);
    InitializerUsageStatus status = ControlFlowUtils.getInitializerUsageStatus(var, loopStatement);
    if (status != InitializerUsageStatus.UNKNOWN) {
      PsiExpression initializer = var.getInitializer();
      if (initializer != null && reductionOperation.getInitializerExpressionRestriction().test(initializer)) {
        PsiType type = var.getType();
        String replacement = (type.isAssignableFrom(expressionType) ? "" : "(" + type.getCanonicalText() + ") ") + streamText;
        return replaceInitializer(loopStatement, var, initializer, replacement, status);
      }
    }
    return loopStatement
      .replace(elementFactory.createStatementFromText(var.getName() + reductionOperation.getOperation() + "=" + streamText + ";",
                                                      loopStatement));
  }

  static PsiElement replaceInitializer(PsiStatement loopStatement,
                                       PsiVariable var,
                                       PsiExpression initializer,
                                       String replacement,
                                       InitializerUsageStatus status) {
    Project project = loopStatement.getProject();
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    if (status == ControlFlowUtils.InitializerUsageStatus.DECLARED_JUST_BEFORE) {
      initializer.replace(elementFactory.createExpressionFromText(replacement, loopStatement));
      removeLoop(loopStatement);
      return var;
    }
    else {
      if (status == ControlFlowUtils.InitializerUsageStatus.AT_WANTED_PLACE_ONLY) {
        initializer.delete();
      }
      return
        loopStatement.replace(elementFactory.createStatementFromText(var.getName() + " = " + replacement + ";", loopStatement));
    }
  }


  @Nullable
  static PsiElement replaceWithFindExtremum(@NotNull PsiStatement loopStatement,
                                            @NotNull PsiVariable extremumHolder,
                                            @NotNull String streamText,
                                            @Nullable PsiVariable keyExtremum) {
    restoreComments(loopStatement, loopStatement instanceof PsiLoopStatement? ((PsiLoopStatement)loopStatement).getBody(): loopStatement);
    if(keyExtremum != null) {
      keyExtremum.delete();
    }
    InitializerUsageStatus status = ControlFlowUtils.getInitializerUsageStatus(extremumHolder, loopStatement);
    return replaceInitializer(loopStatement, extremumHolder, extremumHolder.getInitializer(), streamText, status);
  }

  static void restoreComments(PsiStatement statement, PsiElement body) {
    if(statement instanceof PsiLoopStatement || statement instanceof PsiExpressionStatement) {
      final PsiElement parent = statement.getParent();
      for (PsiElement comment : PsiTreeUtil.findChildrenOfType(body, PsiComment.class)) {
        parent.addBefore(comment, statement);
      }
    }
  }

  static void removeLoop(@NotNull PsiStatement statement) {
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
