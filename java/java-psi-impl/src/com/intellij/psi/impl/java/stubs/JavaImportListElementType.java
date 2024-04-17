// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.impl.java.stubs.impl.PsiImportListStubImpl;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.PsiImportListImpl;
import com.intellij.psi.impl.source.tree.java.ImportListElement;
import com.intellij.psi.stubs.EmptyStubSerializer;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;

public final class JavaImportListElementType extends JavaStubElementType<PsiImportListStub, PsiImportList>
  implements EmptyStubSerializer<PsiImportListStub> {
  public JavaImportListElementType() {
    super("IMPORT_LIST", BasicJavaElementType.BASIC_IMPORT_LIST);
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new ImportListElement();
  }

  @Override
  public PsiImportList createPsi(final @NotNull PsiImportListStub stub) {
    return getPsiFactory(stub).createImportList(stub);
  }

  @Override
  public PsiImportList createPsi(final @NotNull ASTNode node) {
    return new PsiImportListImpl(node);
  }

  @Override
  public @NotNull PsiImportListStub createStub(final @NotNull LighterAST tree, final @NotNull LighterASTNode node, final @NotNull StubElement<?> parentStub) {
    return new PsiImportListStubImpl(parentStub);
  }

  @Override
  public @NotNull PsiImportListStub instantiate(final StubElement parentStub) {
    return new PsiImportListStubImpl(parentStub);
  }

  @Override
  public void indexStub(final @NotNull PsiImportListStub stub, final @NotNull IndexSink sink) {
  }
}
