// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiImportModuleStatement;
import com.intellij.psi.impl.java.stubs.impl.PsiImportModuleStatementStubImpl;
import com.intellij.psi.impl.source.PsiImportModuleStatementImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class JavaImportModuleStatementElementType extends JavaStubElementType<PsiImportModuleStatementStub, PsiImportModuleStatement> {
  public JavaImportModuleStatementElementType(final @NonNls @NotNull String id, @NotNull IElementType parentElementType) {
    super(id, parentElementType);
  }

  @Override
  public PsiImportModuleStatement createPsi(final @NotNull PsiImportModuleStatementStub stub) {
    return getPsiFactory(stub).createImportStatement(stub);
  }

  @Override
  public PsiImportModuleStatement createPsi(final @NotNull ASTNode node) {
    return new PsiImportModuleStatementImpl(node);
  }

  @Override
  public @NotNull PsiImportModuleStatementStub createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    String refText = null;

    for (LighterASTNode child : tree.getChildren(node)) {
      IElementType type = child.getTokenType();
      if (type == JavaElementType.MODULE_REFERENCE) {
        refText = JavaSourceUtil.getReferenceText(tree, child);
      }
    }
    return new PsiImportModuleStatementStubImpl(parentStub, refText);
  }

  @Override
  public void serialize(final @NotNull PsiImportModuleStatementStub stub, final @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getImportReferenceText());
  }

  @Override
  public @NotNull PsiImportModuleStatementStub deserialize(final @NotNull StubInputStream dataStream, final StubElement parentStub) throws IOException {
    String refText = dataStream.readNameString();
    return new PsiImportModuleStatementStubImpl(parentStub, refText);
  }

  @Override
  public void indexStub(final @NotNull PsiImportModuleStatementStub stub, final @NotNull IndexSink sink) {
  }
}
