// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_CATCH_SECTION;

public class MissingCatchBodyFixer implements Fixer {

  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    if (!(BasicJavaAstTreeUtil.is(astNode, BASIC_CATCH_SECTION))) return;

    ASTNode body = BasicJavaAstTreeUtil.getCatchBlock(astNode);
    if (body != null && BasicJavaAstTreeUtil.getLBrace(body) != null && BasicJavaAstTreeUtil.getRBrace(body) != null) return;

    final ASTNode rParenth = BasicJavaAstTreeUtil.getRParenth(astNode);
    if (rParenth == null) return;

    processor.insertBraces(editor, rParenth.getTextRange().getEndOffset());
  }
}