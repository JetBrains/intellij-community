// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiSnippetDocTag;
import com.intellij.psi.javadoc.PsiSnippetDocTagValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiSnippetDocTagImpl extends CompositePsiElement implements PsiSnippetDocTag, PsiLanguageInjectionHost {
  public PsiSnippetDocTagImpl() {
    super(JavaDocElementType.DOC_SNIPPET_TAG);
  }

  @Override
  public @NotNull String getName() {
    return getNameElement().getText().substring(1);
  }

  @Override
  public PsiDocComment getContainingComment() {
    ASTNode scope = getTreeParent();
    while (scope.getElementType() != JavaDocElementType.DOC_COMMENT) {
      scope = scope.getTreeParent();
    }
    return (PsiDocComment)SourceTreeToPsiMap.treeElementToPsi(scope);
  }

  @Override
  public PsiElement getNameElement() {
    return findPsiChildByType(JavaDocTokenType.DOC_TAG_NAME);
  }

  @Override
  public PsiElement @NotNull [] getDataElements() {
    return getChildrenAsPsiElements(PsiInlineDocTagImpl.VALUE_BIT_SET, PsiElement.ARRAY_FACTORY);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public @Nullable PsiSnippetDocTagValue getValueElement() {
    return (PsiSnippetDocTagValue)findPsiChildByType(JavaDocElementType.DOC_SNIPPET_TAG_VALUE);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    super.accept(visitor);
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSnippetTag(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiSnippetDocTag";
  }

  @Override
  public boolean isValidHost() {
    return true;
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull String text) {
    return new SnippetDocTagManipulator().handleContentChange(this, text);
  }

  @Override
  public @NotNull LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new LiteralTextEscaper<PsiSnippetDocTagImpl>(this) {
      private int[] outSourceOffsets;
      @Override
      public boolean decode(@NotNull TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
        String subText = rangeInsideHost.substring(myHost.getText());
        outSourceOffsets = new int[subText.length() + 1];

        final int outOffset = outChars.length();
        int off = 0;
        int len = subText.length();

        boolean lookingForLeadingAsterisk = true;
        while (off < len) {
          final char aChar = subText.charAt(off++);
          if (aChar != '*' && !Character.isWhitespace(aChar)) {
            lookingForLeadingAsterisk = false;
          }
          else if (lookingForLeadingAsterisk && aChar == '*') {
            while (off < len && subText.charAt(off) == '*') {
              off++;
            }
            lookingForLeadingAsterisk = false;
            continue;
          }
          else if (aChar == '\n') {
            lookingForLeadingAsterisk = true;
          }
          outChars.append(aChar);
          outSourceOffsets[outChars.length() - outOffset] = off;
        }

        return true;
      }

      @Override
      public int getOffsetInHost(int offsetInDecoded, @NotNull TextRange rangeInsideHost) {
        int result = offsetInDecoded < outSourceOffsets.length ? outSourceOffsets[offsetInDecoded] : -1;
        if (result == -1) return -1;
        return rangeInsideHost.getStartOffset() + Math.min(result, rangeInsideHost.getLength());
      }

      @Override
      public boolean isOneLine() {
        return false;
      }
    };
  }
}
