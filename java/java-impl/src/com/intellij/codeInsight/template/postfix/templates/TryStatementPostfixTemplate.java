// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.generation.surroundWith.JavaWithTryCatchSurrounder;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

public class TryStatementPostfixTemplate extends PostfixTemplate implements DumbAware {

  protected TryStatementPostfixTemplate() {
    super("try", "try { exp } catch(Exception e)");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    if (JavaPostfixTemplatesUtils.isInExpressionFile(context)) return false;

    PsiStatement statementParent = PsiTreeUtil.getNonStrictParentOfType(context, PsiStatement.class);
    if (statementParent == null ||
        newOffset != statementParent.getTextRange().getEndOffset()) {
      return false;
    }

    if (statementParent instanceof PsiDeclarationStatement) return true;

    if (statementParent instanceof PsiExpressionStatement statement) {
      PsiExpression expression = statement.getExpression();
      return DumbService.getInstance(context.getProject()).computeWithAlternativeResolveEnabled(expression::getType) != null;
    }

    return false;
  }

  @Override
  public PostfixModExpander createModExpander() {
    return (ActionContext actionContext, PostfixTemplateProvider provider, TextRange keyRange) ->
      ModCommand.psiUpdate(actionContext.withSelection(new TextRange(keyRange.getStartOffset(), keyRange.getStartOffset()))
                             .withOffset(keyRange.getStartOffset()),
                           document -> document.deleteString(actionContext.selection().getStartOffset(), actionContext.selection().getEndOffset()),
                           updater -> expandModImpl(actionContext, provider, keyRange, updater));
  }

  @Override
  public boolean isApplicableForModCommand() {
    return true;
  }

  private static void expandModImpl(@NotNull ActionContext actionContext, @NotNull PostfixTemplateProvider provider,
                                    @NotNull TextRange keyRange, @NotNull com.intellij.modcommand.ModPsiUpdater updater) {
    updater.getDocument().deleteString(PostfixLiveTemplate.positiveOffset(keyRange.getStartOffset()), actionContext.selection().getStartOffset());
    PsiDocumentManager.getInstance(actionContext.project()).commitDocument(updater.getDocument());
    PsiFile file = updater.getPsiFile();
    provider.preCheckModCommand(file, PostfixLiveTemplate.positiveOffset(keyRange.getStartOffset()));
    PsiElement context =
      CustomTemplateCallback.getContext(file, PostfixLiveTemplate.positiveOffset(keyRange.getStartOffset() - 1));
    PsiStatement statement = PsiTreeUtil.getNonStrictParentOfType(context, PsiStatement.class);
    JavaWithTryCatchSurrounder surrounder = new JavaWithTryCatchSurrounder();
    surrounder.doSurround(actionContext, statement, updater);
    updater.select(new TextRange(keyRange.getStartOffset(), keyRange.getStartOffset()));
    PsiElement element = file.findElementAt(updater.getCaretOffset());
    PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class);
    assert tryStatement != null;
    PsiCodeBlock block = tryStatement.getTryBlock();
    assert block != null;
    PsiStatement statementInTry = ArrayUtil.getFirstElement(block.getStatements());
    if (null != statementInTry) {
      updater.moveCaretTo(statementInTry.getTextRange().getEndOffset());
    }
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiStatement statement = PsiTreeUtil.getNonStrictParentOfType(context, PsiStatement.class);
    assert statement != null;

    PsiFile file = statement.getContainingFile();
    Project project = context.getProject();

    JavaWithTryCatchSurrounder surrounder = new JavaWithTryCatchSurrounder();
    TextRange range = surrounder.surroundElements(project, editor, new PsiElement[]{statement});

    if (range == null) {
      PostfixTemplatesUtils.showErrorHint(project, editor);
      return;
    }

    editor.getSelectionModel().removeSelection();
    PsiElement element = file.findElementAt(range.getStartOffset());
    PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class);
    assert tryStatement != null;
    PsiCodeBlock block = tryStatement.getTryBlock();
    assert block != null;
    PsiStatement statementInTry = ArrayUtil.getFirstElement(block.getStatements());
    if (null != statementInTry) {
      editor.getCaretModel().moveToOffset(statementInTry.getTextRange().getEndOffset());
    }
  }
}
