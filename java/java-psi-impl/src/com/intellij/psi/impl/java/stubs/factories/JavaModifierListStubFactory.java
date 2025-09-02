// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.java.stubs.impl.PsiModifierListStubImpl;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubElementRegistryService;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaModifierListStubFactory implements LightStubElementFactory<PsiModifierListStubImpl, PsiModifierList> {
  @Override
  public @NotNull PsiModifierListStubImpl createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    return new PsiModifierListStubImpl(parentStub, RecordUtil.packModifierList(tree, node));
  }

  @Override
  public PsiModifierList createPsi(@NotNull PsiModifierListStubImpl stub) {
    return JavaStubElementType.getFileStub(stub).getPsiFactory().createModifierList(stub);
  }
  
  @Override
  public @NotNull PsiModifierListStubImpl createStub(@NotNull PsiModifierList psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    IElementType parentType = node.getTreeParent().getElementType();
    return shouldCreateStub(parentType);
  }

  @Override
  public boolean shouldCreateStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement parentStub) {
    LighterASTNode parent = tree.getParent(node);
    IElementType parentType = parent != null ? parent.getTokenType() : null;
    return shouldCreateStub(parentType);
  }

  private static boolean shouldCreateStub(IElementType parentType) {
    return StubElementRegistryService.getInstance().getStubSerializer(parentType) != null;
  }
}