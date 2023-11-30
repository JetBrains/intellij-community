// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiJavaModuleReferenceImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

class ClsJavaModuleReferenceElementImpl extends ClsElementImpl implements PsiJavaModuleReferenceElement {
  private final PsiElement myParent;
  private final String myText;
  private final PsiJavaModuleReference myReference;

  ClsJavaModuleReferenceElementImpl(PsiElement parent, String text) {
    myParent = parent;
    myText = text;
    myReference = myParent instanceof PsiJavaModule ? null : new PsiJavaModuleReferenceImpl(this);
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return EMPTY_ARRAY;
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    buffer.append(getReferenceText());
  }

  @Override
  protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.MODULE_REFERENCE);
  }

  @NotNull
  @Override
  public String getReferenceText() {
    return myText;
  }

  @Override
  public PsiJavaModuleReference getReference() {
    return myReference;
  }

  @Override
  public String getText() {
    return myText;
  }

  @Override
  public int getTextLength() {
    return myText.length();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitModuleReferenceElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiJavaModuleReference";
  }
}
