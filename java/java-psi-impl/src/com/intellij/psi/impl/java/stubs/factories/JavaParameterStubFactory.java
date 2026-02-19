// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.java.stubs.impl.PsiParameterStubImpl;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaParameterStubFactory implements LightStubElementFactory<PsiParameterStubImpl, PsiParameter> {
  @Override
  public @NotNull PsiParameterStubImpl createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    TypeInfo typeInfo = TypeInfo.create(tree, node, parentStub);
    LighterASTNode id = LightTreeUtil.requiredChildOfType(tree, node, JavaTokenType.IDENTIFIER);
    String name = RecordUtil.intern(tree.getCharTable(), id);
    return new PsiParameterStubImpl(parentStub, name, typeInfo, typeInfo.isEllipsis(), false);
  }

  @Override
  public PsiParameter createPsi(@NotNull PsiParameterStubImpl stub) {
    return JavaStubElementType.getFileStub(stub).getPsiFactory().createParameter(stub);
  }
  
  @Override
  public @NotNull PsiParameterStubImpl createStub(@NotNull PsiParameter psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }
}