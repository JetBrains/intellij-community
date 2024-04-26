// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ClsNameValuePairImpl extends ClsElementImpl implements PsiNameValuePair {
  private final ClsElementImpl myParent;
  private final ClsIdentifierImpl myNameIdentifier;
  private final PsiAnnotationMemberValue myMemberValue;

  ClsNameValuePairImpl(@NotNull ClsElementImpl parent, @Nullable String name, @NotNull PsiAnnotationMemberValue value) {
    myParent = parent;
    myNameIdentifier = name != null ? new ClsIdentifierImpl(this, name) : null;
    myMemberValue = ClsParsingUtil.getMemberValue(value, this);
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    appendText(myNameIdentifier, 0, buffer, " = ");
    appendText(myMemberValue, 0, buffer);
  }

  @Override
  protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);

    PsiNameValuePair mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);
    setMirrorIfPresent(getNameIdentifier(), mirror.getNameIdentifier());
    setMirrorIfPresent(getValue(), mirror.getValue());
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    if (myNameIdentifier != null) {
      return new PsiElement[]{myNameIdentifier, myMemberValue};
    }
    else {
      return new PsiElement[]{myMemberValue};
    }
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitNameValuePair(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return myNameIdentifier;
  }

  @Override
  public String getName() {
    return myNameIdentifier != null ? myNameIdentifier.getText() : null;
  }

  @Override
  public String getLiteralValue() {
    return null;
  }

  @Override
  public PsiAnnotationMemberValue getValue() {
    return myMemberValue;
  }

  @Override
  public @NotNull PsiAnnotationMemberValue setValue(@NotNull PsiAnnotationMemberValue newValue) {
    throw cannotModifyException(this);
  }
}
