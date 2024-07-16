// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiImportStatementStub;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiImportStaticStatementImpl extends PsiImportStatementBaseImpl implements PsiImportStaticStatement {
  public static final PsiImportStaticStatementImpl[] EMPTY_ARRAY = new PsiImportStaticStatementImpl[0];
  public static final ArrayFactory<PsiImportStaticStatementImpl> ARRAY_FACTORY =
    count -> count == 0 ? EMPTY_ARRAY : new PsiImportStaticStatementImpl[count];

  public PsiImportStaticStatementImpl(PsiImportStatementStub stub) {
    super(stub, JavaStubElementTypes.IMPORT_STATIC_STATEMENT);
  }

  public PsiImportStaticStatementImpl(ASTNode node) {
    super(node);
  }

  @Override
  public PsiClass resolveTargetClass() {
    PsiJavaCodeReferenceElement classReference = getClassReference();
    if (classReference == null) return null;
    PsiElement result = classReference.resolve();
    if (result instanceof PsiClass) {
      return (PsiClass) result;
    }
    else {
      return null;
    }
  }

  @Override
  public String getReferenceName() {
    if (isOnDemand()) return null;
    PsiImportStaticReferenceElement memberReference = getMemberReference();
    if (memberReference != null) {
      return memberReference.getReferenceName();
    }
    else {
      return null;
    }
  }

  private @Nullable PsiImportStaticReferenceElement getMemberReference() {
    if (isOnDemand()) {
      return null;
    }
    else {
      return (PsiImportStaticReferenceElement) getImportReference();
    }
  }

  public @Nullable PsiJavaCodeReferenceElement getClassReference() {
    if (isOnDemand()) {
      return getImportReference();
    }
    else {
      PsiImportStaticReferenceElement memberReference = getMemberReference();
      if (memberReference != null) {
        return memberReference.getClassReference();
      }
      else {
        return null;
      }
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitImportStaticStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString(){
    return "PsiImportStaticStatement";
  }
}