// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.impl.java.stubs.impl.PsiTypeParameterListStubImpl;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.tree.java.PsiTypeParameterListImpl;
import com.intellij.psi.impl.source.tree.java.TypeParameterListElement;
import com.intellij.psi.stubs.EmptyStubSerializer;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;

public class JavaTypeParameterListElementType extends JavaStubElementType<PsiTypeParameterListStub, PsiTypeParameterList>
  implements EmptyStubSerializer<PsiTypeParameterListStub> {
  public JavaTypeParameterListElementType() {
    super("TYPE_PARAMETER_LIST", true, BasicJavaElementType.BASIC_TYPE_PARAMETER_LIST);
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new TypeParameterListElement();
  }

  @Override
  public PsiTypeParameterList createPsi(final @NotNull PsiTypeParameterListStub stub) {
    return getPsiFactory(stub).createTypeParameterList(stub);
  }

  @Override
  public PsiTypeParameterList createPsi(final @NotNull ASTNode node) {
    return new PsiTypeParameterListImpl(node);
  }

  @Override
  public @NotNull PsiTypeParameterListStub createStub(final @NotNull LighterAST tree,
                                                      final @NotNull LighterASTNode node,
                                                      final @NotNull StubElement<?> parentStub) {
    return new PsiTypeParameterListStubImpl(parentStub);
  }

  @Override
  public @NotNull PsiTypeParameterListStub instantiate(final StubElement parentStub) {
    return new PsiTypeParameterListStubImpl(parentStub);
  }

  @Override
  public void indexStub(final @NotNull PsiTypeParameterListStub stub, final @NotNull IndexSink sink) {
  }
}
