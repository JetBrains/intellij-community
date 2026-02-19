// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiRecordHeader;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.java.stubs.impl.PsiRecordHeaderStubImpl;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaRecordHeaderStubFactory implements LightStubElementFactory<PsiRecordHeaderStubImpl, PsiRecordHeader> {
  @Override
  public @NotNull PsiRecordHeaderStubImpl createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    return new PsiRecordHeaderStubImpl(parentStub);
  }

  @Override
  public PsiRecordHeader createPsi(@NotNull PsiRecordHeaderStubImpl stub) {
    return JavaStubElementType.getFileStub(stub).getPsiFactory().createRecordHeader(stub);
  }
  
  @Override
  public @NotNull PsiRecordHeaderStubImpl createStub(@NotNull PsiRecordHeader psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }
}