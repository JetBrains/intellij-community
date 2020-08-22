// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.impl.java.stubs.impl.PsiImportListStubImpl;
import com.intellij.psi.impl.source.PsiImportListImpl;
import com.intellij.psi.impl.source.tree.java.ImportListElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;

public final class JavaImportListElementType extends JavaStubElementType<PsiImportListStub, PsiImportList> {
  public JavaImportListElementType() {
    super("IMPORT_LIST");
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new ImportListElement();
  }

  @Override
  public PsiImportList createPsi(@NotNull final PsiImportListStub stub) {
    return getPsiFactory(stub).createImportList(stub);
  }

  @Override
  public PsiImportList createPsi(@NotNull final ASTNode node) {
    return new PsiImportListImpl(node);
  }

  @NotNull
  @Override
  public PsiImportListStub createStub(@NotNull final LighterAST tree, @NotNull final LighterASTNode node, @NotNull final StubElement parentStub) {
    return new PsiImportListStubImpl(parentStub);
  }

  @Override
  public void serialize(@NotNull final PsiImportListStub stub, @NotNull final StubOutputStream dataStream) {
  }

  @NotNull
  @Override
  public PsiImportListStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) {
    return new PsiImportListStubImpl(parentStub);
  }

  @Override
  public void indexStub(@NotNull final PsiImportListStub stub, @NotNull final IndexSink sink) {
  }
}
