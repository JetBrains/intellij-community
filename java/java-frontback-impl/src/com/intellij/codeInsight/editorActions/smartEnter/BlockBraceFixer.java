// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_CODE_BLOCK;
import static com.intellij.psi.impl.source.BasicJavaElementType.STATEMENT_SET;

public class BlockBraceFixer implements Fixer{
  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    PsiElement psiElement = BasicJavaAstTreeUtil.toPsi(astNode);
    if (psiElement == null) {
      return;
    }
    if (BasicJavaAstTreeUtil.is(astNode, BASIC_CODE_BLOCK) && afterUnmatchedBrace(editor, psiElement.getContainingFile().getFileType())) {
      int stopOffset = astNode.getTextRange().getEndOffset();
      List<ASTNode> statements = BasicJavaAstTreeUtil.getChildren(astNode).stream().filter(node->
        BasicJavaAstTreeUtil.is(node, STATEMENT_SET)
      ).toList();
      if (!statements.isEmpty()) {
        stopOffset = statements.get(0).getTextRange().getEndOffset();
      }
      editor.getDocument().insertString(stopOffset, "}");
    }
  }

  static boolean afterUnmatchedBrace(Editor editor, FileType fileType) {
    return EnterAfterUnmatchedBraceHandler.isAfterUnmatchedLBrace(editor, editor.getCaretModel().getOffset(), fileType);
  }
}
