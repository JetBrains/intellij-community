// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.hint.DeclarationRangeUtil;
import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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


  @Override
  public BracePair @NotNull [] getPairs() {
    return pairs;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType(@NotNull final IElementType lbraceType, @Nullable final IElementType contextType) {
    if (contextType instanceof IJavaElementType) return isPairedBracesAllowedBeforeTypeInJava(contextType);
    return true;
  }

  private static boolean isPairedBracesAllowedBeforeTypeInJava(final IElementType tokenType) {
    return ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(tokenType)
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
    if (parent instanceof PsiCodeBlock) {
      parent = parent.getParent();
      if (parent instanceof PsiMethod || parent instanceof PsiClassInitializer) {
        TextRange range = DeclarationRangeUtil.getDeclarationRange(parent);
        return range.getStartOffset();
      }
      else if (parent instanceof PsiStatement) {
        if (parent instanceof PsiBlockStatement && parent.getParent() instanceof PsiStatement) {
          parent = parent.getParent();
        }
        return parent.getTextRange().getStartOffset();
      }
    }
    else if (parent instanceof PsiClass) {
      TextRange range = DeclarationRangeUtil.getDeclarationRange(parent);
      return range.getStartOffset();
    }
    return openingBraceOffset;
  }
}
