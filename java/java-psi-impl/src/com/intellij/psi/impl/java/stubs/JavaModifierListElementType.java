/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author max
 */
public class JavaModifierListElementType extends JavaStubElementType<PsiModifierListStub, PsiModifierList> {
  public JavaModifierListElementType() {
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

  @Override
  public PsiModifierListStub createStub(final LighterAST tree, final LighterASTNode node, final StubElement parentStub) {
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
  public boolean shouldCreateStub(final LighterAST tree, final LighterASTNode node, final StubElement parentStub) {
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
