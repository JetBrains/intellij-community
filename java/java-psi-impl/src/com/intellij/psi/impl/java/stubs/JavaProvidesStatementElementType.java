// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiProvidesStatement;
import com.intellij.psi.impl.java.stubs.impl.PsiProvidesStatementStubImpl;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.PsiProvidesStatementImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class JavaProvidesStatementElementType extends JavaStubElementType<PsiProvidesStatementStub, PsiProvidesStatement> {
  public JavaProvidesStatementElementType() {
    super("PROVIDES_STATEMENT", BasicJavaElementType.BASIC_PROVIDES_STATEMENT);
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new CompositeElement(this);
  }

  @Override
  public PsiProvidesStatement createPsi(@NotNull PsiProvidesStatementStub stub) {
    return getPsiFactory(stub).createProvidesStatement(stub);
  }

  @Override
  public PsiProvidesStatement createPsi(@NotNull ASTNode node) {
    return new PsiProvidesStatementImpl(node);
  }

  @Override
  public @NotNull PsiProvidesStatementStub createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    LighterASTNode ref = LightTreeUtil.firstChildOfType(tree, node, JavaElementType.JAVA_CODE_REFERENCE);
    String refText = ref != null ? JavaSourceUtil.getReferenceText(tree, ref) : null;
    return new PsiProvidesStatementStubImpl(parentStub, refText);
  }

  @Override
  public void serialize(@NotNull PsiProvidesStatementStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getInterface());
  }

  @Override
  public @NotNull PsiProvidesStatementStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PsiProvidesStatementStubImpl(parentStub, dataStream.readNameString());
  }

  @Override
  public void indexStub(@NotNull PsiProvidesStatementStub stub, @NotNull IndexSink sink) { }
}