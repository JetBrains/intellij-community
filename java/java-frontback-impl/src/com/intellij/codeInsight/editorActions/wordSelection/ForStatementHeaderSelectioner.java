// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_FOREACH_STATEMENT;
import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_FOR_STATEMENT;

public final class ForStatementHeaderSelectioner implements ExtendWordSelectionHandler {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    return
      BasicJavaAstTreeUtil.is(node, BASIC_FOR_STATEMENT) ||
      BasicJavaAstTreeUtil.is(node, BASIC_FOREACH_STATEMENT);
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    if (node == null) {
      return null;
    }

    ASTNode lParen = BasicJavaAstTreeUtil.getLParenth(node);
    ASTNode rParen = BasicJavaAstTreeUtil.getRParenth(node);
    if (lParen == null || rParen == null) return null;
    TextRange result = new TextRange(lParen.getTextRange().getEndOffset(), rParen.getTextRange().getStartOffset());
    return result.containsOffset(cursorOffset) ? Collections.singletonList(result) : null;
  }
}
