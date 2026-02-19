// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

class ClsDocTagImpl extends ClsElementImpl implements PsiDocTag {
  private final ClsDocCommentImpl myDocComment;
  private final PsiElement myNameElement;

  ClsDocTagImpl(ClsDocCommentImpl docComment, @NonNls String name) {
    myDocComment = docComment;
    myNameElement = new NameElement(this, name);
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    buffer.append(myNameElement.getText());
  }

  @Override
  protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaDocElementType.DOC_TAG);
  }

  @Override
  public String getText() {
    return myNameElement.getText();
  }

  @Override
  public char @NotNull [] textToCharArray() {
    return myNameElement.textToCharArray();
  }

  @Override
  public @NotNull String getName() {
    return getNameElement().getText().substring(1);
  }

  @Override
  public boolean textMatches(@NotNull CharSequence text) {
    return myNameElement.textMatches(text);
  }

  @Override
  public boolean textMatches(@NotNull PsiElement element) {
    return myNameElement.textMatches(element);
  }

  @Override
  public int getTextLength() {
    return myNameElement.getTextLength();
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return new PsiElement[]{myNameElement};
  }

  @Override
  public PsiElement getParent() {
    return getContainingComment();
  }

  @Override
  public PsiDocComment getContainingComment() {
    return myDocComment;
  }

  @Override
  public PsiElement getNameElement() {
    return myNameElement;
  }

  @Override
  public PsiElement @NotNull [] getDataElements() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  public PsiDocTagValue getValueElement() {
    return null;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocTag(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameElement(), name);
    return this;
  }

  private static class NameElement extends ClsElementImpl {
    private final ClsDocTagImpl myParent;
    private final String myText;

    NameElement(ClsDocTagImpl parent, String text) {
      myParent = parent;
      myText = text;
    }

    @Override
    public String getText() {
      return myText;
    }

    @Override
    public char @NotNull [] textToCharArray() {
      return myText.toCharArray();
    }

    @Override
    public PsiElement @NotNull [] getChildren() {
      return PsiElement.EMPTY_ARRAY;
    }

    @Override
    public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    }

    @Override
    protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
      setMirrorCheckingType(element, null);
    }

    @Override
    public PsiElement getParent() {
      return myParent;
    }
  }
}
