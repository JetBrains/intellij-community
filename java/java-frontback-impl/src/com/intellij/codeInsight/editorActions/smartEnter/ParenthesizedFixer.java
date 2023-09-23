// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_PARENTH_EXPRESSION;

public class ParenthesizedFixer implements Fixer {

  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    if (BasicJavaAstTreeUtil.is(astNode, BASIC_PARENTH_EXPRESSION)) {
      final ASTNode lastChild = astNode.getLastChildNode();
      if (lastChild != null && !")".equals(lastChild.getText())) {
        editor.getDocument().insertString(astNode.getTextRange().getEndOffset(), ")");
      }
    }
  }
}