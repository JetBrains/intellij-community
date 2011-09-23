
/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

class ClsDocTagImpl extends ClsElementImpl implements PsiDocTag {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsDocTagImpl");

  private final ClsDocCommentImpl myDocComment;
  private final PsiElement myNameElement;

  public ClsDocTagImpl(ClsDocCommentImpl docComment, @NonNls String name) {
    myDocComment = docComment;
    myNameElement = new NameElement(name);
  }

  public void appendMirrorText(final int indentLevel, final StringBuilder buffer) {
    buffer.append(myNameElement.getText());
  }

  public void setMirror(@NotNull TreeElement element) {
    setMirrorCheckingType(element, ElementType.DOC_TAG);
  }

  public String getText() {
    return myNameElement.getText();
  }

  @NotNull
  public char[] textToCharArray(){
    return myNameElement.textToCharArray();
  }

  public String getName() {
    return getNameElement().getText().substring(1);
  }

  public boolean textMatches(@NotNull CharSequence text) {
    return myNameElement.textMatches(text);
  }

  public boolean textMatches(@NotNull PsiElement element) {
    return myNameElement.textMatches(element);
  }

  public int getTextLength(){
    return myNameElement.getTextLength();
  }

  @NotNull
  public PsiElement[] getChildren() {
    return new PsiElement[]{myNameElement};
  }

  public PsiElement getParent() {
    return getContainingComment();
  }

  public PsiDocComment getContainingComment() {
    return myDocComment;
  }

  public PsiElement getNameElement() {
    return myNameElement;
  }

  public PsiElement[] getDataElements() {
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiDocTagValue getValueElement() {
    return null;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocTag(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  private class NameElement extends ClsElementImpl {
    private final String myText;

    public NameElement(String text) {
      myText = text;
    }

    public String getText() {
      return myText;
    }

    @NotNull
    public char[] textToCharArray(){
      return myText.toCharArray();
    }

    @NotNull
    public PsiElement[] getChildren(){
      return PsiElement.EMPTY_ARRAY;
    }

    public void appendMirrorText(final int indentLevel, final StringBuilder buffer) {
    }

    public void setMirror(@NotNull TreeElement element) {
      setMirrorCheckingType(element, null);
    }

    public PsiElement getParent() {
      return ClsDocTagImpl.this;
    }

    public void accept(@NotNull PsiElementVisitor visitor) {
      visitor.visitElement(this);
    }
  }
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException{
    PsiImplUtil.setName(getNameElement(), name);
    return this;
  }
}
