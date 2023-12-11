// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler;
import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.ASTNode;
import com.intellij.psi.AbstractBasicJavaFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_EXPRESSION_LIST_STATEMENT;
import static com.intellij.psi.impl.source.BasicJavaElementType.EXPRESSION_SET;

public final class JavaEnterAfterUnmatchedBraceHandler extends EnterAfterUnmatchedBraceHandler {

  protected JavaEnterAfterUnmatchedBraceHandler() {
  }

  @Override
  public boolean isApplicable(@NotNull PsiFile file, int caretOffset) {
    return file instanceof AbstractBasicJavaFile;
  }

  @Override
  protected int calculateOffsetToInsertClosingBraceInsideElement(PsiElement element) {
    if (element instanceof PsiErrorElement &&
        ((PsiErrorElement)element).getErrorDescription().equals(JavaPsiBundle.message("else.without.if"))) {
      return element.getTextRange().getStartOffset();
    }
    ASTNode node = BasicJavaAstTreeUtil.toNode(element);
    if (BasicJavaAstTreeUtil.is(node, BASIC_EXPRESSION_LIST_STATEMENT)) {
      final ASTNode list = BasicJavaAstTreeUtil.getExpressionList(node);
      if (list != null) {
        final ASTNode firstExpression = BasicJavaAstTreeUtil.findChildByType(list, EXPRESSION_SET);
        if (firstExpression != null) {
          return firstExpression.getTextRange().getEndOffset();
        }
      }
    }
    return super.calculateOffsetToInsertClosingBraceInsideElement(element);
  }
}
