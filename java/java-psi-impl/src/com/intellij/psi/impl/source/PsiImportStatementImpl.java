// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiImportStatementStub;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PsiImportStatementImpl extends PsiImportStatementBaseImpl implements PsiImportStatement {
  public static final PsiImportStatementImpl[] EMPTY_ARRAY = new PsiImportStatementImpl[0];
  public static final ArrayFactory<PsiImportStatementImpl> ARRAY_FACTORY =
    count -> count == 0 ? EMPTY_ARRAY : new PsiImportStatementImpl[count];

  public PsiImportStatementImpl(PsiImportStatementStub stub) {
    super(stub, JavaStubElementTypes.IMPORT_STATEMENT);
  }

  public PsiImportStatementImpl(ASTNode node) {
    super(node);
  }

  @Override
  public String getQualifiedName() {
    PsiImportStatementStub stub = getGreenStub();
    if (stub != null) {
      return stub.getImportReferenceText();
    }
    PsiJavaCodeReferenceElement reference = getImportReference();
    return reference == null ? null : reference.getCanonicalText();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitImportStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public boolean isReplaceEquivalent(PsiImportStatementBase other) {
    if (this == other) return true;
    if (!(other instanceof PsiImportStatementImpl)) return false;
    PsiImportStatementImpl statement = (PsiImportStatementImpl)other;
    return isOnDemand() == statement.isOnDemand() && Objects.equals(getQualifiedName(), statement.getQualifiedName());
  }

  @Override
  public String toString(){
    return "PsiImportStatement";
  }
}