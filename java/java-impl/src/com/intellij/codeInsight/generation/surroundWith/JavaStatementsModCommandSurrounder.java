// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.lang.surroundWith.ModCommandSurrounder;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class JavaStatementsModCommandSurrounder extends ModCommandSurrounder {
  @Override
  public boolean isApplicable(PsiElement @NotNull [] elements) {
    return ContainerUtil.find(elements, PsiSwitchLabelStatementBase.class::isInstance) == null;
  }

  @Override
  public final @NotNull ModCommand surroundElements(@NotNull ActionContext context, @NotNull PsiElement @NotNull [] elements) {
    PsiElement container = elements[0].getParent();
    if (container == null) return ModCommand.nop();
    return ModCommand.psiUpdate(context, updater -> surroundStatements(
      context, updater.getWritable(container),
      ContainerUtil.map2Array(elements, PsiElement.EMPTY_ARRAY, updater::getWritable),
      updater));
  }

  protected abstract void surroundStatements(@NotNull ActionContext context,
                                             @NotNull PsiElement container,
                                             @NotNull PsiElement @NotNull [] statements,
                                             @NotNull ModPsiUpdater updater);

  protected static @NotNull PsiStatement addAfter(final PsiStatement statement, final PsiElement container, final PsiElement[] statements) {
    if (container instanceof PsiSwitchLabeledRuleStatement && !(statement instanceof PsiBlockStatement)) {
      Project project = container.getProject();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

      PsiBlockStatement blockStatement = (PsiBlockStatement)factory.createStatementFromText("{\n}", null);
      blockStatement = (PsiBlockStatement)codeStyleManager.reformat(blockStatement);
      blockStatement = (PsiBlockStatement)container.addAfter(blockStatement, statements[statements.length - 1]);

      return (PsiStatement)blockStatement.getCodeBlock().add(statement);
    }
    return (PsiStatement)container.addAfter(statement, statements[statements.length - 1]);
  }

  protected static void addRangeWithinContainer(PsiCodeBlock codeBlock, PsiElement container, PsiElement[] statements, boolean canBreak) {
    if (container instanceof PsiSwitchLabeledRuleStatement && statements.length == 1) {
      PsiElement statement = statements[0];
      if (statement instanceof PsiExpressionStatement && canBreak) {
        addYield(codeBlock, (PsiExpressionStatement)statement);
        return;
      }
      if (statement instanceof PsiBlockStatement) {
        addCodeBlockContents(codeBlock, (PsiBlockStatement)statement);
        return;
      }
    }

    codeBlock.addRange(statements[0], statements[statements.length - 1]);
  }

  private static void addYield(PsiCodeBlock codeBlock, PsiExpressionStatement statement) {
    PsiExpressionStatement wrappedStatement = (PsiExpressionStatement)codeBlock.add(statement);
    CommentTracker tracker = new CommentTracker();
    tracker.markUnchanged(wrappedStatement.getExpression());

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(codeBlock.getProject());
    PsiYieldStatement yieldStatement = (PsiYieldStatement)factory.createStatementFromText("yield 0;", statement);
    yieldStatement = (PsiYieldStatement)tracker.replaceAndRestoreComments(wrappedStatement, yieldStatement);

    PsiExpression yieldExpression = yieldStatement.getExpression();
    assert yieldExpression != null : DebugUtil.psiToString(yieldStatement, true);
    yieldExpression.replace(statement.getExpression());
  }

  protected static void addCodeBlockContents(PsiCodeBlock codeBlock, PsiBlockStatement statement) {
    // could just replace one code block with the other, but then we lose some comments and formatting
    PsiBlockStatement tempStatement = (PsiBlockStatement)codeBlock.add(statement);
    PsiCodeBlock tempBlock = tempStatement.getCodeBlock();
    PsiJavaToken lBrace = tempBlock.getLBrace();
    PsiJavaToken rBrace = tempBlock.getRBrace();
    if (lBrace != null && rBrace != null) {
      CommentTracker tracker = new CommentTracker();
      for (PsiElement element = lBrace.getNextSibling(); element != null && element != rBrace; element = element.getNextSibling()) {
        tracker.markUnchanged(element);
        codeBlock.addBefore(element, tempStatement);
      }
      tracker.deleteAndRestoreComments(tempStatement);
    }
  }
}