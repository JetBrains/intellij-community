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

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_TYPE_CAST_EXPRESSION;

public final class TypeCastSelectioner extends AbstractBasicBackBasicSelectioner {

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return BasicJavaAstTreeUtil.is(BasicJavaAstTreeUtil.toNode(e), BASIC_TYPE_CAST_EXPRESSION);
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = new ArrayList<>(expandToWholeLine(editorText, e.getTextRange(), false));

    List<ASTNode> children = BasicJavaAstTreeUtil.getChildren(BasicJavaAstTreeUtil.toNode(e));
    ASTNode lParen = null;
    ASTNode rParen = null;
    for (ASTNode child : children) {
      if (BasicJavaAstTreeUtil.is(child, JavaTokenType.LPARENTH)) lParen = child;
      if (BasicJavaAstTreeUtil.is(child, JavaTokenType.RPARENTH)) rParen = child;
    }

    if (lParen != null && rParen != null) {
      result.addAll(expandToWholeLine(editorText,
                                      new TextRange(lParen.getTextRange().getStartOffset(),
                                                    rParen.getTextRange().getEndOffset()),
                                      false));
    }

    return result;
  }
}
