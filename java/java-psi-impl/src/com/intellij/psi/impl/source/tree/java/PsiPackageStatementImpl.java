// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiPackageStatementStub;
import com.intellij.psi.impl.source.JavaStubPsiElement;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiPackageStatementImpl extends JavaStubPsiElement<PsiPackageStatementStub> implements PsiPackageStatement {
  public PsiPackageStatementImpl(PsiPackageStatementStub stub) {
    super(stub, JavaStubElementTypes.PACKAGE_STATEMENT);
  }

  public PsiPackageStatementImpl(ASTNode node) {
    super(node);
  }

  @Override
  public @NotNull CompositeElement getNode() {
    return (CompositeElement)super.getNode();
  }

  @Override
  public PsiJavaCodeReferenceElement getPackageReference() {
    return (PsiJavaCodeReferenceElement)getNode().findChildByRoleAsPsiElement(ChildRole.PACKAGE_REFERENCE);
  }

  @Override
  public @NotNull String getPackageName() {
    PsiPackageStatementStub stub = getGreenStub();
    if (stub != null) {
      return stub.getPackageName();
    }
    PsiJavaCodeReferenceElement ref = getPackageReference();
    return ref == null ? "" : JavaSourceUtil.getReferenceText(ref);
  }

  @Override
  public PsiModifierList getAnnotationList() {
    return getStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST, PsiModifierList.class);
  }

  @Override
  public @Nullable PsiDocComment getDocComment() {
    if (!PsiPackage.PACKAGE_INFO_FILE.equals(getContainingFile().getName())) return null;
    PsiElement sibling = PsiTreeUtil.skipWhitespacesBackward(this);
    return sibling instanceof PsiDocComment ? (PsiDocComment)sibling : null;
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
