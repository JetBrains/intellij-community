
/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class JavaStatementsSurrounder implements Surrounder {
  @Override
  public boolean isApplicable(@NotNull PsiElement[] elements) {
    return ContainerUtil.find(elements, PsiSwitchLabelStatementBase.class::isInstance) == null;
  }

  @Override
  @Nullable public TextRange surroundElements(@NotNull Project project,
                                              @NotNull Editor editor,
                                              @NotNull PsiElement[] elements) throws IncorrectOperationException {
    PsiElement container = elements[0].getParent();
    if (container == null) return null;
    return surroundStatements (project, editor, container, elements);
  }

 @Nullable protected abstract TextRange surroundStatements(final Project project, final Editor editor, final PsiElement container, final PsiElement[] statements) throws IncorrectOperationException;

  @NotNull
  protected PsiStatement addAfter(final PsiStatement statement, final PsiElement container, final PsiElement[] statements) {
    if (container instanceof PsiSwitchLabeledRuleStatement && !(statement instanceof PsiBlockStatement)) {
      Project project = container.getProject();
      PsiManager manager = PsiManager.getInstance(project);
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
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
        addBreakWithValue(codeBlock, (PsiExpressionStatement)statement);
        return;
      }
      if (statement instanceof PsiBlockStatement) {
        addCodeBlockContents(codeBlock, (PsiBlockStatement)statement);
        return;
      }
    }

    codeBlock.addRange(statements[0], statements[statements.length - 1]);
  }

  private static void addBreakWithValue(PsiCodeBlock codeBlock, PsiExpressionStatement statement) {
    PsiExpressionStatement wrappedStatement = (PsiExpressionStatement)codeBlock.add(statement);
    CommentTracker tracker = new CommentTracker();
    tracker.markUnchanged(wrappedStatement.getExpression());

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(codeBlock.getProject());
    PsiBreakStatement breakStatement = (PsiBreakStatement)factory.createStatementFromText("break 0;", null);
    breakStatement = (PsiBreakStatement)tracker.replaceAndRestoreComments(wrappedStatement, breakStatement);

    PsiExpression breakExpression = breakStatement.getExpression();
    assert breakExpression != null : "breakExpression";
    breakExpression.replace(statement.getExpression());
  }

  protected static void addCodeBlockContents(PsiCodeBlock codeBlock, PsiBlockStatement statement) {
    // could just replace one code block with the other but then we lose some comments and formatting
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