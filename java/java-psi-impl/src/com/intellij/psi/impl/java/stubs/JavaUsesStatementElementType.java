// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiUsesStatement;
import com.intellij.psi.impl.java.stubs.impl.PsiUsesStatementStubImpl;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.PsiUsesStatementImpl;
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

public class JavaUsesStatementElementType extends JavaStubElementType<PsiUsesStatementStub, PsiUsesStatement> {
  public JavaUsesStatementElementType() {
    super("USES_STATEMENT", BasicJavaElementType.BASIC_USES_STATEMENT);
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new CompositeElement(this);
  }

  @Override
  public PsiUsesStatement createPsi(@NotNull PsiUsesStatementStub stub) {
    return getPsiFactory(stub).createUsesStatement(stub);
  }

  @Override
  public PsiUsesStatement createPsi(@NotNull ASTNode node) {
    return new PsiUsesStatementImpl(node);
  }

  @Override
  public @NotNull PsiUsesStatementStub createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    LighterASTNode ref = LightTreeUtil.firstChildOfType(tree, node, JavaElementType.JAVA_CODE_REFERENCE);
    String refText = ref != null ? JavaSourceUtil.getReferenceText(tree, ref) : null;
    return new PsiUsesStatementStubImpl(parentStub, refText);
  }

  @Override
  public void serialize(@NotNull PsiUsesStatementStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getClassName());
  }

  @Override
  public @NotNull PsiUsesStatementStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PsiUsesStatementStubImpl(parentStub, dataStream.readNameString());
  }

  @Override
  public void indexStub(@NotNull PsiUsesStatementStub stub, @NotNull IndexSink sink) { }
}