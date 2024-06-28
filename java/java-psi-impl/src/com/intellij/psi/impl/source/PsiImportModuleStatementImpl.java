// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiImportStatementStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiImportModuleStatementImpl extends PsiImportStatementBaseImpl implements PsiImportModuleStatement {
  public PsiImportModuleStatementImpl(PsiImportStatementStub stub) {
    super(stub, JavaStubElementTypes.IMPORT_MODULE_STATEMENT);
  }

  public PsiImportModuleStatementImpl(ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable PsiJavaModule resolveTargetModule() {
    PsiJavaModuleReferenceElement moduleReference = geModuleReference();
    if (moduleReference == null) return null;
    PsiJavaModuleReference reference = moduleReference.getReference();
    if (reference == null) return null;
    return reference.resolve();
  }

  @Override
  public String getReferenceName() {
    return null;
  }

  public @Nullable PsiJavaModuleReferenceElement geModuleReference() {
    return null;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitImportModuleStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiImportModuleStatement";
  }
}