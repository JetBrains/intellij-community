// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_METHOD_CALL_EXPRESSION;

public final class MethodCallSelectioner implements ExtendWordSelectionHandler {

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return BasicJavaAstTreeUtil.is(BasicJavaAstTreeUtil.toNode(e), BASIC_METHOD_CALL_EXPRESSION);
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    if (node == null) {
      return null;
    }
    ASTNode methodExpression = BasicJavaAstTreeUtil.getMethodExpression(node);
    if (methodExpression == null) {
      return null;
    }
    ASTNode referenceNameElement = BasicJavaAstTreeUtil.getReferenceNameElement(methodExpression);
    if (referenceNameElement == null) {
      return null;
    }
    else {
      return Arrays.asList(new TextRange(referenceNameElement.getTextRange().getStartOffset(), e.getTextRange().getEndOffset()),
                           e.getTextRange());
    }
  }
}
