// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiProvidesStatementStub;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiProvidesStatementImpl extends JavaStubPsiElement<PsiProvidesStatementStub> implements PsiProvidesStatement {
  public PsiProvidesStatementImpl(@NotNull PsiProvidesStatementStub stub) {
    super(stub, JavaStubElementTypes.PROVIDES_STATEMENT);
  }

  public PsiProvidesStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable PsiJavaCodeReferenceElement getInterfaceReference() {
    return PsiTreeUtil.getChildOfType(this, PsiJavaCodeReferenceElement.class);
  }

  @Override
  public @Nullable PsiClassType getInterfaceType() {
    PsiProvidesStatementStub stub = getStub();
    PsiJavaCodeReferenceElement ref =
      stub != null ? JavaPsiFacade.getElementFactory(getProject()).createReferenceFromText(stub.getInterface(), this) : getInterfaceReference();
    return ref != null ? new PsiClassReferenceType(ref, null, PsiAnnotation.EMPTY_ARRAY) : null;
  }

  @Override
  public @Nullable PsiReferenceList getImplementationList() {
    return getStubOrPsiChild(JavaStubElementTypes.PROVIDES_WITH_LIST, PsiReferenceList.class);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitProvidesStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiProvidesStatement";
  }
}