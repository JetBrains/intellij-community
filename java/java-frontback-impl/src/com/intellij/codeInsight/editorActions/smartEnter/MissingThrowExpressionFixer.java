// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_THROW_STATEMENT;

public class MissingThrowExpressionFixer implements Fixer {
  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode)
    throws IncorrectOperationException {
    if (BasicJavaAstTreeUtil.is(astNode, BASIC_THROW_STATEMENT)) {
      ASTNode expression = BasicJavaAstTreeUtil.getExpression(astNode);
      if (expression != null &&
          startLine(editor, astNode) == startLine(editor, expression)) {
        return;
      }

      final int startOffset = astNode.getTextRange().getStartOffset();
      if (expression != null) {
        editor.getDocument().insertString(startOffset + "throw".length(), ";");
      }
      processor.registerUnresolvedError(startOffset + "throw".length());
    }
  }

  private static int startLine(Editor editor, @NotNull ASTNode psiElement) {
    return editor.getDocument().getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}
