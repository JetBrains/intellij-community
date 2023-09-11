// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_ANNOTATION_ARRAY_INITIALIZER;
import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_ARRAY_INITIALIZER_EXPRESSION;

public class MissingArrayInitializerBraceFixer implements Fixer {

  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    if (!(
      BasicJavaAstTreeUtil.is(astNode, BASIC_ARRAY_INITIALIZER_EXPRESSION) ||
      BasicJavaAstTreeUtil.is(astNode, BASIC_ANNOTATION_ARRAY_INITIALIZER))) {
      return;
    }
    ASTNode child = astNode.getFirstChildNode();
    if (!child.getElementType().equals(JavaTokenType.LBRACE)) return;
    PsiElement psi = BasicJavaAstTreeUtil.toPsi(astNode);
    if (psi == null) {
      return;
    }
    if (!EnterAfterUnmatchedBraceHandler.isAfterUnmatchedLBrace(editor, child.getTextRange().getEndOffset(),
                                                                psi.getContainingFile().getFileType())) {
      return;
    }
    ASTNode anchor = BasicJavaAstTreeUtil.findChildByType(astNode, TokenType.ERROR_ELEMENT);
    if (anchor == null) {
      PsiElement last = PsiTreeUtil.getDeepestVisibleLast(psi);
      while (last != null && last.getNode().getElementType().equals(JavaTokenType.RBRACE)) {
        last = PsiTreeUtil.prevCodeLeaf(last);
      }
      if (last != null && PsiTreeUtil.isAncestor(psi, last, true)) {
        anchor = last.getNode();
      }
    }
    int endOffset = (anchor != null ? anchor : astNode).getTextRange().getEndOffset();
    editor.getDocument().insertString(endOffset, "}");
  }
}