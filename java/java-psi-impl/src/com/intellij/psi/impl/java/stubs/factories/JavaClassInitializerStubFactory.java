// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.java.stubs.impl.PsiClassInitializerStubImpl;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaClassInitializerStubFactory implements LightStubElementFactory<PsiClassInitializerStubImpl, PsiClassInitializer> {
  @Override
  public @NotNull PsiClassInitializerStubImpl createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    return new PsiClassInitializerStubImpl(parentStub);
  }

  @Override
  public PsiClassInitializer createPsi(@NotNull PsiClassInitializerStubImpl stub) {
    return JavaStubElementType.getFileStub(stub).getPsiFactory().createClassInitializer(stub);
  }
  
  @Override
  public @NotNull PsiClassInitializerStubImpl createStub(@NotNull PsiClassInitializer psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }
}