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

import com.intellij.psi.*;
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

  ClsDocCommentImpl(PsiDocCommentOwner parent) {
    myParent = parent;
    
    PsiDocTag[] tags = new PsiDocTag[1];
    tags[0] = new ClsDocTagImpl(this, "@deprecated");
    myTags = tags;
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    buffer.append("/**");
    for (PsiDocTag tag : getTags()) {
      goNextLine(indentLevel + 1, buffer);
      buffer.append("* ");
      buffer.append(tag.getText());
    }
    goNextLine(indentLevel + 1, buffer);
    buffer.append("*/");
  }

  public void setMirror(@NotNull TreeElement element) {
    setMirrorCheckingType(element, JavaDocElementType.DOC_COMMENT);
  }

  @NotNull
  public PsiElement[] getChildren() {
    return getTags();
  }

  public PsiElement getParent() {
    return myParent;
  }

  public PsiDocCommentOwner getOwner() {
    return myParent;
  }

  @NotNull
  public PsiElement[] getDescriptionElements() {
    return EMPTY_ARRAY;
  }

  @NotNull
  public PsiDocTag[] getTags() {
    return myTags;
  }

  public PsiDocTag findTagByName(@NonNls String name) {
    if (!name.equals("deprecated")) return null;
    return getTags()[0];
  }

  @NotNull
  public PsiDocTag[] findTagsByName(@NonNls String name) {
    if (!name.equals("deprecated")) return PsiDocTag.EMPTY_ARRAY;
    return getTags();
  }

  public IElementType getTokenType() {
    return JavaDocElementType.DOC_COMMENT;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocComment(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

}
