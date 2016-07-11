/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.java.stubs.impl.PsiLiteralStub;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author peter
 */
public class JavaLiteralExpressionElementType extends JavaStubElementType<PsiLiteralStub, PsiLiteralExpression> {
  public JavaLiteralExpressionElementType() {
    super("LITERAL_EXPRESSION");
  }

  @Override
  public PsiLiteralExpression createPsi(@NotNull ASTNode node) {
    return new PsiLiteralExpressionImpl(node);
  }

  @Override
  public PsiLiteralStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    return new PsiLiteralStub(parentStub, RecordUtil.intern(tree.getCharTable(), tree.getChildren(node).get(0)));
  }

  @Override
  public PsiLiteralExpression createPsi(@NotNull PsiLiteralStub stub) {
    return new PsiLiteralExpressionImpl(stub);
  }

  @Override
  public void serialize(@NotNull PsiLiteralStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeUTFFast(stub.getLiteralText());
  }

  @NotNull
  @Override
  public PsiLiteralStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PsiLiteralStub(parentStub, dataStream.readUTFFast());
  }

  @Override
  public void indexStub(@NotNull PsiLiteralStub stub, @NotNull IndexSink sink) { }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new CompositeElement(this);
  }

  @Override
  public boolean shouldCreateStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    LighterASTNode parent = tree.getParent(node);
    return parent != null && parent.getTokenType() == JavaStubElementTypes.NAME_VALUE_PAIR;
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    return node.getTreeParent().getElementType() == JavaStubElementTypes.NAME_VALUE_PAIR;
  }
}