// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java;

import com.intellij.lang.ASTNode;
import com.intellij.lang.CodeDocumentationAwareCommenterEx;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

public final class JavaCommenter implements CodeDocumentationAwareCommenterEx {

  @Override
  public String getLineCommentPrefix() {
    return "//";
  }

  @Override
  public String getBlockCommentPrefix() {
    return "/*";
  }

  @Override
  public String getBlockCommentSuffix() {
    return "*/";
  }

  @Override
  public String getCommentedBlockCommentPrefix() {
    return null;
  }

  @Override
  public String getCommentedBlockCommentSuffix() {
    return null;
  }

  @Override
  @Nullable
  public IElementType getLineCommentTokenType() {
    return JavaTokenType.END_OF_LINE_COMMENT;
  }

  @Override
  @Nullable
  public IElementType getBlockCommentTokenType() {
    return JavaTokenType.C_STYLE_COMMENT;
  }

  @Override
  @Nullable
  public IElementType getDocumentationCommentTokenType() {
    return JavaDocElementType.DOC_COMMENT;
  }

  @Override
  public String getDocumentationCommentPrefix() {
    return "/**";
  }

  @Override
  public String getDocumentationCommentLinePrefix() {
    return "*";
  }

  @Override
  public String getDocumentationCommentSuffix() {
    return "*/";
  }

  @Override
  public boolean isDocumentationComment(final PsiComment element) {
    return element instanceof PsiDocComment;
  }

  @Override
  public boolean isDocumentationCommentText(final PsiElement element) {
    if (element == null) return false;
    final ASTNode node = element.getNode();
    return node != null && (node.getElementType() == JavaDocTokenType.DOC_COMMENT_DATA || node.getElementType() == JavaDocTokenType.DOC_TAG_VALUE_TOKEN);
  }
}
