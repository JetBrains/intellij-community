// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_TRY_STATEMENT;

public final class FinallyBlockSelectioner extends AbstractBasicBackBasicSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return BasicJavaAstTreeUtil.is(BasicJavaAstTreeUtil.toNode(e), JavaTokenType.FINALLY_KEYWORD);
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = new ArrayList<>();

    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    if (node == null) {
      return null;
    }
    final ASTNode parent = node.getTreeParent();
    if (BasicJavaAstTreeUtil.is(parent, BASIC_TRY_STATEMENT)) {
      final ASTNode finallyBlock = BasicJavaAstTreeUtil.getFinallyBlock(parent);
      if (finallyBlock != null) {
        result.add(new TextRange(e.getTextRange().getStartOffset(), finallyBlock.getTextRange().getEndOffset()));
      }
    }

    return result;
  }
}
