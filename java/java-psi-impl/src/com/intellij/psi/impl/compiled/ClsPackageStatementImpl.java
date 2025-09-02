// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

class ClsPackageStatementImpl extends ClsElementImpl implements PsiPackageStatement {
  static ClsPackageStatementImpl NULL_PACKAGE = new ClsPackageStatementImpl();

  private final ClsFileImpl myFile;
  private final String myPackageName;

  private ClsPackageStatementImpl() {
    myFile = null;
    myPackageName = null;
  }

  ClsPackageStatementImpl(@NotNull ClsFileImpl file, String packageName) {
    myFile = file;
    myPackageName = packageName;
  }

  @Override
  public PsiElement getParent() {
    return myFile;
  }

  @Override
  public PsiJavaCodeReferenceElement getPackageReference() {
    throw new UnsupportedOperationException("Method not implemented");
  }

  @Override
  public PsiModifierList getAnnotationList() {
    if (myFile != null && myFile.getName().equals("package-info.class")) {
      PsiClass[] classes = myFile.getClasses();
      if (classes.length == 1) {
        return classes[0].getModifierList();
      }
    }
    throw new UnsupportedOperationException("Method not implemented");
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    throw new UnsupportedOperationException("Method not implemented");
  }

  @Override
  public String getPackageName() {
    return myPackageName;
  }

  @Override
  public void appendMirrorText(final int indentLevel, final @NotNull StringBuilder buffer) {
    if (myPackageName != null) {
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
