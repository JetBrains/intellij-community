// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiPackageStatementStub;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;

public class ClsPackageStatementImpl extends ClsRepositoryPsiElement<PsiPackageStatementStub> implements PsiPackageStatement {

  private final String myPackageName;

  public ClsPackageStatementImpl(@NotNull PsiPackageStatementStub stub) {
    super(stub);
    myPackageName = stub.getPackageName();
  }

  @Override
  public PsiJavaCodeReferenceElement getPackageReference() {
    throw new UnsupportedOperationException("Method not implemented");
  }

  @Override
  public PsiModifierList getAnnotationList() {
    @SuppressWarnings("unchecked") final StubElement<PsiModifierList> child =
      (StubElement<PsiModifierList>)getStub().findChildStubByElementType(JavaStubElementTypes.MODIFIER_LIST);
    return child == null ? null : child.getPsi();
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    PsiModifierList list = getAnnotationList();
    return list == null ? EMPTY_ARRAY : new PsiElement[]{list}; 
  }

  @Override
  public String getPackageName() {
    return myPackageName;
  }

  @Override
  public void appendMirrorText(final int indentLevel, final @NotNull StringBuilder buffer) {
    if (myPackageName != null) {
      PsiModifierList list = getAnnotationList();
      if (list != null) {
        for (PsiAnnotation annotation : list.getAnnotations()) {
          appendText(annotation, indentLevel, buffer);
          buffer.append("\n");
        }
      }
      buffer.append("package ").append(getPackageName()).append(';');
    }
  }

  @Override
  protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.PACKAGE_STATEMENT);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitPackageStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiPackageStatement:" + getPackageName();
  }
}
