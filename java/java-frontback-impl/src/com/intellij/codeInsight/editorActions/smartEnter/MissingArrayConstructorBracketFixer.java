// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_NEW_EXPRESSION;

public class MissingArrayConstructorBracketFixer implements Fixer {

  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    if (!(BasicJavaAstTreeUtil.is(astNode, BASIC_NEW_EXPRESSION))) return;
    int count = 0;
    for (ASTNode element = astNode.getFirstChildNode(); element != null; element = element.getTreeNext()) {
      if (BasicJavaAstTreeUtil.is(element, JavaTokenType.LBRACKET)) {
        count++;
      }
      else if (BasicJavaAstTreeUtil.is(element, JavaTokenType.RBRACKET)) {
        count--;
      }
    }
    if (count > 0) {
      editor.getDocument().insertString(astNode.getTextRange().getEndOffset(), "]");
    }
  }
}