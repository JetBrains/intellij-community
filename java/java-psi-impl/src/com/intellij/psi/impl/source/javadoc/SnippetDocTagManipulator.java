// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaFileCodeStyleFacade;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiSnippetDocTag;
import com.intellij.psi.javadoc.PsiSnippetDocTagBody;
import com.intellij.psi.javadoc.PsiSnippetDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class SnippetDocTagManipulator extends AbstractElementManipulator<PsiSnippetDocTagImpl> {

  @Override
  public PsiSnippetDocTagImpl handleContentChange(@NotNull PsiSnippetDocTagImpl element,
                                                  @NotNull TextRange range,
                                                  String newContent) throws IncorrectOperationException {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());

    final JavaFileCodeStyleFacade codeStyleFacade = JavaFileCodeStyleFacade.forContext(element.getContainingFile());
    final String newSnippetTagContent = codeStyleFacade.isJavaDocLeadingAsterisksEnabled()
                                        ? prependAbsentAsterisks(newContent)
                                        : newContent;

    final PsiDocComment text = factory.createDocCommentFromText("/**\n" + newSnippetTagContent + "\n*/");
    final PsiSnippetDocTag snippet = PsiTreeUtil.findChildOfType(text, PsiSnippetDocTag.class);
    if (snippet == null) {
      return element;
    }
    return (PsiSnippetDocTagImpl)element.replace(snippet);
  }

  @Contract(pure = true)
  private static @NotNull String prependAbsentAsterisks(@NotNull String input) {
    final StringBuilder builder = new StringBuilder();
    boolean afterNewLine = false;
    for (char c : input.toCharArray()) {
      if (c == '\n') {
        afterNewLine = true;
      }
      else if (afterNewLine) {
        if (c == '*') {
          afterNewLine = false;
        }
        else if (!Character.isWhitespace(c)) {
          builder.append("* ");
          afterNewLine = false;
        }
      }
      builder.append(c);
    }
    return builder.toString();
  }

  @Override
  public @NotNull TextRange getRangeInElement(@NotNull PsiSnippetDocTagImpl element) {
    final PsiSnippetDocTagValue valueElement = element.getValueElement();
    if (valueElement == null) return super.getRangeInElement(element);

    final PsiSnippetDocTagBody body = valueElement.getBody();
    if (body == null) return super.getRangeInElement(element);

    final TextRange elementTextRange = element.getTextRange();
    final PsiElement[] children = body.getChildren();

    final int startOffset;
    if (children.length == 0) {
      startOffset = body.getTextRange().getStartOffset();
    }
    else {
      final PsiElement colon = getColonElement(children);
      startOffset = colon.getTextRange().getEndOffset();
    }

    final TextRange snippetRange = TextRange.create(startOffset, body.getTextRange().getEndOffset());

    return snippetRange.shiftLeft(elementTextRange.getStartOffset());
  }

  private static @NotNull PsiElement getColonElement(PsiElement@NotNull [] children) {
    final ASTNode node = children[0].getNode();
    if (node.getElementType() == JavaDocTokenType.DOC_TAG_VALUE_COLON) {
      return children[0];
    }

    final ASTNode colonNode = TreeUtil.findSibling(node, JavaDocTokenType.DOC_TAG_VALUE_COLON);
    if (colonNode == null) {
      return children[0];
    }

    return colonNode.getPsi();
  }
}
