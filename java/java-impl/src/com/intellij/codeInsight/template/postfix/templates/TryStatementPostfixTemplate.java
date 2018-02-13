// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.generation.surroundWith.JavaWithTryCatchSurrounder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

public class TryStatementPostfixTemplate extends PostfixTemplate {

  protected TryStatementPostfixTemplate() {
    super("try", "try { exp } catch(Exception e)");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    PsiStatement statementParent = PsiTreeUtil.getNonStrictParentOfType(context, PsiStatement.class);
    if (statementParent == null ||
        newOffset != statementParent.getTextRange().getEndOffset()) return false;

    if (statementParent instanceof PsiDeclarationStatement) return true;

    if (statementParent instanceof PsiExpressionStatement) {
      PsiExpression expression = ((PsiExpressionStatement)statementParent).getExpression();
      return null != expression.getType();
    }

    return false;
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
