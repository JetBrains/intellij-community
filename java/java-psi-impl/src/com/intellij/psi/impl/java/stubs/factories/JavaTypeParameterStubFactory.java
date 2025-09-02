// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.java.stubs.impl.PsiTypeParameterStubImpl;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaTypeParameterStubFactory implements LightStubElementFactory<PsiTypeParameterStubImpl, PsiTypeParameter> {
  @Override
  public @NotNull PsiTypeParameterStubImpl createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    final LighterASTNode id = LightTreeUtil.requiredChildOfType(tree, node, JavaTokenType.IDENTIFIER);
    final String name = RecordUtil.intern(tree.getCharTable(), id);
    return new PsiTypeParameterStubImpl(parentStub, name);
  }

  @Override
  public PsiTypeParameter createPsi(@NotNull PsiTypeParameterStubImpl stub) {
    return JavaStubElementType.getFileStub(stub).getPsiFactory().createTypeParameter(stub);
  }
  
  @Override
  public @NotNull PsiTypeParameterStubImpl createStub(@NotNull PsiTypeParameter psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }
}