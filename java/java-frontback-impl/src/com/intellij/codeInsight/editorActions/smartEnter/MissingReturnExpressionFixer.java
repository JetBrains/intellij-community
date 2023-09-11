// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.impl.source.BasicJavaTokenSet;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.BasicJavaElementType.*;

public class MissingReturnExpressionFixer implements Fixer {
  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    if (!(BasicJavaAstTreeUtil.is(astNode, BASIC_RETURN_STATEMENT))) {
      return;
    }
    if (!BasicJavaAstTreeUtil.hasErrorElements(astNode)) {
      return;
    }

    if (fixMethodCallWithoutTrailingSemicolon(astNode, editor, processor)) {
      return;
    }

    ASTNode returnValue = BasicJavaAstTreeUtil.getReturnValue(astNode);
    if (returnValue != null
        && lineNumber(editor, editor.getCaretModel().getOffset()) == lineNumber(editor, returnValue.getTextRange().getStartOffset())) {
      return;
    }

    ASTNode parent = BasicJavaAstTreeUtil.getParentOfType(astNode, BasicJavaTokenSet.create(BASIC_CLASS_INITIALIZER, BASIC_METHOD));
    if (BasicJavaAstTreeUtil.is(parent, BASIC_METHOD)) {
      ASTNode type = BasicJavaAstTreeUtil.findChildByType(parent, BASIC_TYPE);
      if (type != null && !type.getText().equals("void")) {
        final int startOffset = astNode.getTextRange().getStartOffset();
        if (returnValue != null) {
          editor.getDocument().insertString(startOffset + "return".length(), ";");
        }

        processor.registerUnresolvedError(startOffset + "return".length());
      }
    }
  }

  private static boolean fixMethodCallWithoutTrailingSemicolon(@Nullable ASTNode returnStatement, @NotNull Editor editor,
                                                               @NotNull AbstractBasicJavaSmartEnterProcessor processor) {
    if (returnStatement == null) {
      return false;
    }

    final ASTNode lastChild = returnStatement.getLastChildNode();
    if (!(BasicJavaAstTreeUtil.is(lastChild, TokenType.ERROR_ELEMENT))) {
      return false;
    }
    ASTNode prev = lastChild.getTreePrev();
    if (BasicJavaAstTreeUtil.isWhiteSpace(prev)) {
      prev = prev.getTreePrev();
    }

    if (!(prev instanceof PsiJavaToken prevToken)) {
      int offset = returnStatement.getTextRange().getEndOffset();
      final PsiElement psiMethod =
        BasicJavaAstTreeUtil.getParentOfType(BasicJavaAstTreeUtil.toPsi(returnStatement), BASIC_METHOD, true,
                                             BasicJavaTokenSet.create(BASIC_LAMBDA_EXPRESSION));
      ASTNode method = BasicJavaAstTreeUtil.toNode(psiMethod);
      ASTNode type = BasicJavaAstTreeUtil.findChildByType(method, BASIC_TYPE);
      if (method != null && type != null && type.getText().equals("void")) {
        offset = returnStatement.getTextRange().getStartOffset() + "return".length();
      }
      editor.getDocument().insertString(offset, ";");
      //processor.setSkipEnter(true);
      return true;
    }

    if (prevToken.getTokenType() == JavaTokenType.SEMICOLON) {
      return false;
    }

    final int offset = returnStatement.getTextRange().getEndOffset();
    editor.getDocument().insertString(offset, ";");
    if (prevToken.getTokenType() == JavaTokenType.RETURN_KEYWORD) {
      final ASTNode method = BasicJavaAstTreeUtil.getParentOfType(returnStatement, BASIC_METHOD);
      ASTNode type = BasicJavaAstTreeUtil.findChildByType(method, BASIC_TYPE);
      if (method != null && type != null && !type.getText().equals("void")) {
        editor.getCaretModel().moveToOffset(offset);
        processor.setSkipEnter(true);
      }
    }
    return true;
  }


  private static int lineNumber(Editor editor, int offset) {
    return editor.getDocument().getLineNumber(offset);
  }
}
