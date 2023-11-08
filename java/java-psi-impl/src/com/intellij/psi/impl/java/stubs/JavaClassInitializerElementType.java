// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.impl.java.stubs.impl.PsiClassInitializerStubImpl;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.PsiClassInitializerImpl;
import com.intellij.psi.impl.source.tree.java.ClassInitializerElement;
import com.intellij.psi.stubs.*;
import org.jetbrains.annotations.NotNull;

public class JavaClassInitializerElementType extends JavaStubElementType<PsiClassInitializerStub, PsiClassInitializer>
  implements EmptyStubSerializer<PsiClassInitializerStub> {
  public JavaClassInitializerElementType() {
    super("CLASS_INITIALIZER", BasicJavaElementType.BASIC_CLASS_INITIALIZER);
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new ClassInitializerElement();
  }

  @Override
  public PsiClassInitializer createPsi(@NotNull final PsiClassInitializerStub stub) {
    return getPsiFactory(stub).createClassInitializer(stub);
  }

  @Override
  public PsiClassInitializer createPsi(@NotNull final ASTNode node) {
    return new PsiClassInitializerImpl(node);
  }

  @NotNull
  @Override
  public PsiClassInitializerStub createStub(@NotNull final LighterAST tree,
                                            @NotNull final LighterASTNode node,
                                            final @NotNull StubElement<?> parentStub) {
    return new PsiClassInitializerStubImpl(parentStub);
  }

  @Override
  public @NotNull PsiClassInitializerStub instantiate(StubElement<?> parentStub) {
    return new PsiClassInitializerStubImpl(parentStub);
  }

  @Override
  public void indexStub(@NotNull final PsiClassInitializerStub stub, @NotNull final IndexSink sink) {
  }
}
