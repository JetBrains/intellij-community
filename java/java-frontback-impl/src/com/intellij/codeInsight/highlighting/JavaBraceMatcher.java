// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.hint.DeclarationRangeUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.BasicElementTypes.*;

public class JavaBraceMatcher implements PairedBraceMatcher {
  private final BracePair[] pairs = new BracePair[] {
      new BracePair(JavaTokenType.LPARENTH, JavaTokenType.RPARENTH, false),
      new BracePair(JavaTokenType.LBRACE, JavaTokenType.RBRACE, true),
      new BracePair(JavaTokenType.LBRACKET, JavaTokenType.RBRACKET, false),
      new BracePair(JavaDocTokenType.DOC_INLINE_TAG_START, JavaDocTokenType.DOC_INLINE_TAG_END, false),
      new BracePair(JavaTokenType.LT, JavaTokenType.GT, false),
      new BracePair(JavaDocTokenType.DOC_LBRACKET, JavaDocTokenType.DOC_RBRACKET, false),
      new BracePair(JavaDocTokenType.DOC_LPAREN, JavaDocTokenType.DOC_RPAREN, false)
  };
  public JavaBraceMatcher() {
  }

  @Override
  public BracePair @NotNull [] getPairs() {
    return pairs;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType(final @NotNull IElementType lbraceType, final @Nullable IElementType contextType) {
    if (contextType instanceof IJavaElementType) return isPairedBracesAllowedBeforeTypeInJava(contextType);
    return true;
  }

  private static boolean isPairedBracesAllowedBeforeTypeInJava(final IElementType tokenType) {
    return BASIC_JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(tokenType)
           || tokenType == JavaTokenType.SEMICOLON
           || tokenType == JavaTokenType.COMMA
           || tokenType == JavaTokenType.RPARENTH
           || tokenType == JavaTokenType.RBRACKET
           || tokenType == JavaTokenType.RBRACE
           || tokenType == JavaTokenType.LBRACE
           || tokenType == JavaTokenType.DOT;
  }

  @Override
  public int getCodeConstructStart(final PsiFile file, int openingBraceOffset) {
    PsiElement element = file.findElementAt(openingBraceOffset);
    if (element == null || element instanceof PsiFile) return openingBraceOffset;
    PsiElement parent = element.getParent();
    if(parent==null) return openingBraceOffset;
    ASTNode parentNode = parent.getNode();
    if (BasicJavaAstTreeUtil.is(parentNode, BASIC_CODE_BLOCK)) {
      parentNode = parentNode.getTreeParent();
      if (BasicJavaAstTreeUtil.is(parentNode, BASIC_METHOD) ||
          BasicJavaAstTreeUtil.is(parentNode, BASIC_CLASS_INITIALIZER)) {
        TextRange range = DeclarationRangeUtil.getPossibleDeclarationAtRange(parentNode.getPsi());
        if (range == null) {
          return parentNode.getTextRange().getStartOffset();
        }
        return range.getStartOffset();
      }
      else if (BasicJavaAstTreeUtil.is(parentNode, BASIC_JAVA_STATEMENT_BIT_SET)) {
        if (BasicJavaAstTreeUtil.is(parentNode, BASIC_BLOCK_STATEMENT) &&
            BasicJavaAstTreeUtil.is(parentNode.getTreeParent(), BASIC_JAVA_STATEMENT_BIT_SET)) {
          parentNode = parentNode.getTreeParent();
        }
        return parentNode.getTextRange().getStartOffset();
      }
    }
    else if (BasicJavaAstTreeUtil.is(parentNode, BASIC_CLASS_KEYWORD_BIT_SET)) {
      TextRange range = DeclarationRangeUtil.getPossibleDeclarationAtRange(parent);
      if (range == null) {
        return parentNode.getTextRange().getStartOffset();
      }
      return range.getStartOffset();
    }
    return openingBraceOffset;
  }
}
