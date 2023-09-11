// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class LiteralFixer implements Fixer {
  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode)
    throws IncorrectOperationException {
    if (astNode.getElementType() == JavaTokenType.STRING_LITERAL &&
        !StringUtil.endsWithChar(astNode.getText(), '\"')) {
      editor.getDocument().insertString(astNode.getTextRange().getEndOffset(), "\"");
    }
    else if (astNode.getElementType() == JavaTokenType.CHARACTER_LITERAL &&
             !StringUtil.endsWithChar(astNode.getText(), '\'')) {
      editor.getDocument().insertString(astNode.getTextRange().getEndOffset(), "'");
    }
  }
}
