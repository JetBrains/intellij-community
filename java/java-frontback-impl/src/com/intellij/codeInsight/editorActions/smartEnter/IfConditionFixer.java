// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.BasicElementTypes.BASIC_JAVA_PLAIN_COMMENT_BIT_SET;
import static com.intellij.psi.impl.source.BasicJavaDocElementType.BASIC_DOC_COMMENT;
import static com.intellij.psi.impl.source.BasicJavaElementType.*;

public class IfConditionFixer implements Fixer {

  @Override
  public void apply(Editor editor, AbstractBasicJavaSmartEnterProcessor processor, @NotNull ASTNode astNode) throws IncorrectOperationException {
    if (BasicJavaAstTreeUtil.is(astNode, BASIC_IF_STATEMENT)) {
      final Document doc = editor.getDocument();
      final ASTNode rParen = BasicJavaAstTreeUtil.getRParenth(astNode);
      final ASTNode lParen = BasicJavaAstTreeUtil.getLParenth(astNode);
      final ASTNode condition = BasicJavaAstTreeUtil.getIfCondition(astNode);

      if (condition == null) {
        if (lParen == null || rParen == null) {
          int stopOffset = doc.getLineEndOffset(doc.getLineNumber(astNode.getTextRange().getStartOffset()));
          final ASTNode then = BasicJavaAstTreeUtil.getThenBranch(astNode);
          if (then != null) {
            stopOffset = Math.min(stopOffset, then.getTextRange().getStartOffset());
          }
          stopOffset = Math.min(stopOffset, astNode.getTextRange().getEndOffset());

          ASTNode lastChild = astNode.getLastChildNode();
          String innerComment = "";
          String lastComment = "";
          if (lParen != null && PsiUtilCore.getElementType(lastChild) == JavaTokenType.C_STYLE_COMMENT) {
            innerComment = lastChild.getText();
          }
          else if (BasicJavaAstTreeUtil.is(lastChild, BASIC_DOC_COMMENT) ||
                   BasicJavaAstTreeUtil.is(lastChild, BASIC_JAVA_PLAIN_COMMENT_BIT_SET)
          ) {
            lastComment = lastChild.getText();
          }

          String prefix = "if (" + innerComment;
          doc.replaceString(astNode.getTextRange().getStartOffset(), stopOffset, prefix + ")" + lastComment);

          processor.registerUnresolvedError(astNode.getTextRange().getStartOffset() + prefix.length());
        }
        else {
          processor.registerUnresolvedError(lParen.getTextRange().getEndOffset());
        }
      }
      else if (rParen == null) {
        doc.insertString(condition.getTextRange().getEndOffset(), ")");
      }
    }
    else if (BasicJavaAstTreeUtil.is(astNode, EXPRESSION_SET) &&
             BasicJavaAstTreeUtil.is(astNode.getTreeParent(), BASIC_EXPRESSION_STATEMENT)) {
      PsiElement psi = BasicJavaAstTreeUtil.toPsi(astNode);
      if (psi != null) {
        PsiElement prevLeaf = PsiTreeUtil.prevVisibleLeaf(psi);
        if (prevLeaf != null && prevLeaf.textMatches(JavaKeywords.IF)) {
          Document doc = editor.getDocument();
          doc.insertString(astNode.getTextRange().getEndOffset(), ")");
          doc.insertString(astNode.getTextRange().getStartOffset(), "(");
        }
      }
    }
  }
}
