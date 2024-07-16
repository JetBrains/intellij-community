// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiRequiresStatement;
import com.intellij.psi.impl.java.stubs.impl.PsiRequiresStatementStubImpl;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.PsiRequiresStatementImpl;
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

public class JavaRequiresStatementElementType extends JavaStubElementType<PsiRequiresStatementStub, PsiRequiresStatement> {
  public JavaRequiresStatementElementType() {
    super("REQUIRES_STATEMENT", BasicJavaElementType.BASIC_REQUIRES_STATEMENT);
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new CompositeElement(this);
  }

  @Override
  public PsiRequiresStatement createPsi(@NotNull PsiRequiresStatementStub stub) {
    return getPsiFactory(stub).createRequiresStatement(stub);
  }

  @Override
  public PsiRequiresStatement createPsi(@NotNull ASTNode node) {
    return new PsiRequiresStatementImpl(node);
  }

  @Override
  public @NotNull PsiRequiresStatementStub createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    LighterASTNode ref = LightTreeUtil.firstChildOfType(tree, node, JavaElementType.MODULE_REFERENCE);
    String refText = ref != null ? JavaSourceUtil.getReferenceText(tree, ref) : null;
    return new PsiRequiresStatementStubImpl(parentStub, refText);
  }

  @Override
  public void serialize(@NotNull PsiRequiresStatementStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getModuleName());
  }

  @Override
  public @NotNull PsiRequiresStatementStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PsiRequiresStatementStubImpl(parentStub, dataStream.readNameString());
  }

  @Override
  public void indexStub(@NotNull PsiRequiresStatementStub stub, @NotNull IndexSink sink) { }
}