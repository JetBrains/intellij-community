// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.BasicJavaElementType.BASIC_CATCH_SECTION;

public class CatchDeclarationFixer implements Fixer {
  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    if (BasicJavaAstTreeUtil.is(astNode, BASIC_CATCH_SECTION)) {
      final Document doc = editor.getDocument();

      final int catchStart = astNode.getTextRange().getStartOffset();
      int stopOffset = doc.getLineEndOffset(doc.getLineNumber(catchStart));

      final ASTNode catchBlock = BasicJavaAstTreeUtil.getCatchBlock(astNode);
      if (catchBlock != null) {
        stopOffset = Math.min(stopOffset, catchBlock.getTextRange().getStartOffset());
      }
      stopOffset = Math.min(stopOffset, astNode.getTextRange().getEndOffset());

      final ASTNode lParenth = BasicJavaAstTreeUtil.getLParenth(astNode);
      if (lParenth == null) {
        doc.replaceString(catchStart, stopOffset, "catch ()");
        processor.registerUnresolvedError(catchStart + "catch (".length());
      }
      else {
        if (BasicJavaAstTreeUtil.getParameter(astNode) == null) {
          processor.registerUnresolvedError(lParenth.getTextRange().getEndOffset());
        }
        if (BasicJavaAstTreeUtil.getRParenth(astNode) == null) {
          doc.insertString(stopOffset, ")");
        }
      }
    }
  }
}