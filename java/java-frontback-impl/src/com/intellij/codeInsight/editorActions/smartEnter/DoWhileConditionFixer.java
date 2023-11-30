// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_BLOCK_STATEMENT;
import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_DO_WHILE_STATEMENT;

public class DoWhileConditionFixer implements Fixer {

  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    if (BasicJavaAstTreeUtil.is(astNode, BASIC_DO_WHILE_STATEMENT)) {
      final Document doc = editor.getDocument();
      ASTNode whileKeyword = BasicJavaAstTreeUtil.getWhileKeyword(astNode);
      ASTNode doWhileBody = BasicJavaAstTreeUtil.getDoWhileBody(astNode);
      if (doWhileBody == null || !(BasicJavaAstTreeUtil.is(doWhileBody, BASIC_BLOCK_STATEMENT)) && whileKeyword == null) {
        final int startOffset = astNode.getTextRange().getStartOffset();
        doc.replaceString(startOffset, startOffset + "do".length(), "do {} while()");
        return;
      }

      if (BasicJavaAstTreeUtil.getWhileCondition(astNode) == null) {
        if (whileKeyword == null) {
          final int endOffset = astNode.getTextRange().getEndOffset();
          doc.insertString(endOffset, "while()");
        }
        else if (BasicJavaAstTreeUtil.getLParenth(astNode) == null || BasicJavaAstTreeUtil.getRParenth(astNode) == null) {
          final TextRange whileRange = whileKeyword.getTextRange();
          doc.replaceString(whileRange.getStartOffset(), whileRange.getEndOffset(), "while()");
        }
        else {
          ASTNode lParenth = BasicJavaAstTreeUtil.getLParenth(astNode);
          if (lParenth != null) {
            processor.registerUnresolvedError(lParenth.getTextRange().getEndOffset());
          }
        }
      }
    }
  }
}
