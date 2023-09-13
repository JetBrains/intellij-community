// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiRecordHeader;
import com.intellij.psi.impl.java.stubs.impl.PsiRecordHeaderStubImpl;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.PsiRecordHeaderImpl;
import com.intellij.psi.impl.source.tree.java.RecordHeaderElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class JavaRecordHeaderElementType extends JavaStubElementType<PsiRecordHeaderStub, PsiRecordHeader> {
  public JavaRecordHeaderElementType() {
    super("RECORD_HEADER", BasicJavaElementType.BASIC_RECORD_HEADER);
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new RecordHeaderElement();
  }

  @Override
  public void serialize(@NotNull PsiRecordHeaderStub stub, @NotNull StubOutputStream dataStream) throws IOException {
  }

  @NotNull
  @Override
  public PsiRecordHeaderStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PsiRecordHeaderStubImpl(parentStub);
  }

  @Override
  public boolean isAlwaysEmpty() {
    return true;
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

  @NotNull
  @Override
  public PsiRecordHeaderStub createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    return new PsiRecordHeaderStubImpl(parentStub);
  }
}