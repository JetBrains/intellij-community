// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.impl.java.stubs.PsiImportStatementStub;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.psi.util.PsiUtilCore;

public abstract class PsiImportStatementBaseImpl extends JavaStubPsiElement<PsiImportStatementStub> implements PsiImportStatementBase{
  public static final PsiImportStatementBaseImpl[] EMPTY_ARRAY = new PsiImportStatementBaseImpl[0];

  protected PsiImportStatementBaseImpl(PsiImportStatementStub stub, IJavaElementType type) {
    super(stub, type);
  }

  protected PsiImportStatementBaseImpl(ASTNode node) {
    super(node);
  }

  @Override
  public boolean isOnDemand(){
    PsiImportStatementStub stub = getGreenStub();
    if (stub != null) {
      return stub.isOnDemand();
    }

    return calcTreeElement().findChildByRoleAsPsiElement(ChildRole.IMPORT_ON_DEMAND_DOT) != null ||
           calcTreeElement().findChildByType(JavaElementType.MODULE_REFERENCE) != null;
  }

  @Override
  public PsiJavaCodeReferenceElement getImportReference() {
    PsiUtilCore.ensureValid(this);
    PsiImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.getReference();
    }
    return (PsiJavaCodeReferenceElement)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.IMPORT_REFERENCE);
  }

  @Override
  public PsiElement resolve() {
    PsiJavaCodeReferenceElement reference = getImportReference();
    return reference == null ? null : reference.resolve();
  }

  @Override
  public boolean isForeignFileImport() {
    return false;
  }
}