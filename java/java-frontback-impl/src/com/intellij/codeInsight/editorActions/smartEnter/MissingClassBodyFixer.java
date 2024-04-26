// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.BasicJavaElementType.*;

public class MissingClassBodyFixer implements Fixer {

  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    if (BasicJavaAstTreeUtil.is(astNode, BASIC_RECORD_COMPONENT)) {
      astNode = BasicJavaAstTreeUtil.getRecordComponentContainingClass(astNode);
    }
    if (!(BasicJavaAstTreeUtil.is(astNode, CLASS_SET)) ||
        BasicJavaAstTreeUtil.is(astNode, BASIC_TYPE_PARAMETER)) {
      return;
    }

    if (BasicJavaAstTreeUtil.getLBrace(astNode) == null && astNode != null) {
      ASTNode lastChild = astNode.getLastChildNode();
      int offset = astNode.getTextRange().getEndOffset();
      if (BasicJavaAstTreeUtil.is(lastChild, TokenType.ERROR_ELEMENT)) {
        ASTNode previous = lastChild.getTreePrev();
        if (BasicJavaAstTreeUtil.isWhiteSpace(previous)) {
          offset = previous.getTextRange().getStartOffset();
        }
        else {
          offset = lastChild.getTextRange().getStartOffset();
        }
      }
      if (BasicJavaAstTreeUtil.isInterfaceEnumClassOrRecord(astNode, JavaTokenType.RECORD_KEYWORD) &&
          BasicJavaAstTreeUtil.getRecordHeader(astNode) == null) {
        editor.getDocument().insertString(offset, "() {}");
        editor.getCaretModel().moveToOffset(offset + 1);
        processor.setSkipEnter(true);
      }
      else {
        processor.insertBracesWithNewLine(editor, offset);
        editor.getCaretModel().moveToOffset(offset + 1);
      }
    }
  }
}
