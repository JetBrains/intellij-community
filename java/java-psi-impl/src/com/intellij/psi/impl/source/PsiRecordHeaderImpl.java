// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiRecordHeaderStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiRecordHeaderImpl extends JavaStubPsiElement<PsiRecordHeaderStub> implements PsiRecordHeader {

  public PsiRecordHeaderImpl(@NotNull PsiRecordHeaderStub stub) {
    super(stub, JavaStubElementTypes.RECORD_HEADER);
  }

  public PsiRecordHeaderImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : null;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitRecordHeader(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiRecordHeader";
  }

  @Override
  public PsiRecordComponent @NotNull [] getRecordComponents() {
    return getStubOrPsiChildren(JavaStubElementTypes.RECORD_COMPONENT, PsiRecordComponent.EMPTY_ARRAY);
  }
}
