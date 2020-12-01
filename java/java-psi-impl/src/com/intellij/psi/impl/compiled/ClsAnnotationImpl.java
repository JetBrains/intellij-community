// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ClsAnnotationImpl extends ClsRepositoryPsiElement<PsiAnnotationStub> implements PsiAnnotation, Navigatable {
  private final NotNullLazyValue<ClsJavaCodeReferenceElementImpl> myReferenceElement;
  private final NotNullLazyValue<ClsAnnotationParameterListImpl> myParameterList;

  public ClsAnnotationImpl(PsiAnnotationStub stub) {
    super(stub);
    myReferenceElement = NotNullLazyValue.atomicLazy(() -> {
      String annotationText = getStub().getText();
      int index = annotationText.indexOf('(');
      String refText = index > 0 ? annotationText.substring(1, index) : annotationText.substring(1);
      return new ClsJavaCodeReferenceElementImpl(this, refText);
    });
    myParameterList = NotNullLazyValue.atomicLazy(() -> {
      PsiNameValuePair[] attrs = getStub().getText().indexOf('(') > 0
                                 ? PsiTreeUtil.getRequiredChildOfType(getStub().getPsiElement(), PsiAnnotationParameterList.class)
                                   .getAttributes()
                                 : PsiNameValuePair.EMPTY_ARRAY;
      return new ClsAnnotationParameterListImpl(this, attrs);
    });
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    buffer.append('@').append(myReferenceElement.getValue().getCanonicalText());
    appendText(getParameterList(), indentLevel, buffer);
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);
    PsiAnnotation mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);
    setMirror(getNameReferenceElement(), mirror.getNameReferenceElement());
    setMirror(getParameterList(), mirror.getParameterList());
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return new PsiElement[]{myReferenceElement.getValue(), getParameterList()};
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
  @NotNull
  public PsiAnnotationParameterList getParameterList() {
    return myParameterList.getValue();
  }

  @Override
  public @NotNull String getQualifiedName() {
    return myReferenceElement.getValue().getCanonicalText();
  }

  @Override
  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    return myReferenceElement.getValue();
  }

  @Override
  public PsiAnnotationMemberValue findAttributeValue(String attributeName) {
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  @Override
  @Nullable
  public PsiAnnotationMemberValue findDeclaredAttributeValue(@NonNls final String attributeName) {
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

  @Override
  public PsiAnnotationOwner getOwner() {
    return (PsiAnnotationOwner)getParent();//todo
  }
}
