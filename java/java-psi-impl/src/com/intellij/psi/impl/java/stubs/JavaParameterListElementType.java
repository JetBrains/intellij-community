// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.impl.java.stubs.impl.PsiParameterListStubImpl;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.PsiParameterListImpl;
import com.intellij.psi.impl.source.tree.java.ParameterListElement;
import com.intellij.psi.stubs.EmptyStubSerializer;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;

public class JavaParameterListElementType extends JavaStubElementType<PsiParameterListStub, PsiParameterList>
  implements EmptyStubSerializer<PsiParameterListStub> {
  public JavaParameterListElementType() {
    super("PARAMETER_LIST", BasicJavaElementType.BASIC_PARAMETER_LIST);
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new ParameterListElement();
  }

  @Override
  public PsiParameterList createPsi(final @NotNull PsiParameterListStub stub) {
    return getPsiFactory(stub).createParameterList(stub);
  }

  @Override
  public PsiParameterList createPsi(final @NotNull ASTNode node) {
    return new PsiParameterListImpl(node);
  }

  @Override
  public @NotNull PsiParameterListStub createStub(final @NotNull LighterAST tree, final @NotNull LighterASTNode node, final @NotNull StubElement<?> parentStub) {
    return new PsiParameterListStubImpl(parentStub);
  }

  @Override
  public @NotNull PsiParameterListStub instantiate(final StubElement parentStub) {
    return new PsiParameterListStubImpl(parentStub);
  }

  @Override
  public void indexStub(final @NotNull PsiParameterListStub stub, final @NotNull IndexSink sink) {
  }
}
