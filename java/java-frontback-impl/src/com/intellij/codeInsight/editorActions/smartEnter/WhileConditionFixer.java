// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_WHILE_STATEMENT;

public class WhileConditionFixer implements Fixer {
  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    if (BasicJavaAstTreeUtil.is(astNode, BASIC_WHILE_STATEMENT)) {
      final Document doc = editor.getDocument();
      final ASTNode rParenth = BasicJavaAstTreeUtil.getRParenth(astNode);
      final ASTNode lParenth = BasicJavaAstTreeUtil.getLParenth(astNode);
      final ASTNode condition = BasicJavaAstTreeUtil.getWhileCondition(astNode);

      if (condition == null) {
        if (lParenth == null || rParenth == null) {
          int stopOffset = doc.getLineEndOffset(doc.getLineNumber(astNode.getTextRange().getStartOffset()));
          final ASTNode block = BasicJavaAstTreeUtil.getWhileBody(astNode);
          if (block != null) {
            stopOffset = Math.min(stopOffset, block.getTextRange().getStartOffset());
          }
          stopOffset = Math.min(stopOffset, astNode.getTextRange().getEndOffset());

          doc.replaceString(astNode.getTextRange().getStartOffset(), stopOffset, "while ()");
          processor.registerUnresolvedError(astNode.getTextRange().getStartOffset() + "while (".length());
        } else {
          processor.registerUnresolvedError(lParenth.getTextRange().getEndOffset());
        }
      } else if (rParenth == null) {
        doc.insertString(condition.getTextRange().getEndOffset(), ")");
      }
    }
  }
}
