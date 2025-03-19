// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_CONDITIONAL_EXPRESSION;

public class TernaryColonFixer implements Fixer {

  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    if (!(BasicJavaAstTreeUtil.is(astNode, BASIC_CONDITIONAL_EXPRESSION))) {
      return;
    }

    if (BasicJavaAstTreeUtil.getConditionalExpressionThenExpression(astNode) == null ||
        astNode.findChildByType(JavaTokenType.COLON) != null) {
      return;
    }


    editor.getCaretModel().moveToOffset(astNode.getTextRange().getEndOffset());
    EditorModificationUtilEx.insertStringAtCaret(editor, ": ");
    processor.setSkipEnter(true);
  }
}
