// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class ClsAnnotationValueImpl extends ClsElementImpl implements PsiAnnotation, Navigatable {
  private final ClsElementImpl myParent;
  private final ClsJavaCodeReferenceElementImpl myReferenceElement;
  private final ClsAnnotationParameterListImpl myParameterList;

  @SuppressWarnings("AbstractMethodCallInConstructor")
  ClsAnnotationValueImpl(@NotNull ClsElementImpl parent) {
    myParent = parent;
    myReferenceElement = createReference();
    myParameterList = createParameterList();
  }

  protected abstract ClsAnnotationParameterListImpl createParameterList();

  protected abstract ClsJavaCodeReferenceElementImpl createReference();

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    buffer.append("@").append(myReferenceElement.getCanonicalText());
    myParameterList.appendMirrorText(indentLevel, buffer);
  }

  @Override
  protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);

    PsiAnnotation mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);
    setMirror(getNameReferenceElement(), mirror.getNameReferenceElement());
    setMirror(getParameterList(), mirror.getParameterList());
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return new PsiElement[]{myReferenceElement, myParameterList};
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotation(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @NotNull PsiAnnotationParameterList getParameterList() {
    return myParameterList;
  }

  @Override
  public @Nullable String getQualifiedName() {
    return myReferenceElement != null ? myReferenceElement.getCanonicalText() : null;
  }

  @Override
  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    return myReferenceElement;
  }

  @Override
  public PsiAnnotationMemberValue findAttributeValue(String attributeName) {
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  @Override
  public @Nullable PsiAnnotationMemberValue findDeclaredAttributeValue(final @NonNls String attributeName) {
    return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
  }

  @Override
  public <T extends PsiAnnotationMemberValue> T setDeclaredAttributeValue(@NonNls String attributeName, T value) {
    throw cannotModifyException(this);
  }

  @Override
  public String getText() {
    final StringBuilder buffer = new StringBuilder();
    appendMirrorText(0, buffer);
    return buffer.toString();
  }
}
