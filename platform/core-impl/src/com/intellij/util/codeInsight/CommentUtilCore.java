// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.codeInsight;

import com.intellij.lang.*;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

public class CommentUtilCore {
  public static boolean isComment(final @Nullable PsiElement element) {
    return element != null && isComment(element.getNode());
  }

  public static boolean isComment(final @Nullable ASTNode node) {
    if (node == null) return false;
    final IElementType type = node.getElementType();
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(type.getLanguage());
    return parserDefinition != null && parserDefinition.getCommentTokens().contains(type);
  }

  public static boolean isCommentTextElement(final PsiElement element) {
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(element.getLanguage());
    if (commenter instanceof CodeDocumentationAwareCommenterEx) {
      final CodeDocumentationAwareCommenterEx commenterEx = (CodeDocumentationAwareCommenterEx)commenter;
      if (commenterEx.isDocumentationCommentText(element)) return true;
      if (element instanceof PsiComment && commenterEx.isDocumentationComment((PsiComment)element)) return false;
    }

    return isComment(element);
  }
}
