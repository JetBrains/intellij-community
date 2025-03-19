// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiRecordHeader;
import com.intellij.psi.impl.java.stubs.impl.PsiRecordHeaderStubImpl;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.PsiRecordHeaderImpl;
import com.intellij.psi.impl.source.tree.java.RecordHeaderElement;
import com.intellij.psi.stubs.EmptyStubSerializer;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;

public class JavaRecordHeaderElementType extends JavaStubElementType<PsiRecordHeaderStub, PsiRecordHeader>
  implements EmptyStubSerializer<PsiRecordHeaderStub> {
  public JavaRecordHeaderElementType() {
    super("RECORD_HEADER", BasicJavaElementType.BASIC_RECORD_HEADER);
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new RecordHeaderElement();
  }

  @Override
  public @NotNull PsiRecordHeaderStub instantiate(StubElement parentStub) {
    return new PsiRecordHeaderStubImpl(parentStub);
  }

  @Override
  public void indexStub(@NotNull PsiRecordHeaderStub stub, @NotNull IndexSink sink) {

  }

  @Override
  public PsiRecordHeader createPsi(@NotNull PsiRecordHeaderStub stub) {
    return getPsiFactory(stub).createRecordHeader(stub);
  }


  @Override
  public PsiRecordHeader createPsi(@NotNull ASTNode node) {
    return new PsiRecordHeaderImpl(node);
  }

  @Override
  public @NotNull PsiRecordHeaderStub createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    return new PsiRecordHeaderStubImpl(parentStub);
  }
}