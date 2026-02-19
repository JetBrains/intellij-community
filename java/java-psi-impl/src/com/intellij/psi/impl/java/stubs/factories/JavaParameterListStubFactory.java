// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.java.stubs.impl.PsiParameterListStubImpl;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaParameterListStubFactory implements LightStubElementFactory<PsiParameterListStubImpl, PsiParameterList> {
  @Override
  public @NotNull PsiParameterListStubImpl createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    return new PsiParameterListStubImpl(parentStub);
  }

  @Override
  public PsiParameterList createPsi(@NotNull PsiParameterListStubImpl stub) {
    return JavaStubElementType.getFileStub(stub).getPsiFactory().createParameterList(stub);
  }
  
  @Override
  public @NotNull PsiParameterListStubImpl createStub(@NotNull PsiParameterList psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }
}