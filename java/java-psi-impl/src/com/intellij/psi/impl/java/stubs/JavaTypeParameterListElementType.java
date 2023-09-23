// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.impl.java.stubs.impl.PsiTypeParameterListStubImpl;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.tree.java.PsiTypeParameterListImpl;
import com.intellij.psi.impl.source.tree.java.TypeParameterListElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class JavaTypeParameterListElementType extends JavaStubElementType<PsiTypeParameterListStub, PsiTypeParameterList> {
  public JavaTypeParameterListElementType() {
    super("TYPE_PARAMETER_LIST", true, BasicJavaElementType.BASIC_TYPE_PARAMETER_LIST);
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new TypeParameterListElement();
  }

  @Override
  public PsiTypeParameterList createPsi(@NotNull final PsiTypeParameterListStub stub) {
    return getPsiFactory(stub).createTypeParameterList(stub);
  }

  @Override
  public PsiTypeParameterList createPsi(@NotNull final ASTNode node) {
    return new PsiTypeParameterListImpl(node);
  }

  @NotNull
  @Override
  public PsiTypeParameterListStub createStub(@NotNull final LighterAST tree,
                                             @NotNull final LighterASTNode node,
                                             final @NotNull StubElement<?> parentStub) {
    return new PsiTypeParameterListStubImpl(parentStub);
  }

  @Override
  public void serialize(@NotNull final PsiTypeParameterListStub stub, @NotNull final StubOutputStream dataStream) throws IOException {
  }

  @NotNull
  @Override
  public PsiTypeParameterListStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PsiTypeParameterListStubImpl(parentStub);
  }

  @Override
  public boolean isAlwaysEmpty() {
    return true;
  }

  @Override
  public void indexStub(@NotNull final PsiTypeParameterListStub stub, @NotNull final IndexSink sink) {
  }
}
