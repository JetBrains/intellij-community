// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new ModifierListElement();
  }

  @Override
  public PsiModifierList createPsi(@NotNull final PsiModifierListStub stub) {
    return getPsiFactory(stub).createModifierList(stub);
  }

  @Override
  public PsiModifierList createPsi(@NotNull final ASTNode node) {
    return new PsiModifierListImpl(node);
  }

  @NotNull
  @Override
  public PsiModifierListStub createStub(@NotNull final LighterAST tree, @NotNull final LighterASTNode node, @NotNull final StubElement parentStub) {
    return new PsiModifierListStubImpl(parentStub, RecordUtil.packModifierList(tree, node));
  }

  @Override
  public void serialize(@NotNull final PsiModifierListStub stub, @NotNull final StubOutputStream dataStream) throws IOException {
    dataStream.writeVarInt(stub.getModifiersMask());
  }

  @Override
  public boolean shouldCreateStub(final ASTNode node) {
    final IElementType parentType = node.getTreeParent().getElementType();
    return shouldCreateStub(parentType);
  }

  @Override
  public boolean shouldCreateStub(@NotNull final LighterAST tree, @NotNull final LighterASTNode node, @NotNull final StubElement parentStub) {
    final LighterASTNode parent = tree.getParent(node);
    final IElementType parentType = parent != null ? parent.getTokenType() : null;
    return shouldCreateStub(parentType);
  }

  private static boolean shouldCreateStub(IElementType parentType) {
    return parentType instanceof IStubElementType;
  }

  @NotNull
  @Override
  public PsiModifierListStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PsiModifierListStubImpl(parentStub, dataStream.readVarInt());
  }

  @Override
  public void indexStub(@NotNull final PsiModifierListStub stub, @NotNull final IndexSink sink) { }
}
