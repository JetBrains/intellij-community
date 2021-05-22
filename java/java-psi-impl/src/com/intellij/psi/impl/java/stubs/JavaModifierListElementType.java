// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.java.stubs.impl.PsiModifierListStubImpl;
import com.intellij.psi.impl.source.PsiModifierListImpl;
import com.intellij.psi.impl.source.tree.java.ModifierListElement;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

class JavaModifierListElementType extends JavaStubElementType<PsiModifierListStub, PsiModifierList> {
  JavaModifierListElementType() {
    super("MODIFIER_LIST");
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new ModifierListElement();
  }

  @Override
  public PsiModifierList createPsi(@NotNull PsiModifierListStub stub) {
    return getPsiFactory(stub).createModifierList(stub);
  }

  @Override
  public PsiModifierList createPsi(@NotNull ASTNode node) {
    return new PsiModifierListImpl(node);
  }

  @Override
  public @NotNull PsiModifierListStub createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement parentStub) {
    return new PsiModifierListStubImpl(parentStub, RecordUtil.packModifierList(tree, node));
  }

  @Override
  public void serialize(@NotNull PsiModifierListStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeVarInt(stub.getModifiersMask());
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
    return parentType instanceof IStubElementType;
  }

  @Override
  public @NotNull PsiModifierListStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PsiModifierListStubImpl(parentStub, dataStream.readVarInt());
  }

  @Override
  public void indexStub(@NotNull PsiModifierListStub stub, @NotNull IndexSink sink) { }
}
