// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.java.stubs.impl.PsiLiteralStub;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class JavaLiteralExpressionElementType extends JavaStubElementType<PsiLiteralStub, PsiLiteralExpression> {
  public JavaLiteralExpressionElementType() {
    super("LITERAL_EXPRESSION", BasicJavaElementType.BASIC_LITERAL_EXPRESSION);
  }

  @Override
  public PsiLiteralExpression createPsi(@NotNull ASTNode node) {
    return new PsiLiteralExpressionImpl(node);
  }

  @Override
  public @NotNull PsiLiteralStub createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
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

  @Override
  public @NotNull PsiLiteralStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PsiLiteralStub(parentStub, dataStream.readUTFFast());
  }

  @Override
  public void indexStub(@NotNull PsiLiteralStub stub, @NotNull IndexSink sink) { }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new CompositeElement(this);
  }

  @Override
  public boolean shouldCreateStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement parentStub) {
    LighterASTNode parent = tree.getParent(node);
    return parent != null && parent.getTokenType() == JavaStubElementTypes.NAME_VALUE_PAIR;
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    return node.getTreeParent().getElementType() == JavaStubElementTypes.NAME_VALUE_PAIR;
  }
}