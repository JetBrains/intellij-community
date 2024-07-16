// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

class ClsDocCommentImpl extends ClsElementImpl implements PsiDocComment, JavaTokenType, PsiJavaToken {
  private final PsiDocCommentOwner myParent;
  private final PsiDocTag[] myTags;

  ClsDocCommentImpl(@NotNull PsiDocCommentOwner parent) {
    myParent = parent;
    myTags = new PsiDocTag[]{new ClsDocTagImpl(this, "@deprecated")};
  }

  @Override
  public void appendMirrorText(final int indentLevel, final @NotNull StringBuilder buffer) {
    buffer.append("/**");
    for (PsiDocTag tag : getTags()) {
      goNextLine(indentLevel + 1, buffer);
      buffer.append("* ");
      buffer.append(tag.getText());
    }
    goNextLine(indentLevel + 1, buffer);
    buffer.append("*/");
  }

  @Override
  protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaDocElementType.DOC_COMMENT);
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return getTags();
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public PsiJavaDocumentedElement getOwner() {
    return PsiImplUtil.findDocCommentOwner(this);
  }

  @Override
  public PsiElement @NotNull [] getDescriptionElements() {
    return EMPTY_ARRAY;
  }

  @Override
  public PsiDocTag @NotNull [] getTags() {
    return myTags;
  }

  @Override
  public PsiDocTag findTagByName(@NonNls String name) {
    return name.equals("deprecated") ? getTags()[0] : null;
  }

  @Override
  public PsiDocTag @NotNull [] findTagsByName(@NonNls String name) {
    return name.equals("deprecated") ? getTags() : PsiDocTag.EMPTY_ARRAY;
  }

  @Override
  public @NotNull IElementType getTokenType() {
    return JavaDocElementType.DOC_COMMENT;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocComment(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
}
