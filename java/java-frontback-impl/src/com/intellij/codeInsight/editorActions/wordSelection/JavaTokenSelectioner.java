// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_CODE_BLOCK;
import static com.intellij.psi.impl.source.BasicJavaElementType.CLASS_SET;

public final class JavaTokenSelectioner extends AbstractBasicBackBasicSelectioner {

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    return BasicJavaAstTreeUtil.isJavaToken(node) && !BasicJavaAstTreeUtil.isKeyword(node) &&
           !BasicJavaAstTreeUtil.is(node.getTreeParent(), BASIC_CODE_BLOCK) &&
           !BasicJavaAstTreeUtil.is(node.getTreeParent(), CLASS_SET);
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    ASTNode node = BasicJavaAstTreeUtil.toNode(e);
    if (node == null) {
      return null;
    }

    if (!BasicJavaAstTreeUtil.is(node, JavaTokenType.SEMICOLON) &&
        !BasicJavaAstTreeUtil.is(node, JavaTokenType.LPARENTH)) {
      return super.select(e, editorText, cursorOffset, editor);
    }
    else {
      return null;
    }
  }
}
