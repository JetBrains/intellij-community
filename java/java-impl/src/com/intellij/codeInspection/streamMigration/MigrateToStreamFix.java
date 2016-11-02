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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

/**
 * @author Tagir Valeev
 */
abstract class MigrateToStreamFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return getFamilyName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element instanceof PsiLoopStatement) {
      PsiLoopStatement loopStatement = (PsiLoopStatement)element;
      StreamSource source = StreamSource.tryCreate(loopStatement);
      PsiStatement body = loopStatement.getBody();
      if(body == null || source == null) return;
      TerminalBlock tb = TerminalBlock.from(source, body);
      if (!FileModificationService.getInstance().preparePsiElementForWrite(loopStatement)) return;
      PsiElement result = migrate(project, loopStatement, body, tb);
      if(result != null) {
        simplifyAndFormat(project, result);
      }
    }
  }

  abstract PsiElement migrate(@NotNull Project project,
                              @NotNull PsiLoopStatement loopStatement,
                              @NotNull PsiStatement body,
                              @NotNull TerminalBlock tb);

  static PsiElement replaceWithNumericAddition(@NotNull Project project,
                                               PsiLoopStatement loopStatement,
                                               PsiVariable var,
                                               StringBuilder builder,
                                               PsiType expressionType) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    restoreComments(loopStatement, loopStatement.getBody());
    InitializerUsageStatus status = StreamApiMigrationInspection.getInitializerUsageStatus(var, loopStatement);
    if (status != InitializerUsageStatus.UNKNOWN) {
      PsiExpression initializer = var.getInitializer();
      if (ExpressionUtils.isZero(initializer)) {
        PsiType type = var.getType();
        String replacement = (type.equals(expressionType) ? "" : "(" + type.getCanonicalText() + ") ") + builder;
        return replaceInitializer(loopStatement, var, initializer, replacement, status);
      }
    }
    return loopStatement.replace(elementFactory.createStatementFromText(var.getName() + "+=" + builder + ";", loopStatement));
  }

  static PsiElement replaceInitializer(PsiLoopStatement loopStatement,
                                 PsiVariable var,
                                 PsiExpression initializer,
                                 String replacement,
                                 InitializerUsageStatus status) {
    Project project = loopStatement.getProject();
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    if(status == InitializerUsageStatus.DECLARED_JUST_BEFORE) {
      initializer.replace(elementFactory.createExpressionFromText(replacement, loopStatement));
      removeLoop(loopStatement);
      return var;
    } else {
      if(status == InitializerUsageStatus.AT_WANTED_PLACE_ONLY) {
        initializer.delete();
      }
      return
        loopStatement.replace(elementFactory.createStatementFromText(var.getName() + " = " + replacement + ";", loopStatement));
    }
  }

  static void simplifyAndFormat(@NotNull Project project, PsiElement result) {
    if (result == null) return;
    LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
    CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(result));
  }

  static void restoreComments(PsiLoopStatement loopStatement, PsiStatement body) {
    final PsiElement parent = loopStatement.getParent();
    for (PsiElement comment : PsiTreeUtil.findChildrenOfType(body, PsiComment.class)) {
      parent.addBefore(comment, loopStatement);
    }
  }

  @NotNull
  static StringBuilder generateStream(@NotNull Operation lastOperation) {
    return generateStream(lastOperation, false);
  }

  @NotNull
  static StringBuilder generateStream(@NotNull Operation lastOperation, boolean noStreamForEmpty) {
    StringBuilder buffer = new StringBuilder();
    if(noStreamForEmpty && lastOperation instanceof CollectionStream) {
      return buffer.append(lastOperation.getExpression().getText());
    }
    List<String> replacements =
      StreamEx.iterate(lastOperation, Objects::nonNull, Operation::getPreviousOp).map(Operation::createReplacement).toList();
    for(ListIterator<String> it = replacements.listIterator(replacements.size()); it.hasPrevious(); ) {
      buffer.append(it.previous());
    }
    return buffer;
  }

  static String getIteratedValueText(PsiExpression iteratedValue) {
    return iteratedValue instanceof PsiCallExpression ||
           iteratedValue instanceof PsiReferenceExpression ||
           iteratedValue instanceof PsiQualifiedExpression ||
           iteratedValue instanceof PsiParenthesizedExpression ? iteratedValue.getText() : "(" + iteratedValue.getText() + ")";
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
