// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.impl.java.stubs.impl.PsiParameterListStubImpl;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.PsiParameterListImpl;
import com.intellij.psi.impl.source.tree.java.ParameterListElement;
import com.intellij.psi.stubs.*;
import org.jetbrains.annotations.NotNull;

public class JavaParameterListElementType extends JavaStubElementType<PsiParameterListStub, PsiParameterList>
  implements EmptyStubSerializer<PsiParameterListStub> {
  public JavaParameterListElementType() {
    super("PARAMETER_LIST", BasicJavaElementType.BASIC_PARAMETER_LIST);
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new ParameterListElement();
  }

  @Override
  public PsiParameterList createPsi(@NotNull final PsiParameterListStub stub) {
    return getPsiFactory(stub).createParameterList(stub);
  }

  @Override
  public PsiParameterList createPsi(@NotNull final ASTNode node) {
    return new PsiParameterListImpl(node);
  }

  @NotNull
  @Override
  public PsiParameterListStub createStub(@NotNull final LighterAST tree, @NotNull final LighterASTNode node, final @NotNull StubElement<?> parentStub) {
    return new PsiParameterListStubImpl(parentStub);
  }

  @NotNull
  @Override
  public PsiParameterListStub instantiate(final StubElement parentStub) {
    return new PsiParameterListStubImpl(parentStub);
  }

  @Override
  public void indexStub(@NotNull final PsiParameterListStub stub, @NotNull final IndexSink sink) {
  }
}
