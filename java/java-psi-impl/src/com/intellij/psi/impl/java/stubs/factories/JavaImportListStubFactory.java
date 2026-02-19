// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.java.stubs.impl.PsiImportListStubImpl;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaImportListStubFactory implements LightStubElementFactory<PsiImportListStubImpl, PsiImportList> {
  @Override
  public @NotNull PsiImportListStubImpl createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    return new PsiImportListStubImpl(parentStub);
  }

  @Override
  public PsiImportList createPsi(@NotNull PsiImportListStubImpl stub) {
    return JavaStubElementType.getFileStub(stub).getPsiFactory().createImportList(stub);
  }
  
  @Override
  public @NotNull PsiImportListStubImpl createStub(@NotNull PsiImportList psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }
}