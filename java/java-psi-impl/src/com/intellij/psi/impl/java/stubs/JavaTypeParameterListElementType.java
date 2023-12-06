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
import com.intellij.psi.stubs.*;
import org.jetbrains.annotations.NotNull;

public class JavaTypeParameterListElementType extends JavaStubElementType<PsiTypeParameterListStub, PsiTypeParameterList>
  implements EmptyStubSerializer<PsiTypeParameterListStub> {
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

  @NotNull
  @Override
  public PsiTypeParameterListStub instantiate(final StubElement parentStub) {
    return new PsiTypeParameterListStubImpl(parentStub);
  }

  @Override
  public void indexStub(@NotNull final PsiTypeParameterListStub stub, @NotNull final IndexSink sink) {
  }
}
