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
import com.intellij.psi.stubs.*;
import org.jetbrains.annotations.NotNull;

public class JavaRecordHeaderElementType extends JavaStubElementType<PsiRecordHeaderStub, PsiRecordHeader>
  implements EmptyStubSerializer<PsiRecordHeaderStub> {
  public JavaRecordHeaderElementType() {
    super("RECORD_HEADER", BasicJavaElementType.BASIC_RECORD_HEADER);
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new RecordHeaderElement();
  }

  @NotNull
  @Override
  public PsiRecordHeaderStub instantiate(StubElement parentStub) {
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

  @NotNull
  @Override
  public PsiRecordHeaderStub createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    return new PsiRecordHeaderStubImpl(parentStub);
  }
}